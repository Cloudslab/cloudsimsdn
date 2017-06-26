/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;

// Assumption: Hosts are homogeneous
// This class holds free MIPS/BW information after allocation is done.
// The class holds all hosts information

public class OverbookingVmAllocationPolicy extends VmAllocationPolicy implements PowerUtilizationMaxHostInterface {

	protected HostSelectionPolicy hostSelectionPolicy = null;
	protected VmMigrationPolicy vmMigrationPolicy = null;

	protected final double hostTotalMips;
	protected final double hostTotalBw;
	protected final int hostTotalPes;
	
	/** The vm table. */
	private Map<String, Host> vmTable;

	/** The used pes. */
	private Map<String, Integer> usedPes;
	private Map<String, Long> usedMips;
	private Map<String, Long> usedBw;

	private Map<String, Integer> migrationPes;
	private Map<String, Long> migrationMips;
	private Map<String, Long> migrationBw;

	/** The free pes. */
	private List<Integer> freePes;
	private List<Long> freeMips;
	private List<Long> freeBw;
	
	/**
	 * Creates the new VmAllocationPolicySimple object.
	 * 
	 * @param list the list
	 * @pre $none
	 * @post $none
	 */
	public OverbookingVmAllocationPolicy(List<? extends Host> list,
			HostSelectionPolicy hostSelectionPolicy,
			VmMigrationPolicy vmMigrationPolicy) 
	{
		super(list);
		
		this.hostSelectionPolicy = hostSelectionPolicy;
		this.vmMigrationPolicy = vmMigrationPolicy;
		
		if(this.hostSelectionPolicy != null)
			this.hostSelectionPolicy.setVmAllocationPolicy(this);
		
		if(this.vmMigrationPolicy != null)
			this.vmMigrationPolicy.setVmAllocationPolicy(this);

		setFreePes(new ArrayList<Integer>());
		setFreeMips(new ArrayList<Long>());
		setFreeBw(new ArrayList<Long>());
		
		for (Host host : getHostList()) {
			getFreePes().add(host.getNumberOfPes());
			getFreeMips().add(Long.valueOf(host.getTotalMips()));
			getFreeBw().add(host.getBw());
			
//			getFreeMips().add((long) PeProvisionerOverbooking.getOverbookableMips((host.getTotalMips())));
//			getFreeBw().add((long) BwProvisionerOverbooking.getOverbookableBw(host.getBw()));
		}
		hostTotalMips = getHostList().get(0).getTotalMips();
		hostTotalBw =  getHostList().get(0).getBw();
		hostTotalPes =  getHostList().get(0).getNumberOfPes();

		setVmTable(new HashMap<String, Host>());
		setUsedPes(new HashMap<String, Integer>());
		setUsedMips(new HashMap<String, Long>());
		setUsedBw(new HashMap<String, Long>());
		
		migrationPes = new HashMap<String, Integer>();
		migrationMips = new HashMap<String, Long>();
		migrationBw = new HashMap<String, Long>();

	}
	
	/*
	 * (non-Javadoc)
	 * @see org.cloudbus.cloudsim.VmAllocationPolicy#allocateHostForVm(org.cloudbus.cloudsim.Vm,
	 * org.cloudbus.cloudsim.Host)
	 */
	@Override
	public boolean allocateHostForVm(Vm vm, Host host) {
		if (host.vmCreate(vm)) { // if vm has been succesfully created in the host
			getVmTable().put(vm.getUid(), host);
			reserveResource(host, (SDNVm) vm);
			
//			int requiredPes = vm.getNumberOfPes();
//			int idx = getHostList().indexOf(host);
//			getUsedPes().put(vm.getUid(), requiredPes);
//			getFreePes().set(idx, getFreePes().get(idx) - requiredPes);

			Log.formatLine(
					"%.2f: VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),
					CloudSim.clock());
			
			logMaxNumHostsUsed();
			return true;
		}

		return false;
	}
	
	/**
	 * Allocates a host for a given VM.
	 * 
	 * @param vm VM specification
	 * @return $true if the host could be allocated; $false otherwise
	 * @pre $none
	 * @post $none
	 */
	@Override
	public boolean allocateHostForVm(Vm vm) {
		return allocateHostForVm(vm, hostSelectionPolicy.selectHostForVm((SDNVm) vm, this.<SDNHost>getHostList()));
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.VmAllocationPolicy#optimizeAllocation(double, cloudsim.VmList, double)
	 */
	@Override
	public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
		if(vmMigrationPolicy != null)
			return vmMigrationPolicy.getMigrationMap(this.<SDNHost>getHostList());
		
		return null;
	}

	
	protected int maxNumHostsUsed=0;
	public void logMaxNumHostsUsed() {
		// Get how many are used
		int numHostsUsed=0;
		for(int freePes:getFreePes()) {
			if(freePes < hostTotalPes) {
				numHostsUsed++;
			}
		}
		if(maxNumHostsUsed < numHostsUsed)
			maxNumHostsUsed = numHostsUsed;
		Log.printLine("Number of online hosts:"+numHostsUsed + ", max was ="+maxNumHostsUsed);
	}
	public int getMaxNumHostsUsed() { return maxNumHostsUsed;}

	/**
	 * Releases the host used by a VM.
	 * 
	 * @param vm the vm
	 * @pre $none
	 * @post none
	 */
	@Override
	public void deallocateHostForVm(Vm vm) {
		Host host = getVmTable().remove(vm.getUid());
		if (host != null) {
			host.vmDestroy(vm);
			
			removeResource(host, vm);
		}
	}

	/**
	 * Gets the host that is executing the given VM belonging to the given user.
	 * 
	 * @param vm the vm
	 * @return the Host with the given vmID and userID; $null if not found
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Host getHost(Vm vm) {
		return getVmTable().get(vm.getUid());
	}

	/**
	 * Gets the host that is executing the given VM belonging to the given user.
	 * 
	 * @param vmId the vm id
	 * @param userId the user id
	 * @return the Host with the given vmID and userID; $null if not found
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Host getHost(int vmId, int userId) {
		return getVmTable().get(Vm.getUid(userId, vmId));
	}

	/**
	 * Gets the vm table.
	 * 
	 * @return the vm table
	 */
	public Map<String, Host> getVmTable() {
		return vmTable;
	}

	/**
	 * Sets the vm table.
	 * 
	 * @param vmTable the vm table
	 */
	protected void setVmTable(Map<String, Host> vmTable) {
		this.vmTable = vmTable;
	}

	/**
	 * Gets the used pes.
	 * 
	 * @return the used pes
	 */
	protected Map<String, Integer> getUsedPes() {
		return usedPes;
	}

	/**
	 * Sets the used pes.
	 * 
	 * @param usedPes the used pes
	 */
	protected void setUsedPes(Map<String, Integer> usedPes) {
		this.usedPes = usedPes;
	}

	/**
	 * Gets the free pes.
	 * 
	 * @return the free pes
	 */
	protected List<Integer> getFreePes() {
		return freePes;
	}

	/**
	 * Sets the free pes.
	 * 
	 * @param freePes the new free pes
	 */
	protected void setFreePes(List<Integer> freePes) {
		this.freePes = freePes;
	}

	protected Map<String, Long> getUsedMips() {
		return usedMips;
	}
	protected void setUsedMips(Map<String, Long> usedMips) {
		this.usedMips = usedMips;
	}
	protected Map<String, Long> getUsedBw() {
		return usedBw;
	}
	protected void setUsedBw(Map<String, Long> usedBw) {
		this.usedBw = usedBw;
	}
	protected List<Long> getFreeMips() {
		return this.freeMips;
	}
	protected void setFreeMips(List<Long> freeMips) {
		this.freeMips = freeMips;
	}
	
	protected List<Long> getFreeBw() {
		return this.freeBw;
	}
	protected void setFreeBw(List<Long> freeBw) {
		this.freeBw = freeBw;
	}
	

	
	protected int findHostIdx(Host h) {
		for(int i=0; i< getHostList().size(); i++) {
			if(getHostList().get(i).equals(h)) {
				return i;
			}
		}
		return -1;
	}
	
	protected boolean allocateHostForVm(Vm vm, List<Host> candidateHosts) {
		if (getVmTable().containsKey(vm.getUid())) { // if this vm was not created
			return false;
		}
		boolean result = false;
		
		for(Host host:candidateHosts) {
			result = host.vmCreate(vm);

			if (result) { 
				// if vm were succesfully created in the host
				getVmTable().put(vm.getUid(), host);
				reserveResource(host, (SDNVm) vm);
				break;
			}			
		}

		if(!result) {
			System.err.println("VmAllocationPolicy: WARNING:: Cannot create VM!!!!");
		}
		logMaxNumHostsUsed();
		return result;
	}

	protected static double convertWeightedMetric(double mipsPercent, double bwPercent) {
		double ret = mipsPercent * bwPercent;
		return ret;
	}
	
	protected double[] buildFreeResourceMetric(List<? extends Host> hosts) {
		double[] freeResources = new double[hosts.size()];
		for (int i = 0; i < hosts.size(); i++) {
			Host h = hosts.get(i);
			
			double mipsFreePercent = (double)getAvailableMips(h)/ this.hostTotalMips; 
			double bwFreePercent = (double)getAvailableBw(h) / this.hostTotalBw;
			
			freeResources[i] = convertWeightedMetric(mipsFreePercent, bwFreePercent);
		}
		
		return freeResources;
	}
	
	protected long getAvailableMips(Host host) {
		int idx = findHostIdx(host);
		long freeMips = (long) getFreeMips().get(idx);
		
		return freeMips;		
	}
	
	protected long getAvailableBw(Host host) {
		int idx = findHostIdx(host);
		long freeBw = (long) getFreeBw().get(idx);
		
		return freeBw;		
	}

	protected double getOverRatioMips(SDNVm vm, Host host) {
		Long usedMips = getUsedMips().get(vm.getUid());
		if(usedMips == null) {
			// New VM that is not allocated yet
			return Configuration.OVERBOOKING_RATIO_INIT;
		}
		else {
			// VM already exists: do migration
			return getDynamicOverRatioMips(vm, host);
		}
	}
	
	protected double getOverRatioBw(SDNVm vm, Host host) {
		Long usedBw = getUsedBw().get(vm.getUid());
		if(usedBw == null) {
			// New VM that is not allocated yet
			return Configuration.OVERBOOKING_RATIO_INIT;
		}
		else {
			// VM already exists: for migration. use dynamic OR
			return getDynamicOverRatioBw(vm, host);
		}
	}
	
	protected double getDynamicOverRatioMips(SDNVm vm, Host host) {		
		// If utilization history is not enough
		if(vm.getMonitoringValuesVmCPUUtilization().getNumberOfPoints() == 0) {
			return Configuration.OVERBOOKING_RATIO_INIT;
		}
		
		final double avgCC = getAverageCorrelationCoefficientMips((SDNVm) vm, (SDNHost)host);	// Average Correlation between -1 and 1
		final double delta = Configuration.OVERBOOKING_RATIO_MAX - Configuration.OVERBOOKING_RATIO_MIN;
		if(avgCC >1 || avgCC <-1) {
			System.err.println("getDynamicOverRatioMips: CC is wrong! "+avgCC);
			System.exit(0);
		}
		double endTime = CloudSim.clock();
		double timeWindow = Configuration.overbookingTimeWindowNumPoints * Configuration.overbookingTimeWindowInterval;		
		double startTime = endTime - timeWindow > 0 ? endTime - timeWindow : 0;
		final double avgUtil = vm.getMonitoringValuesVmCPUUtilization().getAverageValue(startTime, endTime);

		double deltaUtil = avgUtil* Configuration.OVERBOOKING_RATIO_UTIL_PORTION;
		double adjustDelta = (delta-Configuration.OVERBOOKING_RATIO_UTIL_PORTION) * (avgCC+1)/2.0;
		
		adjustDelta += deltaUtil;
		
		if(adjustDelta < avgUtil )
			adjustDelta = avgUtil;
		
		if(adjustDelta>delta)
			adjustDelta=delta;
		
		double ratio = Configuration.OVERBOOKING_RATIO_MIN + adjustDelta;
		
		Log.printLine(CloudSim.clock() + ": getDynamicOverRatioMips() " + vm + " to "+host+" Util%%="+ avgUtil+", CC+1%%="+(avgCC+1)+", Ratio="+ratio);

		return ratio;	// AvgCC+1 is between 0 and 2
	}
	
	protected double getDynamicOverRatioBw(SDNVm vm, Host host) {
		if(vm.getMonitoringValuesVmBwUtilization().getNumberOfPoints() == 0) {
			return Configuration.OVERBOOKING_RATIO_INIT;
		}
		
		double avgCC = getAverageCorrelationCoefficientBW((SDNVm) vm, (SDNHost)host);	// Average Correlation between -1 and 1
		double delta = Configuration.OVERBOOKING_RATIO_MAX - Configuration.OVERBOOKING_RATIO_MIN;
		if(avgCC >1 || avgCC <-1) {
			System.err.println("getDynamicOverRatioMips: CC is wrong! "+avgCC);
			System.exit(0);
		}
		return Configuration.OVERBOOKING_RATIO_MIN + (avgCC+1)*delta/2.0 ;	// AvgCC+1 is between 0 and 2
	}
	
	protected double getAverageCorrelationCoefficientBW(SDNVm newVm, SDNHost host) {
		if(host.getVmList().size() == 0) {
			//System.err.println("getAverageCorrelationCoefficient: No VM in the host");
			return -1;
		}
		double interval = Configuration.overbookingTimeWindowInterval;
		double timeWindow = Configuration.overbookingTimeWindowNumPoints * interval;
		double endTime = CloudSim.clock();
		double startTime = endTime - timeWindow > 0 ? endTime - timeWindow : 0;
		
		double sumCoef= 0.0;
		double [] newVmHistory = newVm.getMonitoringValuesVmBwUtilization().getValuePoints(startTime, endTime, interval);
		
		for(SDNVm v:host.<SDNVm>getVmList()) {
			// calculate correlation coefficient between the target VM and existing VMs in the host.
			double [] vHistory = v.getMonitoringValuesVmBwUtilization().getValuePoints(startTime, endTime, interval);
			double cc = calculateCorrelationCoefficient(newVmHistory, vHistory);
			if(cc >= -1 && cc <= 1)
				sumCoef += cc;
		}
		
		return sumCoef / host.getVmList().size();
	}
	
	protected static double getAverageCorrelationCoefficientMips(SDNVm newVm, SDNHost host) {
		if(host.getVmList().size() == 0) {
			//System.err.println("getAverageCorrelationCoefficient: No VM in the host");
			return -1;
		}
		double interval = Configuration.overbookingTimeWindowInterval;
		double timeWindow = Configuration.overbookingTimeWindowNumPoints * interval;
		double endTime = CloudSim.clock();
		double startTime = endTime - timeWindow > 0 ? endTime - timeWindow : 0;
		
		double sumCoef= 0.0;
		double [] newVmHistory = newVm.getMonitoringValuesVmCPUUtilization().getValuePoints(startTime, endTime, interval);
		if(newVmHistory == null)
			return -1;
		
		for(SDNVm v:host.<SDNVm>getVmList()) {
			// calculate correlation coefficient between the target VM and existing VMs in the host.
			double [] vHistory = v.getMonitoringValuesVmCPUUtilization().getValuePoints(startTime, endTime, interval);
			double cc = calculateCorrelationCoefficient(newVmHistory, vHistory);
			if(cc >= -1 && cc <= 1)
				sumCoef += cc;
		}
		
		return sumCoef / host.getVmList().size();
	}
	
	private static PearsonsCorrelation pearson = new PearsonsCorrelation();
	public static double calculateCorrelationCoefficient(double [] x, double [] y) {
		if(x.length > 1)
			return pearson.correlation(x, y);
		
		return 0.0;
	}
	
	protected long getVmAllocatedMips(SDNVm vm) {
		Long mips = getUsedMips().get(vm.getUid());
		if(mips != null)
			return (long)mips;
		return -1;
	}
	
	protected double getCurrentHostOverbookingRatio(Host host) {
		long allAllocatedMips = 0;
		long allRequestedMips = 0;
		
		for(SDNVm vm:host.<SDNVm>getVmList()) {
			long vmAllocatedMips = getVmAllocatedMips(vm);
			if(vmAllocatedMips != -1) {
				allAllocatedMips += vmAllocatedMips;
				allRequestedMips += vm.getTotalMips();
			}
		}
		
		return (double)allAllocatedMips/allRequestedMips;
	}

	public boolean isResourceAllocatable(Host host, SDNVm vm)
	{

		int idx = findHostIdx(host);
		
		//int pe = vm.getNumberOfPes(); // Do not check PE for overbooking: sharable
		double mips = vm.getTotalMips(); //getCurrentRequestedTotalMips();
		long bw = vm.getBw(); //CurrentRequestedBw();
		
		long freeMips = (long) getFreeMips().get(idx);
		long freeBw = (long) getFreeBw().get(idx);
		
		double overbookingRatioMips = getOverRatioMips(vm, host);
		double overbookinRatioBw = getOverRatioBw(vm, host);
				
		// Check whether the host can hold this VM or not.
		if( freeMips < mips * overbookingRatioMips) {
			/*
			System.err.format("%s:not enough MIPS: avail=%d,req=%f (OR=%.2f) / BW avail=%d, req=%d (OR=%.2f)\n", host.toString(), 
					freeMips, mips, overbookingRatioMips,
					freeBw, bw, overbookinRatioBw);
					*/
			return false;
		}
		
		if( freeBw < bw * overbookinRatioBw) {
			
			System.err.format("%s:not enough BW: avail=%d, req=%f (OR=%.2f) / BW avail=%d, req=%d (OR=%.2f)\n", host.toString(), 
					freeMips, mips, overbookingRatioMips,
					freeBw, bw, overbookinRatioBw);
			return false;
		}
		
		return true;
	}
	
	public void redistributeOverbookedResource(Host host) {
		// Update the overbooked resource allocation ratio of every VM in the host
		// It changed overbooking ratio from initial to the up-to-date 
		// after utilization history is applied.
		
		for(SDNVm vm:host.<SDNVm>getVmList()) {
			reallocateResourceVm(host, vm);
		}
	}
	
	public double getCurrentOverbookingRatioMips(SDNVm vm) {
		Long allocatedMips = getUsedMips().get(vm.getUid());
		Long requiredMips = vm.getTotalMips();
		
		return (double)allocatedMips/(double)requiredMips;
	}
	
	public double getCurrentOverbookingRatioBw(SDNVm vm) {
		Long allocatedBw = getUsedBw().get(vm.getUid());
		double requiredBw = (long)vm.getBw();
		
		return (double)allocatedBw/requiredBw;
	}
	
	private void reallocateResourceVm(Host host, SDNVm vm) {
		// Reallocate resources reflecting historical utilization data
		// Each VM's overbooking ratio will be updated
		int idx = findHostIdx(host);
		
		double overbookingRatioMips =getOverRatioMips(vm, host);
		double overbookinRatioBw =getOverRatioBw(vm, host);
		
//		int pe = vm.getNumberOfPes();
		double adjustedMips = vm.getTotalMips()*overbookingRatioMips;
		long adjustedBw = (long) (vm.getBw()*overbookinRatioBw);

		// ReAllocate adjusted PEs 
//		Integer pes = getUsedPes().remove(vm.getUid());
//		getFreePes().set(idx, getFreePes().get(idx) + pes);
//		getUsedPes().put(vm.getUid(), pe);
//		getFreePes().set(idx, getFreePes().get(idx) - pe);

		// Remove previous MIPs and allocated adjusted MIPs
		Long mips = getUsedMips().remove(vm.getUid());
		if(mips != null) {
			getFreeMips().set(idx, getFreeMips().get(idx) + mips);
			getUsedMips().put(vm.getUid(), (long) adjustedMips);
			getFreeMips().set(idx,  (long) (getFreeMips().get(idx) - adjustedMips));
			
			Log.printLine(CloudSim.clock() + ": reallocateResource() " + vm + " MIPS:"+ mips+"->"+adjustedMips+"(OR:"+overbookingRatioMips+")");
		}
		else
			System.err.println(vm+" mips is not allocated!");

		// Remove previous BWs and allocate adjusted BWs
		Long bw = getUsedBw().remove(vm.getUid());
		if(bw != null) {
			getFreeBw().set(idx, getFreeBw().get(idx) + bw);
			getUsedBw().put(vm.getUid(), (long) adjustedBw);
			getFreeBw().set(idx, (long) (getFreeBw().get(idx) - adjustedBw));
			
			Log.printLine(CloudSim.clock() + ": reallocateResource() " + vm + " BW:"+ bw+"->"+adjustedBw+"(OR:"+overbookinRatioBw+")");
		}
		else
			System.err.println(vm+" bw is not allocated!");
	}
	
	// Temporary resource reservation for migration purpose
	// Remove required amount from the available resource of target host
	protected void reserveResourceForMigration(Host host, SDNVm vm) {
		int idx = findHostIdx(host);
		
		double overbookingRatioMips =getOverRatioMips(vm, host);
		double overbookinRatioBw =getOverRatioBw(vm, host);
		
		int pe = vm.getNumberOfPes();
		double adjustedMips = vm.getTotalMips()*overbookingRatioMips;
		long adjustedBw = (long) (vm.getBw()*overbookinRatioBw);
		
		migrationPes.put(vm.getUid(), pe);
		getFreePes().set(idx, getFreePes().get(idx) - pe);
		
		migrationMips.put(vm.getUid(), (long) adjustedMips);
		getFreeMips().set(idx,  (long) (getFreeMips().get(idx) - adjustedMips));

		migrationBw.put(vm.getUid(), (long) adjustedBw);
		getFreeBw().set(idx, (long) (getFreeBw().get(idx) - adjustedBw));

		Log.printLine(CloudSim.clock() + ": reserveResourceForMigration() " + vm + " MIPS:"+adjustedMips+"(OR:"+overbookingRatioMips+")");
		Log.printLine(CloudSim.clock() + ": reserveResourceForMigration() " + vm + " BW:"+ adjustedBw+"(OR:"+overbookinRatioBw+")");
	}
	
	protected boolean finaliseResourceAfterMigration(SDNVm vm) {
		Integer pe = migrationPes.remove(vm.getUid());
		Long mips = migrationMips.remove(vm.getUid());
		Long bw = migrationBw.remove(vm.getUid());
		
		if(pe == null) {
			// This VM was not in migration
			return false;
		}
		
		if(getUsedPes().get(vm.getUid()) != null) {
			System.out.println(vm+ " VM resource reservation is not released yet! ");
			System.exit(1);
		}

		getUsedPes().put(vm.getUid(), pe);
		getUsedMips().put(vm.getUid(), (long) mips);
		getUsedBw().put(vm.getUid(), (long) bw);

		return true;
	}

	// Reserve resource in the Host for the VM
	protected void reserveResource(Host host, SDNVm vm) {
		vm.addMigrationHistory((SDNHost) host);
		
		if(finaliseResourceAfterMigration((SDNVm) vm)) {
			// VM was in migration, the resource is already reserved during migration preparation process.
			// Do not need to duplicate resource reservation. return
			return;
		}
		
		// Error check
		if(getUsedPes().get(vm.getUid()) != null) {
			System.err.println(vm+" is already in the host! "+host);
			System.exit(1);
		}
		
		int idx = findHostIdx(host);
		
		double overbookingRatioMips =getOverRatioMips(vm, host);
		double overbookinRatioBw =getOverRatioBw(vm, host);
		
		int pe = vm.getNumberOfPes();
		double adjustedMips = vm.getTotalMips()*overbookingRatioMips;
		long adjustedBw = (long) (vm.getBw()*overbookinRatioBw);
		
		getUsedPes().put(vm.getUid(), pe);
		getFreePes().set(idx, getFreePes().get(idx) - pe);
		
		getUsedMips().put(vm.getUid(), (long) adjustedMips);
		getFreeMips().set(idx,  (long) (getFreeMips().get(idx) - adjustedMips));

		getUsedBw().put(vm.getUid(), (long) adjustedBw);
		getFreeBw().set(idx, (long) (getFreeBw().get(idx) - adjustedBw));

		Log.printLine(CloudSim.clock() + ": reserveResource() " + vm + " MIPS:"+adjustedMips+"(OR:"+overbookingRatioMips+")");
		Log.printLine(CloudSim.clock() + ": reserveResource() " + vm + " BW:"+ adjustedBw+"(OR:"+overbookinRatioBw+")");

	}

	protected void removeResource(Host host, Vm vm) {
		if (host != null) {
			int idx = getHostList().indexOf(host);
			
			Integer pes = getUsedPes().remove(vm.getUid());
			getFreePes().set(idx, getFreePes().get(idx) + pes);
			
			Long mips = getUsedMips().remove(vm.getUid());
			getFreeMips().set(idx, getFreeMips().get(idx) + mips);
			
			Long bw = getUsedBw().remove(vm.getUid());
			getFreeBw().set(idx, getFreeBw().get(idx) + bw);
		}
	}

	
	public static boolean isHostOverloaded(SDNHost host, double startTime, double endTime) {

		/*
		double hostCPUUtil = host.getMonitoringValuesHostCPUUtilization().getAverageValue(startTime, endTime);
		
		if(hostCPUUtil > Configuration.OVERLOAD_THRESHOLD) {
//			double hostOverRatio = getCurrentHostOverbookingRatio(host);
//			for(SDNVm vm:host.<SDNVm>getVmList()) {
//				double vmUtil = vm.getMonitoringValuesVmCPUUtilization().getAverageValue(startTime, endTime);
//				if(vmUtil < hostOverRatio - Configuration.OVERLOAD_THRESHOLD_ERROR) {
//					return false;
//				}
//			}
			return true;
		}
		/*/
		double overloadPercentile = host.getMonitoringValuesOverloadMonitor().getOverUtilizedPercentile(startTime, endTime, 1.0);
		if(overloadPercentile > Configuration.OVERLOAD_HOST_PERCENTILE_THRESHOLD) {
			Log.printLine(CloudSim.clock() + ": isHostOverloaded() CPU "+host+":  " + overloadPercentile);
			return true;
		}
		
		//*/
		
		double hostBwUsage = host.getMonitoringValuesHostBwUtilization().getAverageValue(startTime, endTime);
		if(hostBwUsage > Configuration.OVERLOAD_THRESHOLD_BW_UTIL) {
			Log.printLine(CloudSim.clock() + ": isHostOverloaded() "+host+": BW " + hostBwUsage);
//			System.err.println(host+" BW is overloaded:"+hostBwUsage);
			return true;
		}
		return false;
	}


	protected static List<SDNHost> getUnderutilizedHosts(List<SDNHost> hosts) {
		List<SDNHost> underHosts = new ArrayList<SDNHost>();
		double endTime = CloudSim.clock();
		double startTime = endTime - Configuration.migrationTimeInterval;
		for(SDNHost host:hosts) {
			if(host.getMonitoringValuesHostCPUUtilization().getAverageValue(startTime, endTime) < Configuration.UNDERLOAD_THRESHOLD_HOST ){
				if(host.getMonitoringValuesHostBwUtilization().getAverageValue(startTime, endTime) < Configuration.UNDERLOAD_THRESHOLD_HOST_BW ){
					underHosts.add(host);
				}
			}
		}
		return underHosts;
	}

	protected List<SDNVm> getUnderUtilizedVmList(SDNHost host) {
		List<SDNVm> vms = host.getVmList();
		double endTime = CloudSim.clock();
		double startTime = endTime - Configuration.migrationTimeInterval;
		List<SDNVm> underUtilized = new ArrayList<SDNVm>();

		for(SDNVm vm:vms) {
			double util = vm.getMonitoringValuesVmCPUUtilization().getAverageValue(startTime, endTime);
			if( util < Configuration.UNDERLOAD_THRESHOLD_VM) {
				System.out.println("This VM is underutilized, moving to migration list:"+vm);
				underUtilized.add(vm);
			}
		}
		
		return underUtilized;
	}	
}

