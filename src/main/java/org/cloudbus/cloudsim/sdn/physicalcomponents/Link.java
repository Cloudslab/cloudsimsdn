/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.physicalcomponents;

import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.sdn.LogWriter;
import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;

/**
 * This is physical link between hosts and switches to build physical topology.
 * Links have latency and bandwidth.
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Link {
	// bi-directional link (one link = both ways)
	private Node highOrder;
	private Node lowOrder;
	private double upBW;	// low -> high
	private double downBW;	// high -> low
	private double latency;	// in milliseconds, need to *0.001 to transform in seconds.
	
	private List<Channel> upChannels;
	private List<Channel> downChannels;
	
	public Link(Node highOrder, Node lowOrder, double latency, double bw) {
		this.highOrder = highOrder;
		this.lowOrder = lowOrder;
		this.upBW = this.downBW = bw;
		this.latency = latency;
		
		this.upChannels = new LinkedList<Channel>();
		this.downChannels = new LinkedList<Channel>();
	}
	
	public Link(Node highOrder, Node lowOrder, double latency, double upBW, double downBW) {
		this(highOrder, lowOrder, latency, upBW);
		this.downBW = downBW;
	}

	public Node getHighOrder() {
		return highOrder;
	}

	public Node getLowOrder() {
		return lowOrder;
	}
	
	public Node getOtherNode(Node from) {
		if(highOrder.equals(from))
			return lowOrder;
		
		return highOrder;
	}
	
	private boolean isUplink(Node from) {
		if(from == lowOrder) {
			return true;
		}
		else if(from == highOrder) {
			return false;
		}
		else {
			throw new IllegalArgumentException("Link.isUplink(): from("+from+") Node is wrong!!");			
		}
	}
	
	public double getBw(Node from) {
		if(isUplink(from)) {
			return upBW;
		}
		else {
			return downBW;
		}
	}
	
	public double getBw() {
		if(upBW != downBW) {
			throw new IllegalArgumentException("Downlink/Uplink BW are different!");
		}
		return upBW;
	}

	public double getLatency() {
		return latency;
	}
	
	public double getLatencyInSeconds() {
		return latency*0.001;
	}
	
	private List<Channel> getChannels(Node from) {
		List<Channel> channels;
		if(isUplink(from)) {
			channels = this.upChannels;
		}
		else {
			channels = this.downChannels;
		}

		return channels;
	}
	
	public double getDedicatedChannelAdjustFactor(Node from) {
		double totalRequested = getRequestedBandwidthForDedicatedChannels(from);
		
		if(totalRequested > this.getBw()) {
			//Log.printLine("Link.getDedicatedChannelAdjustFactor() Exceeds link bandwidth. Reduce requested bandwidth!");
			return this.getBw() / totalRequested;
		}
		return 1.0;
	}
	
	public boolean addChannel(Node from, Channel ch) {
		getChannels(from).add(ch);
		updateRequestedBandwidthForDedicatedChannels(from);
		return true;
	}
	
	public boolean removeChannel(Node from, Channel ch) {
		boolean ret = getChannels(from).remove(ch);
		updateRequestedBandwidthForDedicatedChannels(from);
		return ret;
	}
	
	public void updateChannel(Node from, Channel ch) {
		updateRequestedBandwidthForDedicatedChannels(from);
	}
	
	/*
	private double allocatedBandwidthDedicatedUp = 0;
	private double allocatedBandwidthDedicatedDown = 0;
	
	private double getAllocatedBandwidthForDedicatedChannels(Node from) {
		if(this.isUplink(from))
			return allocatedBandwidthDedicatedUp;
		else
			return allocatedBandwidthDedicatedDown;
	}
	*/

	private double getAllocatedBandwidthForDedicatedChannels(Node from) {
		
		double bw=0;
		for(Channel ch: getChannels(from)) {
			if(ch.getChId() != -1) {
				// chId == -1 : default channel
				bw += ch.getAllocatedBandwidth();
			}
		}
		return bw;
	}

	private double requestedBandwidthDedicatedUp = 0;
	private double requestedBandwidthDedicatedDown = 0;
	
	private double getRequestedBandwidthForDedicatedChannels(Node from) {
		if(this.isUplink(from))
			return requestedBandwidthDedicatedUp;
		else
			return requestedBandwidthDedicatedDown;
	}

	private void updateRequestedBandwidthForDedicatedChannels(Node from) {
		// Look through all busy channel and sum up the amount of total requested bandwidth.
		double bw=0;
		for(Channel ch: getChannels(from)) {
			if(ch.getChId() != -1) {
				// chId == -1 : default channel
				bw += ch.getRequestedBandwidth(); // Only counted for 'Dedicated' channels
			}
		}
		if(isUplink(from)) {
			requestedBandwidthDedicatedUp = bw;
		}
		else{
			requestedBandwidthDedicatedDown = bw;
		}
	}

	public int getChannelCount(Node from) {
		List<Channel> channels =  getChannels(from);
		return channels.size();
	}
	
	public int getDedicatedChannelCount(Node from) {
		int num=0;
		for(Channel ch: getChannels(from)) {
			if(ch.getChId() != -1) {
				// chId == -1 : default channel
				num ++;
			}
		}
		return num;
	}
	
	public int getSharedChannelCount(Node from) {
		int num =  getChannels(from).size() - getDedicatedChannelCount(from);
		return num;
	}
	
	public double getFreeBandwidth(Node from) {
		double bw = this.getBw(from);
		double dedicatedBw = getAllocatedBandwidthForDedicatedChannels(from);
		
		double freeBw = bw-dedicatedBw;
		
		if(freeBw <0) {
			System.err.println("This link has no free BW, all occupied by dedicated channels!"+this);
			freeBw=0;
		}
		
		return freeBw;
	}

	/*
	public double getFreeBandwidthForDedicatedChannel(Node from) {
		double bw = this.getBw(from);
		double dedicatedBw = getRequestedBandwidthForDedicatedChannels(from);
		
		return bw-dedicatedBw;
	}
	*/

	public double getSharedBandwidthPerChannel(Node from) {
		double freeBw = getFreeBandwidth(from);
		double sharedBwEachChannel = freeBw / getSharedChannelCount(from);
		
		if(sharedBwEachChannel < 0)
			System.err.println("Negative BW on link:"+this);
		
		return sharedBwEachChannel;
	}

	public String toString() {
		return "Link:"+this.highOrder.toString() + " <-> "+this.lowOrder.toString() + ", upBW:" + upBW + ", Latency:"+ latency;
	}
	
	public boolean isActive() {
		if(this.upChannels.size() >0 || this.downChannels.size() >0)
			return true;

		return false;
		
	}
	
	// For monitor
	private MonitoringValues mvUp = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
	private MonitoringValues mvDown = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
	private long monitoringProcessedBytesPerUnitUp = 0;
	private long monitoringProcessedBytesPerUnitDown = 0;
	
	public double updateMonitor(double logTime, double timeUnit) {
		long capacity = (long) (this.getBw() * timeUnit);
		double utilization1 = (double)monitoringProcessedBytesPerUnitUp / capacity;
		mvUp.add(utilization1, logTime);
		monitoringProcessedBytesPerUnitUp = 0;
		
		LogWriter log = LogWriter.getLogger("link_utilization_up.csv");
		log.printLine(this.lowOrder+","+logTime+","+utilization1);
		
		double utilization2 = (double)monitoringProcessedBytesPerUnitDown / capacity;
		mvDown.add(utilization2, logTime);
		monitoringProcessedBytesPerUnitDown = 0;
		LogWriter logDown = LogWriter.getLogger("link_utilization_down.csv");
		logDown.printLine(this.highOrder+","+logTime+","+utilization2);		
		
		return Double.max(utilization1, utilization2);
	}
	
	public MonitoringValues getMonitoringValuesLinkUtilizationDown() { 
		return mvDown;
	}
	public MonitoringValues getMonitoringValuesLinkUtilizationUp() { 
		return mvUp;
	}

	public void increaseProcessedBytes(Node from, long processedBytes) {
		if(isUplink(from))
			this.monitoringProcessedBytesPerUnitUp += processedBytes;
		else
			this.monitoringProcessedBytesPerUnitDown += processedBytes;
		
	}
}
