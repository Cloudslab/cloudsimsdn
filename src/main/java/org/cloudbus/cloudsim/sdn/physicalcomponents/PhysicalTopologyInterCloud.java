/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.physicalcomponents;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;


/**
 * Network connection maps including switches, hosts, and links between them
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class PhysicalTopologyInterCloud extends PhysicalTopologyFatTree {

	@Override
	public void buildDefaultRouting() {
		buildDefaultRoutingFatTree();
		buildDefaultRoutingGateway();
		buildDefaultRoutingInterCloud();
		printTopology();
	}
	
	protected void buildDefaultRoutingGateway() {		
		Collection<Node> nodes = getAllNodes();

		// Core -> Gateway (default route)
		// Gateway -> Core (for known hosts)
		for(Node core:nodes) {
			if(core.getRank() == RANK_CORE) {
				Collection<Link> links = getAdjacentLinks(core);
				for(Link l:links) {
					if(l.getLowOrder().equals(core)) {
						core.addRoute(null, l); // Core -> Gateway : default route
						
						// Add all children hosts to GW
						Node gateway = l.getHighOrder();
						addMultipleRoute(gateway, core.getRoutingTable().getKnownDestination(), l); // GW -> Core : for KNOWN HOSTS
					}
				}
			}
		}
	}
	
	protected void buildDefaultRoutingInterCloud() {
		// Gateway -> Gateway
		HashMap<Node, Collection<Node>> gateways = new HashMap<Node, Collection<Node>>();
		for(Node gateway:getAllNodes()) {
			if(gateway.getRank() == RANK_GATEWAY) {
				gateways.put(gateway, new LinkedList<Node>(gateway.getRoutingTable().getKnownDestination()));
			}
		}
		
		for(Node gateway:gateways.keySet()) {
			Collection<Node> knownHosts = gateways.get(gateway);
			
			HashSet<Node> visitedNode = new HashSet<Node>();
			LinkedList<Node> queue = new LinkedList<Node>();
			
			queue.add(gateway);
			while(!queue.isEmpty()) {
				Node cur = queue.remove();
				for(Link l:getAdjacentLinks(cur)) {
					Node otherNode = l.getOtherNode(cur);
					if(visitedNode.contains(otherNode))
						continue;
					
					if(otherNode.getRank() == RANK_GATEWAY || otherNode.getRank() == RANK_INTERCLOUD) {
						addMultipleRoute(otherNode, knownHosts, l);
						queue.add(otherNode);
					}
				}
				visitedNode.add(cur);
			}
		}
	}
	
	protected void addMultipleRoute(Node from, Collection<Node> destinations, Link through) {
		for(Node destination: destinations) {
			if(destination != null)
				from.addRoute(destination, through);
		}
	}
	

}
