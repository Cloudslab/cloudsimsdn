/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;

// Assumption: Hosts are homogeneous
// This class holds free MIPS/BW information after allocation is done.
// The class holds all hosts information

public class VmAllocationPolicyEx extends VmAllocationPolicy implements PowerUtilizationMaxHostInterface {

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
	public VmAllocationPolicyEx(List<? extends Host> list,
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
	 * Allocates a host for a given VM. It determines the host by hostSelectionPolicy
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
			System.err.println("VmAllocationPolicyEx: WARNING:: Cannot create VM!!!!");
		}
		
		logMaxNumHostsUsed();
		return result;
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
			System.err.format("%s:not enough MIPS: avail=%d,req=%f (OR=%.2f) / BW avail=%d, req=%d (OR=%.2f)\n", host.toString(), 
					freeMips, mips, overbookingRatioMips,
					freeBw, bw, overbookinRatioBw);
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
	
	/**
	 * Creates a migration map that describes which VM to migrate to which host
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
	
	public int getMaxNumHostsUsed() {
		return maxNumHostsUsed;
	}

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


	protected static double convertWeightedMetric(double mipsPercent, double bwPercent) {
		double ret = mipsPercent * bwPercent;
		return ret;
	}
	
	public double[] buildFreeResourceMetric(List<? extends Host> hosts) {
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
		return 1.0;	// 100% requested resource is given. No overbooking
	}
	
	protected double getOverRatioBw(SDNVm vm, Host host) {
		return 1.0;	// 100% requested resource is given. No overbooking
	}
	
	public void updateResourceAllocation(Host host) {
		// Update the resource allocation ratio of every VM
		return;
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
	
	private boolean finaliseResourceAfterMigration(SDNVm vm) {
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
	private void reserveResource(Host host, SDNVm vm) {
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


}

