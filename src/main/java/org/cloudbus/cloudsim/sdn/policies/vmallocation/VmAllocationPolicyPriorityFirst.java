/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.PhysicalTopology;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.physicalcomponents.PhysicalTopology.NodeType;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.EdgeSwitch;
import org.cloudbus.cloudsim.sdn.policies.selecthost.HostSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.overbooking.VmMigrationPolicyGroupInterface;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

// 1. Check the priority of the VM group
// 2. Put the highest priority first

public class VmAllocationPolicyPriorityFirst extends VmAllocationPolicyGroupConnectedFirst {
	PhysicalTopology topology;

	public VmAllocationPolicyPriorityFirst(List<? extends Host> list,
			HostSelectionPolicy hostSelectionPolicy,
			VmMigrationPolicy vmMigrationPolicy) 
	{
		super(list, hostSelectionPolicy, vmMigrationPolicy);
	}
	
	public void setTopology(PhysicalTopology top) {
		this.topology = top;
	}

	/**
	 * Allocates a host for a given VM Group.
	 * 
	 * @param vm VM specification
	 * @return $true if the host could be allocated; $false otherwise
	 * @pre $none
	 * @post $none
	 */
	@Override
	public boolean allocateHostForVmInGroup(Vm vm, VmGroup vmGrp) {
		VmGroupPriority vmGroup = (VmGroupPriority)vmGrp;
		if(vmMigrationPolicy instanceof VmMigrationPolicyGroupInterface) {
			((VmMigrationPolicyGroupInterface)vmMigrationPolicy).addVmInVmGroup(vm, vmGroup);
		}

		List<SDNHost> connectedHosts = getHostListVmGroup(vmGroup);
		
		// Other VMs in the group has been already allocated
		if(connectedHosts.size() != 0) {
			// Try to put this VM into one of the correlated hosts			
			if(allocateHostForVm(vm, hostSelectionPolicy.selectHostForVm((SDNVm)vm, connectedHosts)) == true) {
				return true;
			}
		}
		
		// For Priority VMs, find the most available host group (determined by edge connection)
		if(VmGroupPriority.isPriorityVmGroup(vmGroup)) {
			
			// If other VMs in the group has been already allocated, find the group 
			if(connectedHosts.size() != 0) {
				Collection<SDNHost> hostCandidates = new LinkedHashSet<SDNHost>();
				
				for(SDNHost h:connectedHosts) {
					HostGroup hg = this.getAdjacentHostGroupSameEdge(h);
					hostCandidates.addAll(hg.hosts);
				}
			
				// Try to put this VM into the edge switch as the other VMs
				if(allocateHostForVm(vm, hostSelectionPolicy.selectHostForVm((SDNVm)vm, new ArrayList<SDNHost>(hostCandidates))) == true) {
					return true;
				}
				
				// Find the same pod
				hostCandidates = new LinkedHashSet<SDNHost>();
				List<HostGroup> hGroups = this.getAdjacentHostGroupSamePod(connectedHosts.get(0));
				Collections.sort(hGroups);
				
				for(HostGroup hg: hGroups) {
					hostCandidates.addAll(hg.hosts);
				}
				// Try to put this VM into the same pod as the other VMs
				if(allocateHostForVm(vm, hostSelectionPolicy.selectHostForVm((SDNVm)vm, new ArrayList<SDNHost>(hostCandidates))) == true) {
					return true;
				}				
			}
			
			// Find the most available pod. 
			List<HostGroup> groups = new LinkedList<HostGroup>(getHostGroupMap().values());
			Collections.sort(groups);

			// Try to put this VM into the most available pod
			if(allocateHostForVm(vm, hostSelectionPolicy.selectHostForVm((SDNVm)vm, groups.get(0).hosts)) == true) {
				return true;
			}
		}
		return allocateHostForVm(vm);	// Use the Most Full First
	}
	
	@SuppressWarnings("unchecked")
	private Map<Node,HostGroup> getHostGroupMap() {
		Collection<Node> edges = topology.getNodesType(NodeType.Edge);
		HashMap<Node,HostGroup> groups = new HashMap<Node,HostGroup>();
		for(Node e: edges) {
			HostGroup hg = new HostGroup();
			hg.edge = (EdgeSwitch) e;
			hg.hosts  = new ArrayList<SDNHost>((Collection<? extends SDNHost>)(Collection<? extends Node>)topology.getConnectedNodesLow(e));
			for(SDNHost h:hg.hosts) {
				hg.numHosts++;
				hg.availableMips += h.getAvailableMips();
				hg.availableBw += h.getAvailableBandwidth();
			}
			
			groups.put(hg.edge, hg);
		}
		
		return groups;
	}
	
	private Node findEdgeSwitch(SDNHost host) {
		return (EdgeSwitch) topology.getConnectedNodesHigh(host).iterator().next();
	}
	
	protected HostGroup getAdjacentHostGroupSameEdge(SDNHost host) {
		// Search the list of adjacent host group.
		Node edge = findEdgeSwitch(host);
		Map<Node,HostGroup> groupMap = getHostGroupMap();

		return groupMap.get(edge);
		
	}
	
	protected List<HostGroup> getAdjacentHostGroupSamePod(SDNHost host) {
		Node edge = findEdgeSwitch(host);
		
		List<HostGroup> groups = new ArrayList<HostGroup>();
		
		Collection<Node> aggrs = topology.getConnectedNodesHigh(edge);
		Collection<Node> allEdges = new HashSet<Node>();
		for(Node agg:aggrs)
		{
			allEdges.addAll(topology.getConnectedNodesLow(agg));
		}
		allEdges.remove(edge);
		
		Map<Node,HostGroup> groupMap = getHostGroupMap();

		for(Node e:allEdges) {
			groups.add(groupMap.get(e));
		}
		return groups;
		
	}
	
	protected class HostGroup implements Comparable<HostGroup> {
		EdgeSwitch edge=null;
		List<SDNHost> hosts=null;
		int numHosts=0;
		double availableMips=0;
		double availableBw=0;
		
		@Override
		public int compareTo(HostGroup o) {
			return (int) (o.availableMips-this.availableMips);
		}
		
		public boolean contains(SDNHost o) {
			return this.hosts.contains(o);
		}
		
	}

	private List<SDNHost> getHostListVmGroup(VmGroup vmGroup) {
		LinkedHashSet<SDNHost> hosts = new LinkedHashSet<SDNHost>();
		
		for(SDNVm vm:vmGroup.<SDNVm>getVms()) {
			SDNHost h = (SDNHost)this.getHost(vm);
			if(h != null)
				hosts.add(h);
		}
		
		return new ArrayList<SDNHost>(hosts);		
	}		
}

