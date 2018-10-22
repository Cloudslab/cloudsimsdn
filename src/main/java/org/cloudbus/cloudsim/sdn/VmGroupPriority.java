/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cloudbus.cloudsim.Vm;

public class VmGroupPriority extends VmGroup {
	private long priority;
	private Set<Arc> flows;
	private double requiredFlowBw;
	
	private static double maxFlowBw = 0;
	public static boolean isPriorityVmGroup(VmGroupPriority vg) {
		if(vg.getRequiredBw() == maxFlowBw)
			return true;
		return false;
	}
	
	public VmGroupPriority() {
		super();
		priority = 0;
		requiredFlowBw = 0;
		flows = new HashSet<Arc>();
	}
	
	public void addFlow(Arc flow) {
		requiredFlowBw += flow.getBw();
		
		if(requiredFlowBw > maxFlowBw) {
			maxFlowBw = requiredFlowBw;
		}
	}
	
	public Collection<Arc> getFlows() {
		return flows;
	}
	
	@Override
	public double getRequiredBw() {
		return requiredFlowBw;
	}
	
	public int compareTo(VmGroup o) {
		return (int) (o.getRequiredBw() - getRequiredBw());
	}
	
	public static List<VmGroup> classifyGroupByArcList(List<Arc> sortedArcPool, List<Vm> vmPool) {
		List<VmGroup> groups = new ArrayList<VmGroup>();
		
		// Put VMs connected in large bandwidth requirement into the same group
		while(!sortedArcPool.isEmpty()) {
			Arc a = sortedArcPool.remove(0);
			VmGroupPriority vmGroup1 = null, vmGroup2 = null;
			Vm vm1=null, vm2 = null;
			
			int srcIndex = findVm(vmPool, a.getSrcId());
			if(srcIndex != -1)
				vm1 = vmPool.remove(srcIndex);
			int dstIndex = findVm(vmPool, a.getDstId());
			if(dstIndex != -1)
				vm2 = vmPool.remove(dstIndex);
			vmGroup1 = (VmGroupPriority)VmGroup.findVmGroup(groups, a.getSrcId());
			vmGroup2 = (VmGroupPriority)VmGroup.findVmGroup(groups, a.getDstId());
			
			if(vmGroup1 == null && vmGroup2 == null) {
				// both src and dst VMs are not grouped yet, create a new group and put both vms into new group
				assert(vm1 != null && vm2 != null);
				vmGroup1 = new VmGroupPriority();
				groups.add(vmGroup1);
				vmGroup1.addVm(vm1);
				vmGroup1.addVm(vm2);
				vmGroup1.addFlow(a);
			}
			else if(vmGroup1 != null && vmGroup2 !=null) {
				assert(vm1 == null && vm2 == null);
				if(vmGroup1 != vmGroup2) {
					// both VMs are already in group, but in different group. Merge them.
					mergeGroup(vmGroup1, vmGroup2, groups);
					vmGroup1.addFlow(a);
				}
				else {
					// both VMs are already in the same group
					vmGroup1.addFlow(a);
				}
			}
			else {
				// one of VMs is in a group. add the other one to the same group.
				if(vmGroup1 != null) {
					assert(vm1 == null && vm2 != null);
					vmGroup1.addVm(vm2);
					vmGroup1.addFlow(a);
				}
				else {
					assert(vm1 != null && vm2 == null);
					vmGroup2.addVm(vm1);
					vmGroup2.addFlow(a);
				}
			}
		}
		
		return groups;
	}	
	
	private static void mergeGroup(VmGroupPriority vmGroup, VmGroupPriority vmGroupToRemove,
			List<VmGroup> groups) {
		
		for(Vm vm:vmGroupToRemove.getVms()) {
			vmGroup.addVm(vm);
		}
		for(Arc flow:vmGroupToRemove.getFlows()) {
			vmGroup.addFlow(flow);
		}
		groups.remove(vmGroupToRemove);		
	}

	public static void putEachVmIntoEachGroup(List<Vm> vmPool, List<VmGroup> groups) {
		for(Vm vm:vmPool) {
			VmGroupPriority group = new VmGroupPriority();
			group.addVm(vm);
			groups.add(group);
		}
	}
		
	public void setPriority(long priority) {
		System.err.println("Priority is set to "+ priority);
		this.priority = priority; 
	}

	public long getPriority() {
		return this.priority;
	}
}
