/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicy;
import org.cloudbus.cloudsim.sdn.vmallocation.VmMigrationPolicy;

public class OverbookingVmAllocationPolicyStaticRatio extends OverbookingVmAllocationPolicy {
	public OverbookingVmAllocationPolicyStaticRatio(List<? extends Host> list,
			HostSelectionPolicy hostSelectionPolicy,
			VmMigrationPolicy vmMigrationPolicy) {
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
			return Configuration.OVERBOOKING_RATIO_MAX;
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
			return Configuration.OVERBOOKING_RATIO_MAX;
		}
	}
}
