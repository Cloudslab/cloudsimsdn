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

public class VmMigrationPolicyUnderutilizedMostFull extends VmMigrationPolicy{

	@Override
	protected Map<Vm, Host> buildMigrationMap(List<SDNHost> hosts) {
		Map<Vm, Host> vmToHost = new HashMap<Vm, Host>();
		// Check peak VMs and reallocate them into different host
		List<SDNVm> migrationOverVMList = getMostUtilizedVms(hosts);
		
		List<SDNHost> underHosts = OverbookingVmAllocationPolicy.getUnderutilizedHosts(hosts);
		
		for(SDNVm vmToMigrate:migrationOverVMList) {
			List<Host> targetHosts = null;
			Host migratedHost = null;
			
			// 1. Check whether this VM fits into the under-utilized hosts
			if(underHosts.size() > 0) {
				// If the VM is connected to the other VMs, try to put this VM into one of the hosts
				targetHosts = HostSelectionPolicyMostFull.getMostFullHostsForVm(vmToMigrate, underHosts, this.vmAllocationPolicy);
				migratedHost = moveVmToHost(vmToMigrate, targetHosts);
			}
			
			// 2. Find Most Full.
			if(migratedHost == null) {
				// If VM is not connected to any other VMs: most full
				targetHosts = HostSelectionPolicyMostFull.getMostFullHostsForVm(vmToMigrate, hosts, this.vmAllocationPolicy);
				migratedHost = moveVmToHost(vmToMigrate, targetHosts);
			}
			
			// 3. No host can serve this VM, do not migrate.
			if(migratedHost == null) {
				System.err.println("VmAllocationPolicy: WARNING:: Cannot migrate VM!!!!"+vmToMigrate); 
				System.exit(0);
			}
			
			vmToHost.put(vmToMigrate, migratedHost);

		}
		return vmToHost;
	}
}
