/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.parsers;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.cloudbus.cloudsim.sdn.AggregationSwitch;
import org.cloudbus.cloudsim.sdn.CoreSwitch;
import org.cloudbus.cloudsim.sdn.EdgeSwitch;
import org.cloudbus.cloudsim.sdn.Link;
import org.cloudbus.cloudsim.sdn.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.Switch;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class PhysicalTopologyParser {
	private String filename;
	private NetworkOperatingSystem nos;

	private List<SDNHost> sdnHosts = new ArrayList<SDNHost>();
	private List<Switch> switches = new ArrayList<Switch>();
	private List<Link> links = new ArrayList<Link>();
	
	public PhysicalTopologyParser(String jsonFilename, NetworkOperatingSystem networkOS) {
		this.filename = jsonFilename;
		this.nos = networkOS;
		
		parse();
	}
	
	public List<SDNHost> getHosts() {
		return this.sdnHosts;
	}
	
	public List<Switch> getSwitches() {
		return this.switches;
	}
	
	public List<Link> getLinks() {
		return this.links;
	}
	
	private void parse() {
		
		//int hostId=0;
		
		Hashtable<String, Node> nameNodeTable = new Hashtable<String, Node>();
		
		try {
    		JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(this.filename));
    		
    		JSONArray nodes = (JSONArray) doc.get("nodes");
    		@SuppressWarnings("unchecked")
			Iterator<JSONObject> iter =nodes.iterator(); 
			while(iter.hasNext()){
				JSONObject node = iter.next();
				String nodeType = (String) node.get("type");
				String nodeName = (String) node.get("name");
				
				if(nodeType.equalsIgnoreCase("host")){
					long pes = (Long) node.get("pes");
					long mips = (Long) node.get("mips");
					int ram = new BigDecimal((Long)node.get("ram")).intValueExact();
					long storage = (Long) node.get("storage");
					long bw = new BigDecimal((Long)node.get("bw")).intValueExact();
					
					int num = 1;
					if (node.get("nums")!= null)
						num = new BigDecimal((Long)node.get("nums")).intValueExact();

					for(int n = 0; n< num; n++) {
						String nodeName2 = nodeName;
						if(num >1) nodeName2 = nodeName + n;
						
						SDNHost sdnHost = nos.createHost(ram, bw, storage, pes, mips);
						nameNodeTable.put(nodeName2, sdnHost);
						//hostId++;
						
						this.sdnHosts.add(sdnHost);
					}
					
				} else {
					int MAX_PORTS = 256;
							
					long bw = new BigDecimal((Long)node.get("bw")).longValueExact();
					long iops = (Long) node.get("iops");
					int upports = MAX_PORTS;
					int downports = MAX_PORTS;
					if (node.get("upports")!= null)
						upports = new BigDecimal((Long)node.get("upports")).intValueExact();
					if (node.get("downports")!= null)
						downports = new BigDecimal((Long)node.get("downports")).intValueExact();
					Switch sw = null;
					
					if(nodeType.equalsIgnoreCase("core")) {
						sw = new CoreSwitch(nodeName, bw, iops, upports, downports, nos);
					} else if (nodeType.equalsIgnoreCase("aggregate")){
						sw = new AggregationSwitch(nodeName, bw, iops, upports, downports, nos);
					} else if (nodeType.equalsIgnoreCase("edge")){
						sw = new EdgeSwitch(nodeName, bw, iops, upports, downports, nos);
					} else {
						throw new IllegalArgumentException("No switch found!");
					}
					
					if(sw != null) {
						nameNodeTable.put(nodeName, sw);
						this.switches.add(sw);
					}
				}
			}
				
			JSONArray links = (JSONArray) doc.get("links");
			@SuppressWarnings("unchecked")
			Iterator<JSONObject> linksIter =links.iterator(); 
			while(linksIter.hasNext()){
				JSONObject link = linksIter.next();
				String src = (String) link.get("source");  
				String dst = (String) link.get("destination");
				double lat = (Double) link.get("latency");
				
				Node srcNode = nameNodeTable.get(src);
				Node dstNode = nameNodeTable.get(dst);
				
				Link l = new Link(srcNode, dstNode, lat, -1);
				this.links.add(l);
			}
    		
    		
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
	}
}
