/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicy;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyEx;
import org.cloudbus.cloudsim.sdn.vmallocation.VmMigrationPolicy;

// Assumption: Hosts are homogeneous
// This class holds free MIPS/BW information after allocation is done.
// The class holds all hosts information

public class OverbookingVmAllocationPolicy extends VmAllocationPolicyEx implements PowerUtilizationMaxHostInterface {

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
		super(list, hostSelectionPolicy, vmMigrationPolicy);
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

	public void updateResourceAllocation(Host host) {
		// Update the overbooked resource allocation ratio of every VM in the host
		// It changes overbooking ratio based on the utilization history
		
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

