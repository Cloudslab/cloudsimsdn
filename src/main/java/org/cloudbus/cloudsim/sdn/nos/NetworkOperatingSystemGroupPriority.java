/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.nos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmGroup;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmGroupPriority;
import org.cloudbus.cloudsim.sdn.virtualcomponents.FlowConfig;

public class NetworkOperatingSystemGroupPriority extends NetworkOperatingSystemGroupAware {

	public NetworkOperatingSystemGroupPriority() {
		super();
		// TODO Auto-generated constructor stub
	}

	@Override
	protected List<VmGroup> createVmGroup(Collection<Vm> vms, Collection<FlowConfig> flows) {
		// Put VMs into separate group according to their connections 
		List<Vm> vmPool = new ArrayList<Vm>(vms);	// all VMs
		List<FlowConfig> arcPool = new ArrayList<FlowConfig>(flows);	// all virtual Links
		
		// Sort links by their bandwidth 
		Collections.sort(arcPool, new Comparator<FlowConfig>() {
		    public int compare(FlowConfig o1, FlowConfig o2) {
		        return (int) (o1.getBw() - o2.getBw());
		    }
		});
		
		// Separate groups by link bandwidth order
		List<VmGroup> groups = VmGroupPriority.classifyGroupByArcList(arcPool, vmPool);
		
		// Put all other VMs (a single VM without any network connection) into each group
		VmGroupPriority.putEachVmIntoEachGroup(vmPool, groups);
		
		// Set priority of the VM groups.
		for(VmGroup g:groups) {
			((VmGroupPriority)g).setPriority((long)g.getRequiredBw());
		}
		
		// Sort VmGroup by their mips request
		Collections.sort(groups);
		
		/* DEBUG!!
		for(VmGroup g:groups) {
			System.out.println("DEBUG!!"+g);
		}
		*/
		
		return groups;
	}
}
