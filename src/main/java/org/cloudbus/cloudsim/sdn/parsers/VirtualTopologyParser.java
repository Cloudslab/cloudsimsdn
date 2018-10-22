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
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.sdn.Arc;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.policies.CloudletSchedulerTimeSharedMonitor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class VirtualTopologyParser {
	
	private static int flowNumbers=0;
	private static int vmId=0;
	
	private List<SDNVm> vmList = new LinkedList<SDNVm>();
	private List<Arc> arcList = new LinkedList<Arc>();

	private String vmsFileName;
	private int userId;
	
	public VirtualTopologyParser(String topologyFileName, int userId) {
		this.vmsFileName = topologyFileName;
		this.userId = userId;
		
		parse();
	}
	
	private void parse(){
		Hashtable<String, Integer> vmNameIdTable = new Hashtable<String, Integer>();

		try {
    		JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(vmsFileName));
    		JSONArray nodes = (JSONArray) doc.get("nodes");
    		
    		@SuppressWarnings("unchecked")
			Iterator<JSONObject> iter = nodes.iterator(); 
			while(iter.hasNext()){
				JSONObject node = iter.next();
				
				String nodeType = (String) node.get("type");
				String nodeName = (String) node.get("name");
				int pes = new BigDecimal((Long)node.get("pes")).intValueExact();
				long mips = (Long) node.get("mips");
				int ram = new BigDecimal((Long)node.get("ram")).intValueExact();
				long size = (Long) node.get("size");
				long bw = 1000;
				if(node.get("bw") != null)
					bw = (Long) node.get("bw");
				
				double starttime = 0;
				double endtime = Double.POSITIVE_INFINITY;
				if(node.get("starttime") != null)
					starttime = (Double) node.get("starttime");
				if(node.get("endtime") != null)
					endtime = (Double) node.get("endtime");

				long nums =1;
				if(node.get("nums") != null)
					nums = (Long) node.get("nums");
				
				for(int n=0; n<nums; n++) {
					String nodeName2 = nodeName;
					if(nums > 1) {
						// Nodename should be numbered.
						nodeName2 = nodeName + n;
					}
					
					//CloudletScheduler clSch = new CloudletSchedulerSpaceSharedMonitor(mips);
					CloudletScheduler clSch = new CloudletSchedulerTimeSharedMonitor(mips);
					
					SDNVm vm = new SDNVm(vmId,userId,mips,pes,ram,bw,size,"VMM", clSch, starttime, endtime);
					vm.setName(nodeName2);
					
					if(!nodeType.equalsIgnoreCase("vm")){
						vm.setMiddleboxType(nodeType);
					}
					vmList.add(vm);
					vmNameIdTable.put(nodeName2, vmId);
					
					vmId++;
				}
			}
			
			JSONArray links = (JSONArray) doc.get("links");
			
			@SuppressWarnings("unchecked")
			Iterator<JSONObject> linksIter = links.iterator(); 
			while(linksIter.hasNext()){
				JSONObject link = linksIter.next();
				String name = (String) link.get("name");
				String src = (String) link.get("source");  
				String dst = (String) link.get("destination");
				
				Object reqLat = link.get("latency");
				Object reqBw = link.get("bandwidth");
				
				double lat = 0.0;
				long bw = 0;
				
				if(reqLat != null)
					lat = (Double) reqLat;
				if(reqBw != null)
					bw = (Long) reqBw;
				
				int srcId = vmNameIdTable.get(src);
				int dstId = vmNameIdTable.get(dst);
				
				int flowId = -1;
				
				if(name == null || "default".equalsIgnoreCase(name)) {
					// default flow.
					flowId = -1;
				}
				else {
					flowId = flowNumbers++;
				}
				
				Arc arc = new Arc(srcId, dstId, flowId, bw, lat);
				if(flowId != -1) {
					arc.setName(name);
				}
				
				arcList.add(arc);
			}
    	
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public List<SDNVm> getVmList() {
		return vmList;
	}
	
	public List<Arc> getArcList() {
		return arcList;
	}
}
