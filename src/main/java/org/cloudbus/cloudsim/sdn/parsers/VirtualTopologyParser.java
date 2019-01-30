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
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.sdn.CloudletSchedulerSpaceSharedMonitor;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.sfc.ServiceFunction;
import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionChainPolicy;
import org.cloudbus.cloudsim.sdn.virtualcomponents.FlowConfig;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * This class parses Virtual Topology (VMs, Network flows between VMs, and SFCs).
 * It loads Virtual Topology JSON file and creates relevant objects in the simulation.
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class VirtualTopologyParser {
	
	private static int flowNumbers=0;
	
	private Multimap<String, SDNVm> vmList;
	private List<ServiceFunction> sfList = new LinkedList<ServiceFunction>(); // SFs are added in both VM list and SF list
	private List<FlowConfig> arcList = new LinkedList<FlowConfig>();
	private List<ServiceFunctionChainPolicy> policyList = new LinkedList<ServiceFunctionChainPolicy>();

	private String vmsFileName;
	private int userId;

	private String defaultDatacenter;
	
	public VirtualTopologyParser(String datacenterName, String topologyFileName, int userId) {
		vmList = HashMultimap.create();
		this.vmsFileName = topologyFileName;
		this.userId = userId;
		this.defaultDatacenter = datacenterName;
		
		parse();
	}
	
	private void parse() {

		try {
    		JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(vmsFileName));
    		
    		Hashtable<String, Integer> vmNameIdTable = parseVMs(doc);
    		Hashtable<String, Integer> flowNameIdTable = parseLinks(doc, vmNameIdTable);
    		parseSFCPolicies(doc, vmNameIdTable, flowNameIdTable);
    		
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private Hashtable<String, Integer> parseVMs(JSONObject doc) {
		Hashtable<String, Integer> vmNameIdTable = new Hashtable<String, Integer>();

		// Parse VM nodes  
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
			long bw = 0;
			
			if(node.get("bw") != null)
				bw = (Long) node.get("bw");
			
			double starttime = 0;
			double endtime = Double.POSITIVE_INFINITY;
			if(node.get("starttime") != null)
				starttime = (Double) node.get("starttime");
			if(node.get("endtime") != null)
				endtime = (Double) node.get("endtime");
			
			String dcName = this.defaultDatacenter;
			if(node.get("datacenter") != null)
				dcName = (String)node.get("datacenter");

			// Optional datacenter specifies the alternative data center if 'data center' has no more resource.
			ArrayList<String> optionalDatacenter = null;
			if(node.get("subdatacenters") != null) {
				optionalDatacenter = new ArrayList<>();				
				JSONArray subDCs = (JSONArray)node.get("subdatacenters");
				
				for (int i=0;i<subDCs.size();i++){
					String subdc = subDCs.get(i).toString();
					optionalDatacenter.add(subdc);
				}
			}
			
			String hostName = "";
			if(node.get("host") != null)
				hostName = (String)node.get("host");

			long nums =1;
			if(node.get("nums") != null)
				nums = (Long) node.get("nums");
			
			for(int n=0; n<nums; n++) {
				String nodeName2 = nodeName;
				if(nums > 1) {
					// Nodename should be numbered.
					nodeName2 = nodeName + n;
				}
				
				CloudletScheduler clSch = new CloudletSchedulerSpaceSharedMonitor(Configuration.TIME_OUT);
				//CloudletScheduler clSch = new CloudletSchedulerTimeSharedMonitor(mips);
				int vmId = SDNVm.getUniqueVmId();
				
				if(nodeType.equalsIgnoreCase("vm")){
					// Create VM objects						
					SDNVm vm = new SDNVm(vmId, userId,mips,pes,ram,bw,size,"VMM", clSch, starttime, endtime);
					vm.setName(nodeName2);
					vm.setHostName(hostName);
					vm.setOptionalDatacenters(optionalDatacenter);
					vmList.put(dcName, vm);
				}
				else {
					// Create ServiceFunction objects
					ServiceFunction sf = new ServiceFunction(vmId,userId,mips,pes,ram,bw,size,"VMM", clSch, starttime, endtime);
					long mipOperation = (Long) node.get("mipoper");
					
					sf.setName(nodeName2);
					sf.setHostName(hostName);
					sf.setOptionalDatacenters(optionalDatacenter);
					sf.setMIperOperation(mipOperation);
					
					sf.setMiddleboxType(nodeType);
					vmList.put(dcName, sf);
					sfList.add(sf);
				}
				
				vmNameIdTable.put(nodeName2, vmId);
			}
		}
		
		return vmNameIdTable;
	}
	
	private Hashtable<String, Integer> parseLinks(JSONObject doc, Hashtable<String, Integer> vmNameIdTable) {
		Hashtable<String, Integer> flowNameIdTable = new Hashtable<String, Integer>();
		
		// Parse VM-VM links
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
			
			FlowConfig arc = new FlowConfig(srcId, dstId, flowId, bw, lat);
			if(flowId != -1) {
				arc.setName(name);
			}
			
			arcList.add(arc);
			flowNameIdTable.put(name, flowId);
		}
		return flowNameIdTable;
	}
	
	private void parseSFCPolicies(JSONObject doc, Hashtable<String, Integer> vmNameIdTable, Hashtable<String, Integer> flowNameIdTable) {
		// Parse SFC policies
		JSONArray policies = (JSONArray)doc.get("policies");
		
		if(policies == null)
			return;
		
		@SuppressWarnings("unchecked")
		Iterator<JSONObject> policyIter = policies.iterator(); 
		while(policyIter.hasNext()){
			JSONObject policy = policyIter.next();
			String name = (String) policy.get("name");
			String src = (String) policy.get("source");  
			String dst = (String) policy.get("destination");
			String flowname = (String) policy.get("flowname");
			Double expectedTime = (Double)policy.get("expected_time");
			if(expectedTime == null) {
				expectedTime = Double.POSITIVE_INFINITY;
			}
			
			int srcId = vmNameIdTable.get(src);
			int dstId = vmNameIdTable.get(dst);
			int flowId = flowNameIdTable.get(flowname);
			
			JSONArray sfc = (JSONArray)policy.get("sfc");
			ArrayList<Integer> sfcList = new ArrayList<Integer>();
			for (int i=0;i<sfc.size();i++){
				String sfName = sfc.get(i).toString();
				int sfVmId =  vmNameIdTable.get(sfName);
				sfcList.add(sfVmId);
			} 
			
			ServiceFunctionChainPolicy pol = new ServiceFunctionChainPolicy(srcId, dstId, flowId, sfcList, expectedTime);
			if(name != null)
				pol.setName(name);
			
			policyList.add(pol);
		}
	}

	public Collection<SDNVm> getVmList(String dcName) {
		return vmList.get(dcName);
	}
	
	public List<FlowConfig> getArcList() {
		return arcList;
	}
	
	public List<ServiceFunction> getSFList() {
		return sfList;
	}

	public List<ServiceFunctionChainPolicy> getSFCPolicyList() {
		return policyList;
	}
}
