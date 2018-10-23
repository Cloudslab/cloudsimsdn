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

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicy;
import org.cloudbus.cloudsim.sdn.vmallocation.VmMigrationPolicy;

public class OverBookingVmAllocationPolicyDistributeConnected extends OverbookingVmAllocationPolicyConsolidateConnected {
	
	public OverBookingVmAllocationPolicyDistributeConnected(
			List<? extends Host> list,
			HostSelectionPolicy hostSelectionPolicy,
			VmMigrationPolicy vmMigrationPolicy) {
		super(list, hostSelectionPolicy, vmMigrationPolicy);
	}
	
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
			// Avoid the correlated hosts.
			List<SDNHost> allHosts = new ArrayList<SDNHost>(this.<SDNHost>getHostList());
			allHosts.removeAll(connectedHosts);
			
			if(allocateHostForVm(vm, hostSelectionPolicy.selectHostForVm((SDNVm)vm, allHosts)) == true) {
				return true;
			}
			else {
				// Cannot create VM to correlated hosts. Use the Most Full First
				return allocateHostForVm(vm);
			}
		}
	}
}
