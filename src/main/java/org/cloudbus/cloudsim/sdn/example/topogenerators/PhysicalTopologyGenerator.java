/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.example.topogenerators;

import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
/**
 * Generate Physical topology Json file, for example:
{
  "nodes" : [
     {
      "name": "core",
      "type" : "core",
      "iops" : 1000000000,
      "bw" : 1000000000,
    },
    {
      "name": "edge1",
      "type" : "edge",
      "iops" : 1000000000,
      "bw" : 1000000000,
    },
    {
      "name": "host01",
      "type" : "host",
      "pes" : 1,
      "mips" : 30000000,
      "ram" : 10240,
      "storage" : 10000000,
      "bw" : 200000000,
    },
     {
      "name": "host02",
      "type" : "host",
      "pes" : 1,
      "mips" : 30000000,
      "ram" : 10240,
      "storage" : 10000000,
      "bw" : 200000000,
    },
   ],
 "links" : [
    { "source" : "core" , "destination" : "edge1" , "latency" : 0.5 },
    { "source" : "edge1" , "destination" : "host01" , "latency" : 0.5 },
    { "source" : "edge1" , "destination" : "host02" , "latency" : 0.5 },
  ]
}
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class PhysicalTopologyGenerator {

	public static void main(String [] argv) {
		startTest();
	}
	
	public static void startConst() {		
		String jsonFileName = "wiki.physical.constant.short.json";
		
//		int fanout = 2;
		int numPods = 8;	// Total hosts = (numPods^3)/4
		double latency = 0.1;
		
		long iops = 1000000000L;
		
		int pe = 4;
		long mips = 400;//8000;
		int ram = 10240;
		long storage = 10000000;
		//long bw = 125000000;
		long bw = 1000000000;
		
		PhysicalTopologyGenerator reqg = new PhysicalTopologyGenerator();
		HostSpec hostSpec = reqg.createHostSpec(pe, mips, ram, storage, bw);
//		reqg.createTreeTopology(hostSpec, iops, bw, fanout, latency);
		reqg.createTopologyFatTree(hostSpec, iops, bw, numPods, latency);
		reqg.wrtieJSON(jsonFileName);
	}
	
	
	public static void startTest() {		
		String jsonFileName = "physical.test.json";
		
		int fanout = 4;
		int numPods = 2;	// Total hosts = (fanout^2) * numPods
		int redundancy = 2;	// For aggr and core tier, how many switches are connected.
		double latency = 0.1;
		
		long iops = 1000000000L;
		
		int pe = 1;
		long mips = 100;//8000;
		int ram = 10240;
		long storage = 10000000;
		//long bw = 125000000;
		long bw = 100;
		
		PhysicalTopologyGenerator reqg = new PhysicalTopologyGenerator();
		HostSpec hostSpec = reqg.createHostSpec(pe, mips, ram, storage, bw);
		reqg.createTestTopology(hostSpec, iops, 
				bw,bw,bw, 
				latency, fanout, numPods, redundancy);
		//reqg.createTopologyFatTree(hostSpec, iops, bw, numPods, latency);
		reqg.wrtieJSON(jsonFileName);
	}
	
	public static void startTree() {		
		String jsonFileName = "wiki.physical.tree.json";
		
		int fanout = 4;
		int numPods = 8;	// Total hosts = (fanout^2) * numPods
		int redundancy = 2;	// For aggr and core tier, how many switches are connected.
		double latency = 0.1;
		
		long iops = 1000000000L;
		
		int pe = 8;
		long mips = 2000;//8000;
		int ram = 10240;
		long storage = 10000000;
		//long bw = 125000000;
		long bw = 1000000000;
		
		PhysicalTopologyGenerator reqg = new PhysicalTopologyGenerator();
		HostSpec hostSpec = reqg.createHostSpec(pe, mips, ram, storage, bw);
		reqg.createMultiLinkTreeTopology(hostSpec, iops, 
				bw,bw,bw, 
				latency, fanout, numPods, redundancy);
		//reqg.createTopologyFatTree(hostSpec, iops, bw, numPods, latency);
		reqg.wrtieJSON(jsonFileName);
	}


	protected void createTreeTopology(HostSpec hostSpec, long swIops, long swBw, int fanout, double latency) {
		// core, aggregation, edge
		// Core switch
		SwitchSpec c = addSwitch("c", "core", swBw, swIops);
		
		for(int i=0; i<fanout; i++) {
			SwitchSpec e = addSwitch("e"+i, "edge", swBw, swIops);
			addLink(c, e, latency);
			
			for(int j=0; j<fanout; j++) {
				String hostname = "h_" + i + "_" + j;
				HostSpec h = addHost(hostname, hostSpec);
				addLink(e, h, latency);
			}
		}
	}
	
	// This creates a 3 layer cannonical tree topology with redundant links on aggr and core layers
	// https://www.grotto-networking.com/figures/BBNetVirtualizationDataCenter/DCNetworkConventional.png
	protected void createMultiLinkTreeTopology(HostSpec hostSpec, long swIops,
			long coreBw, long aggrBw, long edgeBw,
			double latency, int fanout, int numPods, int redundancy) {
		// core, aggregation, edge
		// Core switch
		SwitchSpec [] c = new SwitchSpec [redundancy];
		for(int i=0; i<redundancy; i++) {
			c[i] = addSwitch("c_"+i, "core", coreBw, swIops);
		}
		
		for(int pod=0; pod<numPods; pod++) {
			SwitchSpec [] a = new SwitchSpec [redundancy];
			
			for(int i=0; i<redundancy; i++) {
				a[i] = addSwitch("a"+pod+"_"+i, "aggregate", aggrBw, swIops);

				// Add link between aggr - core
				for(int j=0; j<redundancy; j++)
					addLink(a[i], c[j], latency);
			}

			for(int i=0; i<fanout; i++) {
				SwitchSpec e = addSwitch("e"+pod+"_"+i, "edge", edgeBw, swIops);
				// Add link between aggr - edge
				for(int j=0; j<redundancy; j++)
					addLink(a[j], e, latency);
				
				for(int j=0; j<fanout; j++) {
					String hostname = "h"+pod+"_" + i + "_" + j;
					HostSpec h = addHost(hostname, hostSpec);
					addLink(e, h, latency);
				}
			}
		}
	}
	
	protected void createTestTopology(HostSpec hostSpec, long swIops,
			long coreBw, long aggrBw, long edgeBw,
			double latency, int fanout, int numPods, int redundancy) {
		// core, aggregation, edge
		// Core switch
		SwitchSpec [] c = new SwitchSpec [redundancy];
		for(int i=0; i<redundancy; i++) {
			c[i] = addSwitch("c_"+i, "core", coreBw, swIops);
		}
		
		for(int pod=0; pod<numPods; pod++) {
			SwitchSpec e = addSwitch("e"+pod, "edge", edgeBw, swIops);
			// Add link between aggr - edge
			for(int j=0; j<redundancy; j++)
				addLink(c[j], e, latency);
			
			for(int j=0; j<fanout; j++) {
				String hostname = "h"+pod+"_" + j;
				HostSpec h = addHost(hostname, hostSpec);
				addLink(e, h, latency);
			}
		}
	}
	
	
	protected void createTopologyFatTree(HostSpec hostSpec, long swIops, long swBw, int numpods, double latency) {
		SwitchSpec [][] c = new SwitchSpec [numpods/2][numpods/2];
		for(int i=0; i<numpods/2; i++) {
			for(int j=0; j<numpods/2; j++) {
				c[i][j] = addSwitch("c_"+i+"_"+j, "core", swBw, swIops);
			}
		}
		
		for(int k=0; k<numpods; k++) {
			SwitchSpec [] e = new SwitchSpec[numpods/2];
			SwitchSpec [] a = new SwitchSpec[numpods/2];
			
			for(int i=0; i<numpods/2; i++) {
				e[i] = addSwitch("e_"+k+"_"+i, "edge", swBw, swIops);
				a[i] = addSwitch("a_"+k+"_"+i, "aggregate", swBw, swIops);
				addLink(a[i], e[i], latency);
				
				for(int j=0; j<i; j++) {
					addLink(a[i], e[j], latency);
					addLink(a[j], e[i], latency);
				}
				
				for(int j=0; j<numpods/2; j++) {
					addLink(a[i], c[i][j], latency);
				}
				
				for(int j=0; j<numpods/2; j++) {
					String hostname = "h_" + k+"_"+ i + "_" + j;
					HostSpec h = addHost(hostname, hostSpec);
					addLink(e[i], h, latency);
				}
			}
		}
	}
	
	private List<HostSpec> hosts = new ArrayList<HostSpec>();
	private List<SwitchSpec> switches = new ArrayList<SwitchSpec>();
	private List<LinkSpec> links = new ArrayList<LinkSpec>();

	public HostSpec addHost(String name, HostSpec spec) {
		HostSpec host = new HostSpec(spec.pe, spec.mips, spec.ram, spec.storage, spec.bw);
		
		host.name = name;
		host.type = "host";
		
		hosts.add(host);
		return host;
	}
	public HostSpec addHost(String name, int pes, long mips, int ram, long storage, long bw) {
		HostSpec host = new HostSpec(pes, mips, ram, storage, bw);
		return addHost(name, host);
	}
	
	public SwitchSpec addSwitch(String name, String type, long bw, long iops) {
		SwitchSpec sw = new SwitchSpec();
		
		sw.name = name;
		sw.type = type;		// core, aggregation, edge
		sw.bw = bw;
		sw.iops = iops;
		
		switches.add(sw);
		return sw;
	}
	
	
	private void addLink(NodeSpec source, NodeSpec dest, double latency) {
		links.add(new LinkSpec(source.name,dest.name, latency));
	}
	
	public HostSpec createHostSpec(int pe, long mips, int ram, long storage, long bw) {
		return new HostSpec(pe, mips, ram, storage, bw);
	}

	class NodeSpec {
		String name;
		String type;
		long bw;
	}
	class HostSpec extends NodeSpec {
		int pe;
		long mips;
		int ram;
		long storage;
		
		@SuppressWarnings("unchecked")
		JSONObject toJSON() {
			HostSpec o = this;
			JSONObject obj = new JSONObject();
			obj.put("name", o.name);
			obj.put("type", o.type);
			obj.put("storage", o.storage);
			obj.put("pes", o.pe);
			obj.put("mips", o.mips);
			obj.put("ram", new Integer(o.ram));
			obj.put("bw", o.bw);
			return obj;
		}
		public HostSpec(int pe, long mips, int ram, long storage, long bw) {
			this.pe = pe;
			this.mips = mips;
			this.ram = ram;
			this.storage = storage;
			this.bw = bw;
			this.type = "host";
		}
	}

	class SwitchSpec extends NodeSpec {
		long iops;
		
		@SuppressWarnings("unchecked")
		JSONObject toJSON() {
			SwitchSpec o = this;
			JSONObject obj = new JSONObject();
			obj.put("name", o.name);
			obj.put("type", o.type);
			obj.put("iops", o.iops);
			obj.put("bw", o.bw);
			return obj;
		}
	}

	class LinkSpec {
		String source;
		String destination;
		double latency;
		
		public LinkSpec(String source,String destination,double latency2) {
			this.source = source;
			this.destination = destination;
			this.latency = latency2;
		}
		@SuppressWarnings("unchecked")
		JSONObject toJSON() {
			LinkSpec link = this;
			JSONObject obj = new JSONObject();
			obj.put("source", link.source);
			obj.put("destination", link.destination);
			obj.put("latency", link.latency);
			return obj;
		}
	}
	
	int vmId = 0;
	
	@SuppressWarnings("unchecked")
	public void wrtieJSON(String jsonFileName) {
		JSONObject obj = new JSONObject();

		JSONArray nodeList = new JSONArray();
		JSONArray linkList = new JSONArray();
		
		for(HostSpec o:hosts) {
			nodeList.add(o.toJSON());
		}
		for(SwitchSpec o:switches) {
			nodeList.add(o.toJSON());
		}
		
		for(LinkSpec link:links) {
			linkList.add(link.toJSON());
		}
		
		obj.put("nodes", nodeList);
		obj.put("links", linkList);
	 
		try {
	 
			FileWriter file = new FileWriter(jsonFileName);
			file.write(obj.toJSONString().replaceAll(",", ",\n"));
			file.flush();
			file.close();
	 
		} catch (IOException e) {
			e.printStackTrace();
		}
	 
		System.out.println(obj);
	}
}
