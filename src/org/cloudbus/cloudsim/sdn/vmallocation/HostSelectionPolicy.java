/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;

public abstract class HostSelectionPolicy {
	protected VmAllocationPolicyEx vmAllocPolicy=null;
	
	public void setVmAllocationPolicy(VmAllocationPolicyEx vmAllocationPolicyEx) {
		vmAllocPolicy=vmAllocationPolicyEx;
	}

	public abstract List<Host> selectHostForVm(SDNVm vm, List<SDNHost> hosts);
}
