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
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;

public abstract class HostSelectionPolicy {
	protected OverbookingVmAllocationPolicy vmAllocPolicy=null;
	
	public void setVmAllocationPolicy(OverbookingVmAllocationPolicy vmAllocation) {
		vmAllocPolicy=vmAllocation;
	}

	public abstract List<Host> selectHostForVm(SDNVm vm, List<SDNHost> hosts);
}
