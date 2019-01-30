package org.cloudbus.cloudsim.sdn.virtualcomponents;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;

public class VirtualNetworkMapper {
	
	protected NetworkOperatingSystem nos;
	protected LinkSelectionPolicy linkSelector;
	
	public VirtualNetworkMapper(NetworkOperatingSystem nos) {
		this.nos = nos;
	}
	
	public void setLinkSelectionPolicy(LinkSelectionPolicy linkSelectionPolicy) {
		this.linkSelector = linkSelectionPolicy;
	}

	public boolean buildForwardingTable(int srcVm, int dstVm, int flowId) {
		SDNHost srchost = (SDNHost)nos.findHost(srcVm);
		SDNHost dsthost = (SDNHost)nos.findHost(dstVm);
		if(srchost == null || dsthost == null) {
			//System.err.println(CloudSim.clock() + ": " + getName() + ": Cannot find src VM ("+srcVm+":"+srchost+") or dst VM ("+dstVm+":"+dsthost+")");
			return false;
		}
		
		if(srchost.equals(dsthost)) {
			Log.printLine(CloudSim.clock() + ": Source SDN Host is same as Destination. Go loopback");
			srchost.addVMRoute(srcVm, dstVm, flowId, dsthost);
		}
		else {
			Log.printLine(CloudSim.clock() + ": VMs are in different hosts:"+ srchost+ "("+srcVm+")->"+dsthost+"("+dstVm+")");
			boolean findRoute = buildForwardingTableRec(srchost, srcVm, dstVm, flowId);
			
			if(!findRoute) {
				System.err.println("NetworkOperatingSystem.deployFlow: Could not find route!!" + 
						NetworkOperatingSystem.getVmName(srcVm) + "->"+NetworkOperatingSystem.getVmName(dstVm));
				return false;
			}
		}
		return true;
	}

	protected boolean buildForwardingTableRec(Node node, int srcVm, int dstVm, int flowId) {
		// There are multiple links. Determine which hop to go.
		SDNHost desthost = nos.findHost(dstVm);
		if(node.equals(desthost))
			return true;
		
		List<Link> nextLinkCandidates = node.getRoute(desthost);
		
		if(nextLinkCandidates == null) {
			throw new RuntimeException("Cannot find next links for the flow:"+srcVm+"->"+dstVm+"("+flowId+") for node="+node+", dest node="+desthost);
		}
		
		// Choose which link to follow
		Link nextLink = linkSelector.selectLink(nextLinkCandidates, flowId, nos.findHost(srcVm), desthost, node);
		Node nextHop = nextLink.getOtherNode(node);
		
		node.addVMRoute(srcVm, dstVm, flowId, nextHop);
		buildForwardingTableRec(nextHop, srcVm, dstVm, flowId);
		
		return true;
	}

	public boolean updateDynamicForwardingTableRec(Node node, int srcVm, int dstVm, int flowId, boolean isNewRoute) {
		if(!linkSelector.isDynamicRoutingEnabled())
			return false;
		
		// There are multiple links. Determine which hop to go.
		SDNHost desthost = nos.findHost(dstVm);
		if(node.equals(desthost))
			return false;	// Nothing changed
		
		List<Link> nextLinkCandidates = node.getRoute(desthost);
		
		if(nextLinkCandidates == null) {
			throw new RuntimeException("Cannot find next links for the flow:"+srcVm+"->"+dstVm+"("+flowId+") for node="+node+", dest node="+desthost);
		}
		
		// Choose which link to follow
		Link nextLink = linkSelector.selectLink(nextLinkCandidates, flowId, nos.findHost(srcVm), desthost, node);
		Node nextHop = nextLink.getOtherNode(node);
		
		Node oldNextHop = node.getVMRoute(srcVm, dstVm, flowId);
		if(isNewRoute || !nextHop.equals(oldNextHop)) {
			// Create a new route
			//node.removeVMRoute(srcVm, dstVm, flowId);
			node.addVMRoute(srcVm, dstVm, flowId, nextHop);
			//Log.printLine(CloudSim.clock() + ": " + getName() + ": Updating VM route for flow:"+srcVm+"->"+dstVm+"("+flowId+") From="+node+", Old="+oldNextHop+", New="+nextHop);
			
			updateDynamicForwardingTableRec(nextHop, srcVm, dstVm, flowId, true);
			return true;
		}
		else {
			// Nothing changed for this node.
			return updateDynamicForwardingTableRec(nextHop, srcVm, dstVm, flowId, false);
		}
	}
	
	
	/**
	 * Gets the list of nodes and links that a channel will pass through.
	 * 
	 * @param src source VM id
	 * @param dst destination VM id
	 * @param flowId flow id
	 * @param srcNode source node (host of src VM)
	 * @param nodes empty list to get return of the nodes on the route
	 * @param links empty list to get return of the links on the route
	 * @return none
	 * @pre $none
	 * @post $none
	 */
	public void buildNodesLinks(int src, int dst, int flowId, Node srcNode,
			List<Node> nodes, List<Link> links) {
		
		// Build the list of nodes and links that this channel passes through
		Node origin = srcNode;
		Node dest = origin.getVMRoute(src, dst, flowId);
		
		if(dest==null) {
			System.err.println("buildNodesLinks() Cannot find dest!");
			return;	
		}
	
		nodes.add(origin);
	
		while(dest != null) {
			Link link = origin.getLinkTo(dest);
			if(link == null)
				throw new IllegalArgumentException("Link is NULL for srcNode:"+origin+" -> dstNode:"+dest);
			
			links.add(link);
			nodes.add(dest);
			
			if(dest instanceof SDNHost)
				break;
			
			origin = dest;
			dest = origin.getVMRoute(src, dst, flowId);
		}
	}

	// This function rebuilds the forwarding table only for the specific VM
	public void rebuildForwardingTable(int srcVmId, int dstVmId, int flowId, Node srcHost) {
		// Remove the old routes.
		List<Node> oldNodes = new ArrayList<Node>();
		List<Link> oldLinks = new ArrayList<Link>();
		buildNodesLinks(srcVmId, dstVmId, flowId, srcHost, oldNodes, oldLinks);
		
		for(Node node:oldNodes) {
			//System.err.println("Removing routes for: "+node + "("+arc+")");
			node.removeVMRoute(srcVmId, dstVmId, flowId);
		}
		
		// Build a forwarding table for the new route.
		if(buildForwardingTable(srcVmId, dstVmId, flowId) == false) {
			System.err.println("NetworkOperatingSystem.processVmMigrate: cannot build a new forwarding table!!");
			System.exit(0);
		}
	}

}
