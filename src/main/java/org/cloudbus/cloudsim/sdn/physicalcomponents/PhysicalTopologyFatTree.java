/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.physicalcomponents;

import java.util.Collection;


/**
 * Network connection maps including switches, hosts, and links between them
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class PhysicalTopologyFatTree extends PhysicalTopology {

	@Override
	public void buildDefaultRouting() {
		buildDefaultRoutingFatTree();
		printTopology();
	}
	
	protected void buildDefaultRoutingFatTree() {
		Collection<Node> nodes = getAllNodes();
		
		/********************************************
		 * FatTree:: Building routing table for downlinks 
		 ********************************************/
		// For SDNHost: build path to edge switch
		// For Edge: build path to SDN Host
		// For Agg: build path to SDN Host through edge
		for(Node sdnhost:nodes) {
			if(sdnhost.getRank() == RANK_HOST) {	// Rank3 = SDN Host
				Collection<Link> links = getAdjacentLinks(sdnhost);
				for(Link l:links) {
					if(l.getLowOrder().equals(sdnhost)) {
						sdnhost.addRoute(null, l); // Host -> Edge : default route
						Node edge = l.getHighOrder();
						edge.addRoute(sdnhost, l); // Edge -> Host : for specific destination host
						
						Collection<Link> links2 = getAdjacentLinks(edge);
						for(Link l2:links2) {
							if(l2.getLowOrder().equals(edge)) {
								Node agg = l2.getHighOrder();
								agg.addRoute(sdnhost, l2); // Aggr -> Edge : for specific destination host
							}
						}
						
					}
				}
			}
		}
		// For Core: build path to SDN Host through agg
		for(Node agg:nodes) {
			if(agg.getRank() == RANK_AGGR) {	// Rank1 = Agg switch
				Collection<Link> links = getAdjacentLinks(agg);
				for(Link l:links) {
					if(l.getLowOrder().equals(agg)) {
						Node core = l.getHighOrder();
						
						// Add all children hosts to
						for(Node destination: agg.getRoutingTable().getKnownDestination()) {
							if(destination != null)
								core.addRoute(destination, l); // Core -> Aggr : for KNOWN HOSTS
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
			if(edge.getRank() == RANK_EDGE) {	// Rank2 = Edge switch
				Collection<Link> links = getAdjacentLinks(edge);
				for(Link l:links) {
					if(l.getLowOrder().equals(edge)) {
						// Link is between Edge and Aggregate
						edge.addRoute(null, l); // Edge -> Aggr : for default route
					}
				}
			}
		}
		// For Agg: build path to core switch
		for(Node agg:nodes) {
			if(agg.getRank() == RANK_AGGR) {	// Rank1 = Agg switch
				Collection<Link> links = getAdjacentLinks(agg);
				for(Link l:links) {
					if(l.getLowOrder().equals(agg)) {
						// Link is between Core and Aggregate
						agg.addRoute(null, l); // Agg -> Core : for default route
					}
				}
			}
		}
	}
}
