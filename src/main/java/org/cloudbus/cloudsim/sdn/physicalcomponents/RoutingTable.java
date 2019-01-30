/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.physicalcomponents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Routing table for hosts and switches in physical level. This class has information about the next hop.
 * When a physical topology is set up, a RoutingTable is created with the information of the next hop for each node.
 * RoutingTable contains physical topology's routing information, whereas ForwardingRule contains 
 * VM-to-VM network (virtual topology) routing information.
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class RoutingTable {
	
	Map<Node, List<Link>> table;

	public RoutingTable(){
		this.table = new HashMap<Node, List<Link>>();
	}
	
	public void clear(){
		table.clear();
	}
	
	public void addRoute(Node destHost, Link to){
		// Default route with destHost == null
		List<Link> links = table.get(destHost);
		if(links == null)
		{
			links = new ArrayList<Link>();
		}
		links.add(to);
		table.put(destHost, links);
	}
	
	public void removeRoute(Node destHost){
		table.remove(destHost);
	}

	public List<Link> getRoute(Node destHost) {
		List<Link> links = table.get(destHost);
		if(links == null)
			links = table.get(null); // default route
		return links;
	}
	
	public Set<Node> getKnownDestination() {
		return table.keySet();
	}
	
	public void printRoutingTable() {
		for(Node key:table.keySet()) {
			for(Link l: table.get(key)) {
				System.out.println("dst:"+key+" : "+l);
			}
		}
	}
	
	public String toString() {
		return table.toString();
	}
}
