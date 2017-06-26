/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import org.cloudbus.cloudsim.Vm;

public interface VmAllocationInGroup {
	public abstract boolean allocateHostForVmInGroup(Vm vm, VmGroup vmGroup);
}
