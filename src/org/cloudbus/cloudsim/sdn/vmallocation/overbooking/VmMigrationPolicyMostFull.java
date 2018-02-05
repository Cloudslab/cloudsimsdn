/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicyMostFull;
import org.cloudbus.cloudsim.sdn.vmallocation.VmMigrationPolicy;

public class VmMigrationPolicyMostFull extends VmMigrationPolicy {
	
	// Build migration map for overloaded hosts and VMs
	protected Map<Vm, Host> buildMigrationMap(List<SDNHost> hosts) {
		Map<Vm, Host> vmToHost = new HashMap<Vm, Host>();
		
		// Check peak VMs and reallocate them into different host
		List<SDNVm> migrationOverVMList = getMostUtilizedVms(hosts);		
		if(migrationOverVMList.size() == 0) {
			return vmToHost;
		}
		
		for(SDNVm vmToMigrate:migrationOverVMList) {
			List<Host> targetHosts = HostSelectionPolicyMostFull.getMostFullHostsForVm(vmToMigrate, hosts, vmAllocationPolicy);
			
			// If no host can serve this VM, do not migrate.
			if(targetHosts == null || targetHosts.size() == 0) {
				System.err.println(vmToMigrate + ": Cannot find target host to migrate");
				//System.exit(-1);
				continue;
			}
			
			Host host = moveVmToHost(vmToMigrate, targetHosts);
			if(host == null) {
				System.err.println("VmAllocationPolicy: WARNING:: Cannot migrate VM!!!!"+vmToMigrate); 
				System.exit(0);
			}
			
			vmToHost.put(vmToMigrate, host);

		}
		return vmToHost;

	}
}
