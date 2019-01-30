package org.cloudbus.cloudsim.sdn.nos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionForwarder;
import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;
import org.cloudbus.cloudsim.sdn.virtualcomponents.VirtualNetworkMapper;

public class ChannelManager {
	protected NetworkOperatingSystem nos = null;
	protected VirtualNetworkMapper vnMapper = null;
	protected ServiceFunctionForwarder sfcForwarder = null;
	
	// Processing requests
	protected HashMap<String, Channel> channelTable = new HashMap<String, Channel>();	// getKey(fromVM, toVM, flowID) -> Channel
	protected List<Channel> tempRemovedChannels = new LinkedList<Channel>();
	
	public ChannelManager(NetworkOperatingSystem nos, VirtualNetworkMapper vnMapper,
			ServiceFunctionForwarder sfcForwarder) {
		this.nos = nos;
		this.vnMapper = vnMapper;
		this.sfcForwarder = sfcForwarder;
	}
	
	public Channel createChannel(int src, int dst, int flowId, Node srcNode) {
		// For dynamic routing, rebuild forwarding table (select which link to use).
		vnMapper.updateDynamicForwardingTableRec(srcNode, src, dst, flowId, false);
		
		List<Node> nodes = new ArrayList<Node>();
		List<Link> links = new ArrayList<Link>();
		
		Node origin = srcNode;
		Node dest = origin.getVMRoute(src, dst, flowId);
		
		if(dest==null) {
			throw new IllegalArgumentException("createChannel(): dest is null, cannot create channel! " +
					NetworkOperatingSystem.findVmGlobal(src)+"->" + 
					NetworkOperatingSystem.findVmGlobal(dst)+"|"+flowId);
		}
		
		double lowestBw = Double.POSITIVE_INFINITY;
		double reqBw = 0;
		if(flowId != -1) {
			reqBw = nos.getRequestedBandwidth(flowId);
			if(reqBw == 0)
				throw new RuntimeException("reqBW cannot be zero for dedicated channels!!"+flowId);
		}
		
		nodes.add(origin);
		
		// Find the lowest available bandwidth along the link.
		while(true) {
			Link link = origin.getLinkTo(dest);
			if(link == null)
				throw new IllegalArgumentException("Link is NULL for srcNode:"+origin+" -> dstNode:"+dest);
			
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
		
		// If currently free bandwidth is less than required one.
		if(flowId != -1 && lowestBw < reqBw) {
			// Cannot make channel.
			//Log.printLine(CloudSim.clock() + ": " + getName() + ": Free bandwidth is less than required.("+getKey(src,dst,flowId)+"): ReqBW="+ reqBw + "/ Free="+lowestBw);
			//return null;
		}
		
		Channel channel=new Channel(flowId, src, dst, nodes, links, reqBw, 
				(SDNVm)NetworkOperatingSystem.findVmGlobal(src), (SDNVm)NetworkOperatingSystem.findVmGlobal(dst));
		//Log.printLine(CloudSim.clock() + ": " + getName() + ".createChannel:"+channel);
	
		return channel;
	}

	public void addChannel(int src, int dst, int chId, Channel ch) {
			//System.err.println("NOS.addChannel:"+getKey(src, dst, chId));
			
			this.channelTable.put(getChannelKey(src, dst, chId), ch);
			ch.initialize();
			
			ch.adjustDedicatedBandwidthAlongLink();
			ch.adjustSharedBandwidthAlongLink();
			
			nos.sendAdjustAllChannelEvent();		
	//		allChannels.add(ch);
		}

	public Channel findChannel(int from, int to, int channelId) {
		// check if there is a pre-configured channel for this application
		Channel channel=channelTable.get(getChannelKey(from, to, channelId));
	
		if (channel == null) {
			//there is no channel for specific flow, find the default channel for this link
			channel=channelTable.get(getChannelKey(from,to));
		}
		return channel;
	}
	
	public List<Channel> findAllChannels(int vmId) {
		List<Channel> allChannels = new ArrayList<>();
		
		for(Channel ch:channelTable.values()) {
			if(ch.getSrcId() == vmId
					|| ch.getDstId() == vmId) {
				allChannels.add(ch);
			}
		}
		
		return allChannels;
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
	
	public Channel removeChannel(int srcVm, int dstVm, int flowId) {
		if(findChannel(srcVm, dstVm, flowId) == null)
			return null;
		return removeChannel(getChannelKey(srcVm, dstVm, flowId));
	}
	
	private Channel removeChannel(String key) {
		//System.err.println("NOS.removeChannel:"+key);
		Channel ch = this.channelTable.remove(key);
		ch.terminate();
		nos.sendAdjustAllChannelEvent();
		tempRemovedChannels.add(ch);
		return ch;
	}
	
	private void resetTempRemovedChannel() {
		tempRemovedChannels = new LinkedList<Channel>();
	}
	
	public boolean updateChannelBandwidth(int src, int dst, int flowId, long newBandwidth) {
		Channel ch = this.channelTable.get(getChannelKey(src, dst, flowId));
		if(ch != null) {
			ch.updateRequestedBandwidth(newBandwidth);
			return true;
		}
		
		return false;
	}
	
	public void adjustAllChannel() {
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
	
	public double nextFinishTime() {
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
	
	public boolean updatePacketProcessing() {
		boolean needSendEvent = false;
		
		LinkedList<Channel> completeChannels = new LinkedList<Channel>();
		
		// Check every channel
		for(Channel ch:channelTable.values()){
			boolean isCompleted = ch.updatePacketProcessing();
			
			if(isCompleted) {
				completeChannels.add(ch);
				
				if(ch.getActiveTransmissionNum() != 0)
				{
					// There are more transmissions even after completing these transmissions.
					needSendEvent = true;
				}

			} else {
				// Something is not completed. Need to send an event. 
				needSendEvent = true;
			}
		}
		
		if(completeChannels.size() != 0) {
			nos.processCompletePackets(completeChannels);
			updateChannel();
		}

		return needSendEvent;
	}
	
	public long getTotalNumPackets() {
		long numPackets=0;
		for(Channel ch:channelTable.values()) {
			numPackets += ch.getActiveTransmissionNum();
		}
		
		return numPackets;
	}
	
	public long getTotalChannelNum() {
		return channelTable.size();
	}

	public static String getChannelKey(int origin, int destination) {
		return origin+"-"+destination;
	}
	
	public static String getChannelKey(int origin, int destination, int appId) {
		return getChannelKey(origin,destination)+"-"+appId;
	}

	public void updateMonitor(double monitoringTimeUnit) {
		// Update bandwidth consumption of all channels
		for(Channel ch:channelTable.values()) {
			long processedBytes = ch.updateMonitor(CloudSim.clock(), monitoringTimeUnit);
			sfcForwarder.updateSFCMonitor(ch.getSrcId(), ch.getDstId(), ch.getChId(), processedBytes);
		}
		
		for(Channel ch:tempRemovedChannels) {
			long processedBytes = ch.updateMonitor(CloudSim.clock(), monitoringTimeUnit);
			sfcForwarder.updateSFCMonitor(ch.getSrcId(), ch.getDstId(), ch.getChId(), processedBytes);
		}
		this.resetTempRemovedChannel();
		
	}

	
}
