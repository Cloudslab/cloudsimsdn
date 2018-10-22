/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;

public class HostSelectionPolicyMostFull extends HostSelectionPolicy {
	@Override
	public List<Host> selectHostForVm(SDNVm vm, List<SDNHost> hosts) {
		return getMostFullHostsForVm(vm, hosts, vmAllocPolicy);
	}

	public static List<Host> getMostFullHostsForVm(SDNVm vm, List<SDNHost> hosts, VmAllocationPolicyEx vmAllocPolicy) {
		int numHosts = hosts.size();
		List<Host> hostCandidates = new ArrayList<Host>();

		// 1. Find/Order the best host for this VM by comparing a metric
		boolean result = false;
		
		// freeReousrces : Weighted-calculated free resource percentage in each host 
		double[] freeResources = vmAllocPolicy.buildFreeResourceMetric(hosts);

		// Find the most full host, with available resource. 
		for(int tries = 0; result == false && tries < numHosts; tries++) {
			// we still trying until we find a host or until we try all of them
			double lessFree = Double.POSITIVE_INFINITY;
			int idx = -1;

			// Find the most full host
			for (int i = 0; i < numHosts; i++) {
				if (freeResources[i] < lessFree) {
					lessFree = freeResources[i];
					idx = i;
				}
			}
			freeResources[idx] = Double.POSITIVE_INFINITY;	// Mark visited
			
			SDNHost host = hosts.get(idx);
			if(vmAllocPolicy.isResourceAllocatable(host, vm)) {
				hostCandidates.add(host);
			}
		}
		
		return hostCandidates;
	}
}
