/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.predicates.PredicateType;
import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
import org.cloudbus.cloudsim.sdn.parsers.PhysicalTopologyParser;
import org.cloudbus.cloudsim.sdn.parsers.VirtualTopologyParser;
import org.cloudbus.cloudsim.sdn.policies.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.OverbookingVmAllocationPolicy;

/**
 * NOS calculates and estimates network behaviour. It also mimics SDN Controller functions.  
 * It manages channels between allSwitches, and assigns packages to channels and control their completion
 * Once the transmission is completed, forward the packet to the destination.
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public abstract class NetworkOperatingSystem extends SimEntity {
	protected SDNDatacenter datacenter;
	protected LinkSelectionPolicy linkSelector;

	// Physical topology
	protected String physicalTopologyFileName; 
	protected PhysicalTopology topology;
	protected List<SDNHost> allHosts;
	protected List<Switch> allSwitches;
	
	// Virtual topology
	protected LinkedList<Vm> allVms = new LinkedList<Vm>();
	protected LinkedList<Arc> allArcs = new LinkedList<Arc>();
	protected boolean isApplicationDeployed = false;
	
	// Mapping tables for searching
	protected Map<String, Integer> deployVmNameToIdTable;
	protected Map<String, Integer> deployFlowNameToIdTable;
	protected Map<Integer, Arc> deployFlowIdToArcTable;

	// Processing requests
	protected Hashtable<String, Channel> channelTable;	// getKey(fromVM, toVM, flowID) -> Channel

	// Debug only
	public static Map<Integer, String> debugVmIdName = new HashMap<Integer, String>();
	public static Map<Integer, String> debugFlowIdName = new HashMap<Integer, String>();

	// Resolution of the result.
	public static long bandwidthWithinSameHost = 1500000000; // bandwidth between VMs within a same host: 12Gbps = 1.5GBytes/sec
	public static double latencyWithinSameHost = 0.1; //0.1 msec latency 
	
	private double lastMigration = 0;

	
//	private LinkedList<Channel> allChannels = new LinkedList<Channel>();	// this is only to track all channels.
	
	/**
	 * 1. map VMs and middleboxes to hosts, add the new vm/mb to the vmHostTable, advise host, advise dc
	 * 2. set channels and bws
	 * 3. set routing tables to restrict hops to meet latency
	 */
	protected abstract boolean deployApplication(List<Vm> vms, List<Middlebox> middleboxes, List<Arc> links);
	protected abstract Middlebox deployMiddlebox(String type, Vm vm);

	public NetworkOperatingSystem(String physicalTopologyFilename) {
		super("NOS");
		
		this.physicalTopologyFileName = physicalTopologyFilename;
		this.channelTable = new Hashtable<String, Channel>();
		
		initPhysicalTopology();
	}

	public static double getMinTimeBetweenNetworkEvents() {
	    return CloudSim.getMinTimeBetweenEvents();
	}
	
	public static double round(double value) {
		/*
		int places = Configuration.resolutionPlaces;
	    if (places < 0) throw new IllegalArgumentException();

		if(Configuration.timeUnit >= 1000) value = Math.floor(value*Configuration.timeUnit);
		
	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(places, RoundingMode.CEILING);
	    return bd.doubleValue();
	    */
	    return value;
	}
	
	private boolean monitorEnabled = true;
	
	public void setMonitorEnable(boolean monitorEnable) {
		monitorEnabled = monitorEnable;
	}
	
	public void setLinkSelectionPolicy(LinkSelectionPolicy linkSelectionPolicy) {
		this.linkSelector = linkSelectionPolicy;
	}
	
	public LinkSelectionPolicy getLinkSelectionPolicy() {
		return this.linkSelector;
	}

	
	@Override
	public void startEntity() {
		if(monitorEnabled)
			send(this.getId(), Configuration.monitoringTimeInterval, Constants.MONITOR_UPDATE_UTILIZATION);
	}

	@Override
	public void shutdownEntity() {
		
	}
	
	protected void debugPrintMonitoredValues() {
		//////////////////////////////////////////////////////////////		
		//////////////////////////////////////////////////////////////
		// For debug only
		
		Collection<Link> links = this.topology.getAllLinks();
		for(Link l:links) {
			System.err.println(l);
			MonitoringValues mv = l.getMonitoringValuesLinkUtilizationUp();
			System.err.print(mv);
			mv = l.getMonitoringValuesLinkUtilizationDown();
			System.err.print(mv);
		}
//		
//		for(Channel ch:this.allChannels) {
//			System.err.println(ch);
//			MonitoringValues mv = ch.getMonitoringValuesLinkUtilization();
//			System.err.print(mv);
//		}
		
		for(SDNHost h:datacenter.<SDNHost>getHostList()) {
			System.err.println(h);
			MonitoringValues mv = h.getMonitoringValuesHostCPUUtilization();
			System.err.print(mv);			
		}

		for(Vm vm:allVms) {
			SDNVm tvm = (SDNVm)vm;
			System.err.println(tvm);
			MonitoringValues mv = tvm.getMonitoringValuesVmCPUUtilization();
			System.err.print(mv);			
		}
	}
	
	@Override
	public void processEvent(SimEvent ev) {
		int tag = ev.getTag();
		
		switch(tag){
			case Constants.SDN_INTERNAL_PACKET_PROCESS: 
				internalPacketProcess(); 
				break;
			case CloudSimTags.VM_CREATE_ACK:
				processVmCreateAck(ev);
				break;
			case CloudSimTags.VM_DESTROY:
				processVmDestroyAck(ev);
				break;
			case Constants.MONITOR_UPDATE_UTILIZATION:
				this.datacenter.processUpdateProcessing();
				updatePacketProcessing();
				
				this.updateBWMonitor(Configuration.monitoringTimeInterval);
				this.updateHostMonitor(Configuration.monitoringTimeInterval);
				
				if(CloudSim.clock() >= lastMigration + Configuration.migrationTimeInterval) {
					this.datacenter.startMigrate();
					lastMigration = CloudSim.clock(); 
				}
				this.updateVmMonitor(CloudSim.clock());
				
				if(CloudSimEx.getNumFutureEvents() > 0) {
					double nextMonitorDelay = Configuration.monitoringTimeInterval;
					double nextEventDelay = CloudSimEx.getNextEventTime() - CloudSim.clock();
					
					// If there's no event between now and the next monitoring time, skip monitoring until the next event time. 
					if(nextEventDelay > nextMonitorDelay) {
						nextMonitorDelay = nextEventDelay;	
					}
					
					System.err.println(CloudSim.clock() + ": Elasped time="+ CloudSimEx.getElapsedTimeString()+", "+CloudSimEx.getNumFutureEvents()+" more events, next monitoring in "+nextMonitorDelay);
					send(this.getId(), nextMonitorDelay, Constants.MONITOR_UPDATE_UTILIZATION);
				}
				break;
			default: System.out.println("Unknown event received by "+super.getName()+". Tag:"+ev.getTag());
		}
	}

	protected void processVmCreateAck(SimEvent ev) {
//		SDNVm vm = (SDNVm) ev.getData();
//		Host host = findHost(vm.getId());
//		vm.setSDNHost(host);
	}
	
	// Migrate network flow from previous routing
	protected void processVmMigrate(Vm vm, SDNHost oldHost, SDNHost newHost) {
		// Find the virtual route associated with the migrated VM
		// VM is already migrated to the new host
		for(Arc arc:allArcs) {
			if(arc.getSrcId() == vm.getId()
					|| arc.getDstId() == vm.getId() )
			{
				SDNHost sender = findHost(arc.getSrcId());	// Sender will be the new host after migrated
				if(arc.getSrcId() == vm.getId())
					sender = oldHost;	// In such case, sender should be changed to the old host
				
				rebuildForwardingTable(arc.getSrcId(), arc.getDstId(), arc.getFlowId(), sender);
			}
		}
		
		// Move the transferring data packets in the old channel to the new one.
		migrateChannel(vm, oldHost, newHost);
		
		// Print all routing tables.
//		for(Node node:this.topology.getAllNodes()) {
//			node.printVMRoute();
//		}
	}
	
	protected void processVmDestroyAck(SimEvent ev) {
		Vm destroyedVm = (Vm) ev.getData();
		// remove all channels transferring data from or to this vm.
		for(Vm vm:this.allVms) {
			Channel ch = this.findChannel(vm.getId(), destroyedVm.getId(), -1);
			if(ch != null) {
				this.removeChannel(getKey(vm.getId(), destroyedVm.getId(), -1));
			}

			ch = this.findChannel(destroyedVm.getId(), vm.getId(), -1);
			if(ch != null) {
				this.removeChannel(getKey(destroyedVm.getId(), vm.getId(), -1));
			}

		}
		
		sendInternalEvent();
		
	}

	protected boolean buildForwardingTable(int srcVm, int dstVm, int flowId) {
		SDNHost srchost = (SDNHost) findHost(srcVm);
		SDNHost dsthost = (SDNHost) findHost(dstVm);
		if(srchost == null || dsthost == null) {
			return false;
		}
		
		if(srchost.equals(dsthost)) {
			Log.printLine(CloudSim.clock() + ": " + getName() + ": Source SDN Host is same as Destination. Go loopback");
			srchost.addVMRoute(srcVm, dstVm, flowId, dsthost);
		}
		else {
			Log.printLine(CloudSim.clock() + ": " + getName() + ": VMs are in different hosts:"+ srchost+ "("+srcVm+")->"+dsthost+"("+dstVm+")");
			boolean findRoute = buildForwardingTableRec(srchost, srcVm, dstVm, flowId);
			
			if(!findRoute) {
				System.err.println("NetworkOperatingSystem.deployFlow: Could not find route!!" + 
						NetworkOperatingSystem.debugVmIdName.get(srcVm) + "->"+NetworkOperatingSystem.debugVmIdName.get(dstVm));
				return false;
			}
		}
		return true;
	}
	
	protected boolean buildForwardingTableRec(Node node, int srcVm, int dstVm, int flowId) {
		// There are multiple links. Determine which hop to go.
		SDNHost desthost = findHost(dstVm);
		if(node.equals(desthost))
			return true;
		
		List<Link> nextLinkCandidates = node.getRoute(desthost);
		
		if(nextLinkCandidates == null) {
			throw new IllegalArgumentException();
		}
		
		// Choose which link to follow
		Link nextLink = linkSelector.selectLink(nextLinkCandidates, flowId, findHost(srcVm), desthost, node);
		Node nextHop = nextLink.getOtherNode(node);
		
		node.addVMRoute(srcVm, dstVm, flowId, nextHop);
		buildForwardingTableRec(nextHop, srcVm, dstVm, flowId);
		
		return true;
	}
	
	// This function rebuilds the forwarding table only for the specific VM
	protected void rebuildForwardingTable(int srcVmId, int dstVmId, int flowId, Node srcHost) {
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
	
	private boolean updateDynamicForwardingTableRec(Node node, int srcVm, int dstVm, int flowId, boolean isNewRoute) {
		// There are multiple links. Determine which hop to go.
		SDNHost desthost = findHost(dstVm);
		if(node.equals(desthost))
			return false;	// Nothing changed
		
		List<Link> nextLinkCandidates = node.getRoute(desthost);
		
		if(nextLinkCandidates == null) {
			throw new IllegalArgumentException();
		}
		
		// Choose which link to follow
		Link nextLink = linkSelector.selectLink(nextLinkCandidates, flowId, findHost(srcVm), desthost, node);
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
	
	
	public void addPacketToChannel(Packet pkt) {
		int src = pkt.getOrigin();
		int dst = pkt.getDestination();
		int flowId = pkt.getFlowId();
			
		/*
		if(sender.equals(sender.getVMRoute(src, dst, flowId))) {
			// For loopback packet (when src and dst is on the same host)
			//Log.printLine(CloudSim.clock() + ": " + getName() + ".addPacketToChannel: Loopback package: "+pkt +". Send to destination:"+dst);
			sendNow(sender.getAddress(),Constants.SDN_PACKAGE,pkt);
			return;
		}
		*/
		
		updatePacketProcessing();
		
		Channel channel=findChannel(src, dst, flowId);
		if(channel == null) {
			//No channel establisihed. Add a channel.
			SDNHost sender = findHost(src);
			channel = createChannel(src, dst, flowId, sender);
			
			if(channel == null) {
				// failed to create channel
				System.err.println("ERROR!! Cannot create channel!" + pkt);
				return;
			}
			addChannel(src, dst, flowId, channel);
		}
		
		channel.addTransmission(new Transmission(pkt));
//		Log.printLine(CloudSim.clock() + ": " + getName() + ".addPacketToChannel ("+channel
//				+"): Transmission added:" + 
//				NetworkOperatingSystem.debugVmIdName.get(src) + "->"+
//				NetworkOperatingSystem.debugVmIdName.get(dst) + ", flow ="+flowId + " / eft="+eft);

		sendInternalEvent();
	}
	
	public double getRequestedBandwidth(Packet pkt) {
		int src = pkt.getOrigin();
		int dst = pkt.getDestination();
		int flowId = pkt.getFlowId();
		Channel channel=findChannel(src, dst, flowId);
		double bw = channel.getRequestedBandwidth();
		
		return bw;
	}
	

	private void internalPacketProcess() {
		if(updatePacketProcessing()) {
			sendInternalEvent();
		}
	}
	
	private double nextEventTime = -1;
	
	private void sendInternalEvent() {
		if(channelTable.size() != 0) {
			if(nextEventTime == CloudSim.clock() + NetworkOperatingSystem.getMinTimeBetweenNetworkEvents())
				return;
			
			// More to process. Send event again
			double delay = this.nextFinishTime();

			// Shape the delay
			delay=NetworkOperatingSystem.round(delay);

			if (delay < NetworkOperatingSystem.getMinTimeBetweenNetworkEvents()) { 
				//Log.printLine(CloudSim.clock() + ":Channel: delay is too short: "+ delay);
				delay = NetworkOperatingSystem.getMinTimeBetweenNetworkEvents();
			}

			//Log.printLine(CloudSim.clock() + ": " + getName() + ".sendInternalEvent(): delay for next event="+ delay);

			if((nextEventTime > CloudSim.clock() + delay) || nextEventTime <= CloudSim.clock() ) 
			{
				//Log.printLine(CloudSim.clock() + ": " + getName() + ".sendInternalEvent(): next event time changed! old="+ nextEventTime+", new="+(CloudSim.clock()+delay));
				
				CloudSim.cancelAll(getId(), new PredicateType(Constants.SDN_INTERNAL_PACKET_PROCESS));
				send(this.getId(), delay, Constants.SDN_INTERNAL_PACKET_PROCESS);
				nextEventTime = CloudSim.clock()+delay;
			}
		}
	}
	
	private double nextFinishTime() {
		double earliestEft = Double.POSITIVE_INFINITY;
		for(Channel ch:channelTable.values()){
			
			double eft = ch.nextFinishTime();
			if (eft<earliestEft){
				earliestEft=eft;
			}
		}
		
		if(earliestEft == Double.POSITIVE_INFINITY) {
			throw new IllegalArgumentException("NOS.nextFinishTime(): next finish time is infinite!");
		}
		return earliestEft;
		
	}
	
	private boolean updatePacketProcessing() {
		boolean needSendEvent = false;
		
		LinkedList<Channel> completeChannels = new LinkedList<Channel>();
		
		// Check every channel
		for(Channel ch:channelTable.values()){
			boolean isCompleted = ch.updatePacketProcessing();
			
			if(isCompleted) {
				completeChannels.add(ch);
				
				if(ch.getActiveTransmissionNum() != 0)
				{
					// There are more transmissions even after completing some transmissions.
					needSendEvent = true;
				}

			} else {
				// Something is not completed. Need to send an event. 
				needSendEvent = true;
			}
		}
		
		if(completeChannels.size() != 0) {
			processCompletePackets(completeChannels);
			updateChannel();
		}

		return needSendEvent;
	}
	
	private void processCompletePackets(List<Channel> channels){
		for(Channel ch:channels) {
			for (Transmission tr:ch.getArrivedPackets()){
				Packet pkt = tr.getPacket();
				//Node sender = pkgTable.remove(pkt);
				//Node nextHop = sender.getRoute(pkt.getOrigin(),pkt.getDestination(),pkt.getFlowId());
				
//				Log.printLine(CloudSim.clock() + ": " + getName() + ": Packet completed: "+pkt +". Send to destination:"+ch.getLastNode());
				send(this.datacenter.getId(), ch.getTotalLatency(), Constants.SDN_PACKET_COMPLETE, pkt);
			}
		}
	}
	
	public Map<String, Integer> getVmNameIdTable() {
		return this.deployVmNameToIdTable;
	}
	public Map<String, Integer> getFlowNameIdTable() {
		return this.deployFlowNameToIdTable;
	}
	
	private Channel findChannel(int from, int to, int channelId) {
		// check if there is a pre-configured channel for this application
		Channel channel=channelTable.get(getKey(from,to, channelId));

		if (channel == null) {
			//there is no channel for specific flow, find the default channel for this link
			channel=channelTable.get(getKey(from,to));
		}
		return channel;
	}
	
	private void addChannel(int src, int dst, int chId, Channel ch) {
		//System.err.println("NOS.addChannel:"+getKey(src, dst, chId));
		this.channelTable.put(getKey(src, dst, chId), ch);
		ch.initialize();
		adjustAllChannels();
		
//		allChannels.add(ch);
	}
	
	private Channel removeChannel(String key) {
		//System.err.println("NOS.removeChannel:"+key);
		Channel ch = this.channelTable.remove(key);
		ch.terminate();
		adjustAllChannels();
		return ch;
	}
	
	private void adjustAllChannels() {
		for(Channel ch:this.channelTable.values()) {
			if(ch.adjustDedicatedBandwidthAlongLink()) {
				// Channel BW is changed. send event.
			}
		}
		
		for(Channel ch:this.channelTable.values()) {
			if(ch.adjustSharedBandwidthAlongLink()) {
				// Channel BW is changed. send event.
			}
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
	private void buildNodesLinks(int src, int dst, int flowId, Node srcNode,
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
			Link link = this.topology.getLink(origin.getAddress(), dest.getAddress());
			
			links.add(link);
			nodes.add(dest);
			
			if(dest instanceof SDNHost)
				break;
			
			origin = dest;
			dest = origin.getVMRoute(src, dst, flowId);
		}
	}
	
	private Channel createChannel(int src, int dst, int flowId, Node srcNode) {
		// For dynamic routing, rebuild forwarding table (select which link to use).
		if(linkSelector.isDynamicRoutingEnabled())
			updateDynamicForwardingTableRec(srcNode, src, dst, flowId, false);
		
		List<Node> nodes = new ArrayList<Node>();
		List<Link> links = new ArrayList<Link>();
		
		Node origin = srcNode;
		Node dest = origin.getVMRoute(src, dst, flowId);
		
		if(dest==null) {
			throw new IllegalArgumentException("createChannel(): dest is null, cannot create channel! "+findVm(src)+"->"+findVm(dst)+"|"+flowId);
		}
		
		double lowestBw = Double.POSITIVE_INFINITY;
		double reqBw = 0;
		if(flowId != -1) {
			Arc flow = deployFlowIdToArcTable.get(flowId);
			reqBw = flow.getBw();			
		}
		
		nodes.add(origin);
		
		while(true) {
			Link link = this.topology.getLink(origin.getAddress(), dest.getAddress());
			//Log.printLine(CloudSim.clock() + ": createChannel() :(" +getKey(src,dst,flowId)+"): "+link);
			
			links.add(link);
			nodes.add(dest);
			
			if(lowestBw > link.getFreeBandwidth(origin)) {
				lowestBw = link.getFreeBandwidth(origin);
			}
		
			if(dest instanceof SDNHost)
				break;
			
			origin = dest;
			dest = origin.getVMRoute(src, dst, flowId);
		} 
		
		if(flowId != -1 && lowestBw < reqBw) {
			// free bandwidth is less than required one.
			// Cannot make channel.
			//Log.printLine(CloudSim.clock() + ": " + getName() + ": Free bandwidth is less than required.("+getKey(src,dst,flowId)+"): ReqBW="+ reqBw + "/ Free="+lowestBw);
			//return null;
		}
		
		Channel channel=new Channel(flowId, src, dst, nodes, links, reqBw, 
				(SDNVm)findVm(src), (SDNVm)findVm(dst));
		//Log.printLine(CloudSim.clock() + ": " + getName() + ".createChannel:"+channel);

		return channel;
	}
	
	private void updateChannel() {
		List<String> removeCh = new ArrayList<String>();  
		for(String key:this.channelTable.keySet()) {
			Channel ch = this.channelTable.get(key);
			if(ch.getActiveTransmissionNum() == 0) {
				// No more job in channel. Delete
				removeCh.add(key);
			}
		}
		
		for(String key:removeCh) {
			removeChannel(key);
		}
	}
	
	private void migrateChannel(Vm vm, SDNHost oldHost, SDNHost newHost) {
		for(Channel ch:channelTable.values()) {
			if(ch.getSrcId() == vm.getId()
					|| ch.getDstId() == vm.getId()) {
				List<Node> nodes = new ArrayList<Node>();
				List<Link> links = new ArrayList<Link>();

				SDNHost sender = findHost(ch.getSrcId());	// After migrated
				
				buildNodesLinks(ch.getSrcId(), ch.getDstId(), 
						ch.getChId(), sender, nodes, links);
				
				// update with the new nodes and links
				ch.updateRoute(nodes, links);			
			}
		}
	}
	
	private String getKey(int origin, int destination) {
		return origin+"-"+destination;
	}
	
	private String getKey(int origin, int destination, int appId) {
		return getKey(origin,destination)+"-"+appId;
	}


	public void setDatacenter(SDNDatacenter dc) {
		this.datacenter = dc;
	}

//	public List<Host> getHostList() {
//		return this.hosts;		
//	}
//
//	public List<SDNHost> getSDNHostList() {
//		return this.sdnhosts;		
//	}

	public List<Switch> getSwitchList() {
		return this.allSwitches;
	}

	public boolean isApplicationDeployed() {
		return isApplicationDeployed;
	}

	protected Vm findVm(int vmId) {
		for(Vm vm:allVms) {
			if(vm.getId() == vmId)
				return vm;
		}
		return null;
	}
	protected SDNHost findHost(int vmId) {
		Vm vm = findVm(vmId);
		return (SDNHost)this.datacenter.getVmAllocationPolicy().getHost(vm);
	}
	
//	protected SDNHost findSDNHost(Host host) {
//		for(SDNHost sdnhost:sdnhosts) {
//			if(sdnhost.equals(host)) {
//				return sdnhost;
//			}
//		}
//		return null;
////	}
//	protected SDNHost findSDNHost(int vmId) {
//		Vm vm = findVm(vmId);
//		if(vm == null)
//			return null;
//		
//		for(SDNHost sdnhost:sdnhosts) {
//			if(sdnhost.equals(vm.getHost())) {
//				return sdnhost;
//			}
//		}
//		//System.err.println("NOS.findSDNHost: Host is not found for VM:"+ vmId);
//		return null;
//	}
//	
//	public int getHostAddressByVmId(int vmId) {
//		Vm vm = findVm(vmId);
//		if(vm == null) {
//			Log.printLine(CloudSim.clock() + ": " + getName() + ": Cannot find VM with vmId = "+ vmId);
//			return -1;
//		}
//		
//		Host host = vm.getHost();
//		SDNHost sdnhost = findSDNHost(host);
//		if(sdnhost == null) {
//			Log.printLine(CloudSim.clock() + ": " + getName() + ": Cannot find SDN Host with vmId = "+ vmId);
//			return -1;
//		}
//		
//		return sdnhost.getAddress();
//	}
	
	public abstract SDNHost createHost(int ram, long bw, long storage, long pes, double mips);
	
	protected void initPhysicalTopology() {
		this.topology = new PhysicalTopology();
//		this.hosts = new ArrayList<Host>();
		this.allHosts = new ArrayList<SDNHost>();
		this.allSwitches= new ArrayList<Switch>();
		
		PhysicalTopologyParser parser = new PhysicalTopologyParser(this.physicalTopologyFileName, this);
		
		for(SDNHost sdnHost: parser.getHosts()) {
			topology.addNode(sdnHost);
//			this.hosts.add(sdnHost);
			this.allHosts.add(sdnHost);
		}
		
		for(Switch sw:parser.getSwitches()) {
			topology.addNode(sw);
			this.allSwitches.add(sw);
		}

		for(Link link:parser.getLinks()) {
			Node highNode = link.getHighOrder();
			Node lowNode = link.getLowOrder();
			
			if(highNode.getRank() > lowNode.getRank()) {
				Node temp = highNode;
				highNode = lowNode;
				lowNode = temp;
			}
			double latency = link.getLatency();
			
			topology.addLink(highNode, lowNode, latency);
		}
		
//		topology.buildDefaultRouting();
		topology.buildDefaultRoutingFatTree();
	}
	
	public boolean deployApplication(int userId, String vmsFileName){
		LinkedList<Middlebox> mbList = new LinkedList<Middlebox>();
		deployVmNameToIdTable = new HashMap<String, Integer>();
		deployFlowIdToArcTable = new HashMap<Integer, Arc>();
		deployFlowNameToIdTable = new HashMap<String, Integer>();
		deployFlowNameToIdTable.put("default", -1);
		
		VirtualTopologyParser parser = new VirtualTopologyParser(vmsFileName, userId);
		for(SDNVm vm:parser.getVmList()) {
			
			if(vm.getMiddleboxType() != null ) {
				// For Middle box
				Middlebox m = deployMiddlebox(vm.getMiddleboxType(), vm);
				mbList.add(m);
			} else {
				allVms.add(vm);
			}
			
			deployVmNameToIdTable.put(vm.getName(), vm.getId());
			debugVmIdName.put(vm.getId(), vm.getName());
		}
		
		for(Arc arc:parser.getArcList()) {
			allArcs.add(arc);
			
			deployFlowNameToIdTable.put(arc.getName(), arc.getFlowId());
			if(arc.getFlowId() != -1) {
				deployFlowIdToArcTable.put(arc.getFlowId(), arc);
			}
		}
			
		boolean result = deployApplication(allVms, mbList, parser.getArcList());
		
		isApplicationDeployed = result;		
		return result;
	}
	
	// for monitoring
	private void updateBWMonitor(double monitoringTimeUnit2) {
		double highest=0;
		// Update utilization of all links
		Set<Link> links = new HashSet<Link>(this.topology.getAllLinks());
		for(Link l:links) {
			double util = l.updateMonitor(CloudSim.clock(), monitoringTimeUnit2);
			if(util > highest) highest=util;
		}
		System.err.println(CloudSim.clock()+": Highest utilization of Links = "+highest);
		
		// Update bandwidth consumption of all channels
		for(Channel ch:channelTable.values()) {
			ch.updateMonitor(CloudSim.clock(), monitoringTimeUnit2);
		}
	}

	private void updateHostMonitor(double monitoringTimeUnit2) {
		for(SDNHost h: datacenter.<SDNHost>getHostList()) {
			h.updateMonitor(CloudSim.clock(), monitoringTimeUnit2);
		}
	}
	
	private void updateVmMonitor(double logTime) {
		VmAllocationPolicy vmAlloc = datacenter.getVmAllocationPolicy();
		if(vmAlloc instanceof OverbookingVmAllocationPolicy) {
			for(Vm v: this.allVms) {
				SDNVm vm = (SDNVm)v;
				double mipsOBR = ((OverbookingVmAllocationPolicy)vmAlloc).getCurrentOverbookingRatioMips((SDNVm) vm);
				LogWriter log = LogWriter.getLogger("vm_OBR_mips.csv");
				log.printLine(vm.getName()+","+logTime+","+mipsOBR);
				
				double bwOBR =  ((OverbookingVmAllocationPolicy)vmAlloc).getCurrentOverbookingRatioBw((SDNVm) vm);
				log = LogWriter.getLogger("vm_OBR_bw.csv");
				log.printLine(vm.getName()+","+logTime+","+bwOBR);
			}
		}
	}


	@SuppressWarnings("unchecked")
	public <T extends Host> List<T> getHostList() {
		return (List<T>)allHosts;
	}
	
	public PhysicalTopology getPhysicalTopology() {
		return this.topology;
	}
}
