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
import java.util.List;

import org.cloudbus.cloudsim.Vm;

public class VmGroup implements Comparable<VmGroup> {
	private List<Vm> vms;
	private double requiredBw;
	private double requiredMips;
	
	public VmGroup() {
		vms = new ArrayList<Vm>();
		requiredBw = 0;
		requiredMips = 0;
	}
	
	public void addVm(Vm vm) {
		if(findVm(vm.getId()) == -1) {
			vms.add(vm);
			requiredBw += vm.getBw();
			requiredMips += ((SDNVm) vm).getTotalMips();
		}
	}
	
	public double getRequiredBw() {
		return requiredBw;
	}
	
	public void addVms(Collection<Vm> vms) {
		for(Vm vm: vms) {
			this.addVm(vm);
		}
	}
	
	public int findVm(int vmIdToFind) {
		for(int i=0; i< vms.size(); i++) {
			if(vms.get(i).getId() == vmIdToFind)
				return i;
		}
		
		return -1;
	}

	@SuppressWarnings("unchecked")
	public <T extends Vm> List<T>  getVms() {
		return (List<T>)vms;
	}
	
	public int size() {
		return vms.size();
	}

	@Override
	public int compareTo(VmGroup o) {
		return (int) (this.requiredMips - o.requiredMips);
	}
	
	@Override
	public String toString() {
		String str = "VMGroup: Req BW="+getRequiredBw() + " / MIP="+this.requiredMips+"\n";
		for(Vm v:vms)
			str+= v.toString() + "\n";
		return str;
	}

	public static void classifyGroupByArcList(List<Arc> sortedArcPool, List<Vm> vmPool, List<VmGroup> groups) {
		// Put VMs connected in large bandwidth requirement into the same group
		while(!sortedArcPool.isEmpty()) {
			Arc a = sortedArcPool.remove(0);
			VmGroup vmGroup = null;
			Vm srcVm = null;
			int srcIndex = findVm(vmPool, a.getSrcId());
			if(srcIndex != -1)
				srcVm = vmPool.remove(srcIndex);
			
			if(srcVm != null) {
				// src vm is not grouped yet.
				vmGroup = findVmGroup(groups, a.getDstId());
				if(vmGroup == null) {
					// dst vm is not grouped yet, put both vms into new group
					vmGroup = new VmGroup();
					groups.add(vmGroup);
					vmGroup.addVm(srcVm);
					
					int dstIndex = findVm(vmPool, a.getDstId());
					Vm dstVm = vmPool.get(dstIndex);
					vmGroup.addVm(dstVm);
				}
				else {
					// dst vm is in a group. put into the same group.
					vmGroup.addVm(srcVm);
				}
			}
			else {
				Vm dstVm = null;
				// src vm is already grouped. check dst.
				int dstIndex = findVm(vmPool, a.getDstId());
				if(dstIndex != -1)
					dstVm = vmPool.remove(dstIndex);
				
				if(dstVm != null) {
					// dst vm is not grouped yet. put dst into the same group
					vmGroup = findVmGroup(groups, a.getSrcId());
					vmGroup.addVm(dstVm);
				}
				else {
					// dst vm is also already grouped. both are in group.
					
				}
				
				vmGroup = findVmGroup(groups, a.getSrcId());
				
			}
		}
	}

	protected static int findVm(List<Vm> vms, int idToFind) {
		for(int i=0; i<vms.size(); i++) {
			Vm vm = vms.get(i);
			if(vm.getId() == idToFind)
				return i;
		}
		
		return -1;
	}

	protected static VmGroup findVmGroup(List<VmGroup> vmGroups, int vmIdToFind) {
		for(VmGroup vmGr:vmGroups) {
			if(vmGr.findVm(vmIdToFind) != -1)
				return vmGr;
		}
		return null;
	}

	public static void putEachVmIntoEachGroup(List<Vm> vmPool, List<VmGroup> groups) {
		for(Vm vm:vmPool) {
			VmGroup group = new VmGroup();
			group.addVm(vm);
			groups.add(group);
		}
	}
}
