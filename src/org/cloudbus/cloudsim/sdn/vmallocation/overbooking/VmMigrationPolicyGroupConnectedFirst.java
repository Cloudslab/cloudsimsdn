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

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicyMostFull;
import org.cloudbus.cloudsim.sdn.vmallocation.VmMigrationPolicy;

public class VmMigrationPolicyGroupConnectedFirst extends VmMigrationPolicy implements VmMigrationPolicyGroupInterface {
	/** VmGroups to decide whether consolidate or not */
	protected Map<Vm, VmGroup> vmGroups = new HashMap<Vm, VmGroup>();

	@Override
	protected Map<Vm, Host> buildMigrationMap(List<SDNHost> hosts) {
		return buildMigrationMapOverloadedHost(hosts);		
	}
	
	protected Map<Vm, Host> buildMigrationMapOverloadedHost(List<SDNHost> hosts) {
		Map<Vm, Host> vmToHost = new HashMap<Vm, Host>();
		// Check peak VMs and reallocate them into different host
		List<SDNVm> migrationOverVMList = getMostUtilizedVms(hosts);
		
		for(SDNVm vmToMigrate:migrationOverVMList) {
			List<Host> targetHosts = null;

			// 1. Find correlated host where connected VMs are running
			VmGroup vmGroup = vmGroups.get(vmToMigrate);
			List<SDNHost> connectedHosts = getHostListVmGroup(vmGroup);
			Host migratedHost = null;

			if(connectedHosts.size() > 0) {
				// If the VM is connected to the other VMs, try to put this VM into one of the hosts
				targetHosts = HostSelectionPolicyMostFull.getMostFullHostsForVm(vmToMigrate, connectedHosts, vmAllocationPolicy);
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

	@Override
	public void addVmInVmGroup(Vm vm, VmGroup vmGroup) {
		vmGroups.put(vm, vmGroup);
	}
	
	protected List<SDNHost> getHostListVmGroup(VmGroup vmGroup) {
		List<SDNHost> hosts = new ArrayList<SDNHost>();
		
		for(SDNVm vm:vmGroup.<SDNVm>getVms()) {
			SDNHost h = (SDNHost)vmAllocationPolicy.getHost(vm);
			if(h != null)
				hosts.add(h);
		}
		
		return hosts;		
	}

}
