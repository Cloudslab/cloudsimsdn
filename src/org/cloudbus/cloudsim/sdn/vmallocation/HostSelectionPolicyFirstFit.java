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

public class HostSelectionPolicyFirstFit extends HostSelectionPolicy {
	@Override
	public List<Host> selectHostForVm(SDNVm vm, List<SDNHost> hosts) {
		return getFirstFitHostsForVm(vm, hosts, vmAllocPolicy);
	}

	public static List<Host> getFirstFitHostsForVm(SDNVm vm, List<SDNHost> hosts, VmAllocationPolicyEx vmAllocPolicy) {
		int numHosts = hosts.size();
		List<Host> hostCandidates = new ArrayList<Host>();
		boolean result = false;
		
		// Find the fit host for VM 
		for(int idx = 0; result == false && idx < numHosts; idx++) {
			SDNHost host = hosts.get(idx);
			if(vmAllocPolicy.isResourceAllocatable(host, vm)) {
				hostCandidates.add(host);
			}
		}
		
		return hostCandidates;
	}
}
