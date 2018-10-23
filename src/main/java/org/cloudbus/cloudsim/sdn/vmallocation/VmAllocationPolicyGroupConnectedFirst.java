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
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmAllocationInGroup;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VmMigrationPolicyGroupInterface;

public class VmAllocationPolicyGroupConnectedFirst extends VmAllocationPolicyEx implements VmAllocationInGroup {
	public VmAllocationPolicyGroupConnectedFirst(
			List<? extends Host> list,
			HostSelectionPolicy hostSelectionPolicy,
			VmMigrationPolicy vmMigrationPolicy) {
		super(list, hostSelectionPolicy, vmMigrationPolicy);
	}

	/**
	 * Allocates a host for a given VM Group.
	 * 
	 * @param vm VM specification
	 * @return $true if the host could be allocated; $false otherwise
	 * @pre $none
	 * @post $none
	 */
	@Override
	public boolean allocateHostForVmInGroup(Vm vm, VmGroup vmGroup) {
		if(vmMigrationPolicy instanceof VmMigrationPolicyGroupInterface) {
			((VmMigrationPolicyGroupInterface)vmMigrationPolicy).addVmInVmGroup(vm, vmGroup);
		}

		List<SDNHost> connectedHosts = getHostListVmGroup(vmGroup);

		if(connectedHosts.size() == 0) {
			// This VM is the first VM to be allocated
			return allocateHostForVm(vm);	// Use the Most Full First
		}
		else {
			// Other VMs in the group has been already allocated
			// Try to put this VM into one of the correlated hosts			
			if(allocateHostForVm(vm, hostSelectionPolicy.selectHostForVm((SDNVm)vm, connectedHosts)) == true) {
				return true;
			}
			else {
				// Cannot create VM to correlated hosts. Use the Most Full First
				return allocateHostForVm(vm);
			}
		}
	}

	private List<SDNHost> getHostListVmGroup(VmGroup vmGroup) {
		List<SDNHost> hosts = new ArrayList<SDNHost>();
		
		for(SDNVm vm:vmGroup.<SDNVm>getVms()) {
			SDNHost h = (SDNHost)this.getHost(vm);
			if(h != null)
				hosts.add(h);
		}
		
		return hosts;		
	}	
}
