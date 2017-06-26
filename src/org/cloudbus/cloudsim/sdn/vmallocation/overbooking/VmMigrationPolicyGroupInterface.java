/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.VmGroup;

public interface VmMigrationPolicyGroupInterface {
	public void addVmInVmGroup(Vm vm, VmGroup vmGroup);
}
