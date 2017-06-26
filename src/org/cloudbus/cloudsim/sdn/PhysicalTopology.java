/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;


/**
 * Network connection maps including switches, hosts, and links between them
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class PhysicalTopology {
	
	Hashtable<Integer,Node> nodesTable;	// Address -> Node
	Table<Integer, Integer, Link> linkTable; 	// From : To -> Link
	Multimap<Node,Link> nodeLinks;	// Node -> all Links

	public PhysicalTopology() {
		nodesTable = new Hashtable<Integer,Node>();
		nodeLinks = HashMultimap.create();
		linkTable = HashBasedTable.create();
	}
	
	public Link getLink(int from, int to) {
		return linkTable.get(from, to);
	}
	public Node getNode(int id) {
		return nodesTable.get(id);
	}
	public double getLinkBandwidth(int from, int to){
		return getLink(from, to).getBw(getNode(from));
	}
	
	public double getLinkLatencyInSeconds(int from, int to){
		return getLink(from, to).getLatencyInSeconds();
	}
	
	public void addNode(Node node){
		nodesTable.put(node.getAddress(), node);
		if (node instanceof CoreSwitch){//coreSwitch is rank 0 (root)
			node.setRank(0);
		} else if (node instanceof AggregationSwitch){//Hosts are on the bottom of hierarchy (leaf)
			node.setRank(1);
		} else if (node instanceof EdgeSwitch){//Edge switches are just before hosts in the hierarchy
			node.setRank(2);
		} else if (node instanceof SDNHost){//Hosts are on the bottom of hierarchy (leaf)
			node.setRank(3);
		} else {
			throw new IllegalArgumentException();
		}
		
		addLoopbackLink(node);
	}
	
	public void buildDefaultRouting() {
		Collection<Node> nodes = getAllNodes();
		
		// For SDNHost: build path to edge switch
		// For Edge: build path to SDN Host
		for(Node sdnhost:nodes) {
			if(sdnhost.getRank() == 3) {	// Rank3 = SDN Host
				Collection<Link> links = getAdjacentLinks(sdnhost);
				for(Link l:links) {
					if(l.getLowOrder().equals(sdnhost)) {
						sdnhost.addRoute(null, l);
						Node edge = l.getHighOrder();
						edge.addRoute(sdnhost, l);
					}
				}
			}
		}
		// For Edge: build path to aggregate switch
		// For Aggregate: build path to edge switch
		for(Node agg:nodes) {
			if(agg.getRank() == 2) {	// Rank2 = Edge switch
				Collection<Link> links = getAdjacentLinks(agg);
				for(Link l:links) {
					if(l.getLowOrder().equals(agg)) {
						// Link is between Edge and Aggregate
						agg.addRoute(null, l);
						Node core = l.getHighOrder();
						
						// Add all children hosts to
						for(Node destination: agg.getRoutingTable().getKnownDestination()) {
							if(destination != null)
								core.addRoute(destination, l);
						}
					}
				}
			}
		}
		// For Agg: build path to core switch
		// For Core: build path to aggregate switch
		for(Node agg:nodes) {
			if(agg.getRank() == 1) {	// Rank1 = Agg switch
				Collection<Link> links = getAdjacentLinks(agg);
				for(Link l:links) {
					if(l.getLowOrder().equals(agg)) {
						// Link is between Edge and Aggregate
						agg.addRoute(null, l);
						Node core = l.getHighOrder();
						
						// Add all children hosts to
						for(Node destination: agg.getRoutingTable().getKnownDestination()) {
							if(destination != null)
								core.addRoute(destination, l);
						}
					}
				}
			}
		}
		
		for(Node n:nodes) {
			System.out.println("============================================");
			System.out.println("Node: "+n);
			n.getRoutingTable().printRoutingTable();
		}

	}
	
	public void buildDefaultRoutingFatTree() {
		Collection<Node> nodes = getAllNodes();
		
		/********************************************
		 * FatTree:: Building routing table for downlinks 
		 ********************************************/
		// For SDNHost: build path to edge switch
		// For Edge: build path to SDN Host
		// For Agg: build path to SDN Host through edge
		for(Node sdnhost:nodes) {
			if(sdnhost.getRank() == 3) {	// Rank3 = SDN Host
				Collection<Link> links = getAdjacentLinks(sdnhost);
				for(Link l:links) {
					if(l.getLowOrder().equals(sdnhost)) {
						sdnhost.addRoute(null, l);
						Node edge = l.getHighOrder();
						edge.addRoute(sdnhost, l);
						
						Collection<Link> links2 = getAdjacentLinks(edge);
						for(Link l2:links2) {
							if(l2.getLowOrder().equals(edge)) {
								Node agg = l2.getHighOrder();
								agg.addRoute(sdnhost, l2);
							}
						}
						
					}
				}
			}
		}
		// For Core: build path to SDN Host through agg
		for(Node agg:nodes) {
			if(agg.getRank() == 1) {	// Rank1 = Agg switch
				Collection<Link> links = getAdjacentLinks(agg);
				for(Link l:links) {
					if(l.getLowOrder().equals(agg)) {
						Node core = l.getHighOrder();
						
						// Add all children hosts to
						for(Node destination: agg.getRoutingTable().getKnownDestination()) {
							if(destination != null)
								core.addRoute(destination, l);
						}
					}
				}
			}
		}
		
		/********************************************
		 * FatTree:: Building routing table for uplinks 
		 ********************************************/
		// For Edge: build path to aggregate switch
		for(Node edge:nodes) {
			if(edge.getRank() == 2) {	// Rank2 = Edge switch
				Collection<Link> links = getAdjacentLinks(edge);
				for(Link l:links) {
					if(l.getLowOrder().equals(edge)) {
						// Link is between Edge and Aggregate
						edge.addRoute(null, l);
					}
				}
			}
		}
		// For Agg: build path to core switch
		for(Node agg:nodes) {
			if(agg.getRank() == 1) {	// Rank1 = Agg switch
				Collection<Link> links = getAdjacentLinks(agg);
				for(Link l:links) {
					if(l.getLowOrder().equals(agg)) {
						// Link is between Edge and Aggregate
						agg.addRoute(null, l);
					}
				}
			}
		}
		
		for(Node n:nodes) {
			System.out.println("============================================");
			System.out.println("Node: "+n);
			n.getRoutingTable().printRoutingTable();
		}

	}
	public void addLink(int from, int to, double latency){
		Node fromNode = nodesTable.get(from);
		Node toNode = nodesTable.get(to);
		
		addLink(fromNode, toNode, latency);
	}
	
	public void addLink(Node fromNode, Node toNode, double latency){
		int from = fromNode.getAddress();
		int to = toNode.getAddress();
		
		long bw = (fromNode.getBandwidth()<toNode.getBandwidth())? fromNode.getBandwidth():toNode.getBandwidth();
		
		if(!nodesTable.containsKey(from)||!nodesTable.containsKey(to)){
			throw new IllegalArgumentException("Unknown node on link:"+nodesTable.get(from).getAddress()+"->"+nodesTable.get(to).getAddress());
		}
		
		if (linkTable.contains(fromNode.getAddress(), toNode.getAddress())){
			throw new IllegalArgumentException("Link added twice:"+fromNode.getAddress()+"->"+toNode.getAddress());
		}
		
		if(fromNode.getRank()==-1&&toNode.getRank()==-1){
			throw new IllegalArgumentException("Unable to establish orders for nodes on link:"+nodesTable.get(from).getAddress()+"->"+nodesTable.get(to).getAddress());
		}
		
		if (fromNode.getRank()>=0 && toNode.getRank()>=0){
			//we know the rank of both nodes; easy to establish topology
			if ((toNode.getRank()-fromNode.getRank())!=1) {
				//throw new IllegalArgumentException("Nodes need to be parent and child:"+nodesTable.get(from).getAddress()+"->"+nodesTable.get(to).getAddress());
			}
		}
		
		if(fromNode.getRank()>=0&&toNode.getRank()==-1){
			//now we know B is children of A
			toNode.setRank(fromNode.getRank()+1);
		}
		
		if(fromNode.getRank()==-1&&toNode.getRank()>=1){
			//now we know A is parent of B
			fromNode.setRank(toNode.getRank()-1);
		}
		Link l = new Link(fromNode, toNode, latency, bw);
		
		// Two way links (From -> to, To -> from)
		linkTable.put(from, to, l);
		linkTable.put(to, from, l);
		
		nodeLinks.put(fromNode, l);
		nodeLinks.put(toNode, l);
		
		fromNode.addLink(l);
		toNode.addLink(l);
	}
	
	private void addLoopbackLink(Node node) {
		int nodeId = node.getAddress();
		long bw = NetworkOperatingSystem.bandwidthWithinSameHost;
		double latency = NetworkOperatingSystem.latencyWithinSameHost;
		
		Link l = new Link(node, node, latency, bw);
		
		// Two way links (From -> to, To -> from)
		linkTable.put(nodeId, nodeId, l);
	}
	
	public Collection<Link> getAdjacentLinks(Node node) {
		return nodeLinks.get(node);
	}
	
	public Collection<Node> getAllNodes() {
		return nodesTable.values();
	}
	
	public Collection<Link> getAllLinks() {
		HashSet<Link> allLinks = new HashSet<Link>();
		allLinks.addAll(nodeLinks.values());
		return allLinks;
	}

}
