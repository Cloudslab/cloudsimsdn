/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.Arc;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.cloudbus.cloudsim.sdn.VmGroupPriority;

public class NetworkOperatingSystemOverbookableGroupPriority extends NetworkOperatingSystemOverbookableGroup {

	public NetworkOperatingSystemOverbookableGroupPriority(String fileName) {
		super(fileName);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected List<VmGroup> createVmGroup(Collection<Vm> vms, Collection<Arc> flows) {
		// Put VMs into separate group according to their connections 
		List<Vm> vmPool = new ArrayList<Vm>(vms);	// all VMs
		List<Arc> arcPool = new ArrayList<Arc>(flows);	// all virtual Links
		
		// Sort links by their bandwidth 
		Collections.sort(arcPool, new Comparator<Arc>() {
		    public int compare(Arc o1, Arc o2) {
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
