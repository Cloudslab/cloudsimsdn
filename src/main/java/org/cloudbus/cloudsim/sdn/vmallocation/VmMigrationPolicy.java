/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;

public abstract class VmMigrationPolicy {
	protected abstract Map<Vm, Host> buildMigrationMap(List<SDNHost> hosts);

	protected VmAllocationPolicyEx vmAllocationPolicy = null;
	
	public VmMigrationPolicy() {
	}
	
	public void setVmAllocationPolicy(VmAllocationPolicyEx vmAllocationPolicyEx) {
		vmAllocationPolicy = vmAllocationPolicyEx;
	}
	
	public List<Map<String, Object>> getMigrationMap(List<SDNHost> hosts) {
		Map<Vm, Host> vmToHost = buildMigrationMap(hosts);
		
		// Make a list from the migration map
		List<Map<String, Object>> migrationList = new ArrayList<Map<String, Object>>();
		
		for(Vm vmToMigrate:vmToHost.keySet()) {
			Host host = vmToHost.get(vmToMigrate);
			
			Map<String, Object> migrationMap = new HashMap<String, Object>();
			migrationMap.put("vm", vmToMigrate);
			migrationMap.put("host", host);
			migrationList.add(migrationMap);
		}
		return migrationList;
	}
		
	protected Host moveVmToHost(SDNVm vmToMigrate, List<Host> targetHosts) {		
		// Remove myself from the target hosts (Do not migrate to the same host) 
		List<SDNHost> myHost = new ArrayList<SDNHost>();
		myHost.add((SDNHost) vmToMigrate.getHost());
		targetHosts.removeAll(myHost);

		// Pre-allocate resource from candidate hosts for the migrating VM 
		boolean result = false;
		Host host = null;
		for(int i=0; i<targetHosts.size(); i++) {
			host = targetHosts.get(i);
			result = host.isSuitableForVm(vmToMigrate);

			if (result) { // if vm is suitable for the host
				vmAllocationPolicy.reserveResourceForMigration(host, vmToMigrate);
				break;
			}
		}
		if(!result)
			host = null;
	
		return host;
	}
		
	protected SDNVm getMostUtilizedVm(SDNHost host) {
		List<SDNVm> vms = host.getVmList();
		double endTime = CloudSim.clock();
		double startTime = endTime - Configuration.migrationTimeInterval;
		double maxUtilization = 0;
		SDNVm mostUtilized = null;

		// Find the most utilized VM within the host
		for(SDNVm vm:vms) {
			SDNVm svm = (SDNVm)vm;
			double util = svm.getMonitoringValuesVmCPUUtilization().getAverageValue(startTime, endTime);
			if( util >= maxUtilization) {
				maxUtilization = util;
				mostUtilized = svm;
			}
		}
		
		return mostUtilized;
	}
	
	protected List<SDNHost> getOverutilizedHosts(List<SDNHost> hosts) {
		List<SDNHost> overHosts = new ArrayList<SDNHost>();
		double endTime = CloudSim.clock();
		double startTime = endTime - Configuration.migrationTimeInterval;
		if(startTime <0) startTime = 0;
		
		for(SDNHost host:hosts) {
			// Re-adjust each VM's allocated resource applying historical utilization data 
			vmAllocationPolicy.updateResourceAllocation(host);
			
			// Criteria to decided overloaded hosts
			// 1. Host's utilization level should be higher than threshold
			// 2. Each VMs in the Host are also overheaded
			if(isHostOverloaded(host, startTime, endTime)) {
				overHosts.add(host);
			}
		}
		return overHosts;
	}
	
	protected List<SDNVm> getMostUtilizedVms(List<SDNHost> hosts) {
		List<SDNVm> migrationOverVMList = new ArrayList<SDNVm>();

		// Check over-utilized host
		List<SDNHost> overHosts = getOverutilizedHosts(hosts);
		
		// Move the most over-headed VM into migration list
		if(overHosts != null && overHosts.size() != 0) {
			for(SDNHost host: overHosts) {
				SDNVm mostUtilized = getMostUtilizedVm(host);
				if(mostUtilized != null)
					migrationOverVMList.add(mostUtilized);
			}
		}
		
		if(migrationOverVMList.size() == 0) {
			return migrationOverVMList;
		}
		
		// Sort the most utilized VMs
		Collections.sort(migrationOverVMList, new Comparator<SDNVm>() {
		    public int compare(SDNVm o1, SDNVm o2) {
				double endTime = CloudSim.clock();
				double startTime = endTime - Configuration.migrationTimeInterval;
				
		    	double o1util = o1.getMonitoringValuesVmCPUUtilization().getAverageValue(startTime, endTime);
		    	double o2util = o2.getMonitoringValuesVmCPUUtilization().getAverageValue(startTime, endTime);
		        return (int) (o1util - o2util);
		    }
		});

		return migrationOverVMList;
	}

	private static boolean isHostOverloaded(SDNHost host, double startTime, double endTime) {

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


}
