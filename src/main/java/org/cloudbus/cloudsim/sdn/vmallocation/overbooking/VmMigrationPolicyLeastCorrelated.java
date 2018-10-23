/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicyFirstFit;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicyMostFull;
import org.cloudbus.cloudsim.sdn.vmallocation.VmMigrationPolicy;

public class VmMigrationPolicyLeastCorrelated extends VmMigrationPolicy{

	@Override
	protected Map<Vm, Host> buildMigrationMap(List<SDNHost> hosts) {
		Map<Vm, Host> vmToHost = new HashMap<Vm, Host>();
		
		// Check peak VMs and reallocate them into different host
		List<SDNVm> migrationOverVMList = getMostUtilizedVms(hosts);		
		if(migrationOverVMList.size() == 0) {
			return vmToHost;
		}
		
		for(SDNVm vmToMigrate:migrationOverVMList) {
			// 1. get least correlated hosts among active ones
			List<SDNHost> activeHosts = getActiveHost(hosts);
			List<SDNHost> sortedHosts = VmMigrationPolicyLeastCorrelated.<SDNHost>sortLeastCorrelatedHosts(vmToMigrate, activeHosts);
			List<Host> targetHosts = HostSelectionPolicyFirstFit.getFirstFitHostsForVm(vmToMigrate, sortedHosts, vmAllocationPolicy);
			
			Host migratedHost = null;

			if(targetHosts.size() > 0) {
				migratedHost = moveVmToHost(vmToMigrate, targetHosts);
			}
			
			if(migratedHost == null) {
				targetHosts = HostSelectionPolicyMostFull.getMostFullHostsForVm(vmToMigrate, hosts, vmAllocationPolicy);
				migratedHost = moveVmToHost(vmToMigrate, targetHosts);
			}				
				
			// 3. No host can serve this VM, do not migrate.
			if(migratedHost == null) {
				System.err.println(vmToMigrate + ": Cannot find target host to migrate");
				//System.exit(-1);
				continue;
			}
			
			vmToHost.put(vmToMigrate, migratedHost);
		}
		return vmToHost;

	}
	
	protected static List<SDNHost> getActiveHost(List<SDNHost> hostList) {
		List<SDNHost> activeHosts = new ArrayList<SDNHost>();
		for(SDNHost host:hostList) {
			if(getAverageUtilizationMips(host) > Configuration.HOST_ACTIVE_AVERAGE_UTIL_THRESHOLD) {
				activeHosts.add(host);
			}
		}
		return activeHosts;
	}
	
	protected static double getAverageUtilizationMips(SDNHost host) {
		double endTime = CloudSim.clock();
		double startTime = endTime - Configuration.migrationTimeInterval;
		double util = host.getMonitoringValuesHostCPUUtilization().getAverageValue(startTime, endTime);
		
		return util;
	}

	@SuppressWarnings("unchecked")
	protected static <T extends Host> List<T>  sortLeastCorrelatedHosts(SDNVm vm, List<? extends Host> hostList) {
		List<? extends Host> hosts = new ArrayList<Host>(hostList);	// for sorting

		// Calculate correlation factors
		final Map<Host, Double> corr = new HashMap<Host, Double>();
		for(Host h:hosts) {
			//double avgCC = getAverageCorrelationCoefficientMipsHost(vm, (SDNHost)h);
			double avgCC = OverbookingVmAllocationPolicy.getAverageCorrelationCoefficientMips(vm, (SDNHost)h);
			corr.put(h, avgCC);
		}
		
		// Sort the most utilized VMs
		Collections.sort(hosts, new Comparator<Host>() {
		    public int compare(Host o1, Host o2) {
		    	double o1cc = corr.get(o1);
		    	double o2cc = corr.get(o2);
		        return Double.compare(o1cc, o2cc);
		    }
		});
		return (List<T>) hosts;
	}
	
	
	protected static double getAverageCorrelationCoefficientMipsHost(SDNVm newVm, SDNHost host) {
		if(host.getVmList().size() == 0) {
			//System.err.println("getAverageCorrelationCoefficient: No VM in the host");
			return -1;
		}
		double interval = Configuration.overbookingTimeWindowInterval;
		double timeWindow = Configuration.overbookingTimeWindowNumPoints * interval;
		double endTime = CloudSim.clock();
		double startTime = endTime - timeWindow > 0 ? endTime - timeWindow : 0;
		
		double [] newVmHistory = newVm.getMonitoringValuesVmCPUUtilization().getValuePoints(startTime, endTime, interval);
		// calculate correlation coefficient between the target VM and existing VMs in the host.
		double [] vHistory = host.getMonitoringValuesHostCPUUtilization().getValuePoints(startTime, endTime, interval);
		double cc = OverbookingVmAllocationPolicy.calculateCorrelationCoefficient(newVmHistory, vHistory);
		
		return cc;
	}
}
