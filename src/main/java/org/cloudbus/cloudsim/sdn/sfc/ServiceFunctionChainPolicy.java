/*
 * Title:        CloudSimSDN + SFC
 * Description:  SFC extension for CloudSimSDN
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2018, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.sfc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Packet;

/**
 * This class defines a SFC policy set: (src, dst, srcPort, dstPort, proto) => ServiceFunctionChain.
 * Note that the tuple of (srcPort, dstPort, proto) is represented as a single Channel ID in CloudSimSDN. 
 *
 * @author Jungmin Jay Son
 * @since CloudSimSDN 3.0
 */

public class ServiceFunctionChainPolicy {

	/** Start time to enforce this policy. */
	private double startTime  = 0;
	
	/** End time to finish enforcing this policy. */
	private double finishTime = Double.POSITIVE_INFINITY;
	
	/** Source VM ID. */
	private int srcId;
	
	/** Destination VM ID. */
	private int dstId;

	/** Virtual flow ID from Src -> to Dest. */
	private int flowId; // represent srcPort, dstPort, and Protocol
	
	/** A list of SFs (VMs) that the matching packets (src,dst,flow) have to go through. */
	private List<Integer> sfc;
	
	/** Allocated bandwidth for the SFC policy, dynamically changeable by auto-scale techniques. */
	private long bandwidth;
	
	/** Initial bandwidth allocated for the SFC policy, we retain it because bandwidth fluctuate by auto-scale. */
	private long initBandwidth;

	/** Separate bandwidth allocated for each network section (between one SF to the next SF). */
	private Map<Integer, Long> sectionBandwidth = new HashMap<Integer, Long>();
	
	/** Name of this SFC policy for debug only. */
	private String name = null;
	

	/** End-to-end delay threshold for scale up (expected response time).
	 *  If the measured time is exceeding this, SFs/BWs will scale up to meet the required response time. */
	private double delayMaxThreshold = 0; // If average delay is above this, scale up
	
	/** End-to-end delay threshold for scale down. */
	private double delayMinThreshold = 0; // If average delay is below this, scale down.
	
	/** Bandwidth utilization threshold for scale up. */
	private double bwUtilThresholdMax = Configuration.SFC_OVERLOAD_THRESHOLD_BW;
	
	/** Bandwidth utilization threshold for scale down. */
	private double bwUtilThresholdMin = Configuration.SFC_UNDERLOAD_THRESHOLD_BW;
	
	
	/** Monitor end-to-end delay going through the entire chain: total time */
	private double accumulatedTime = 0;
	
	/** Monitor end-to-end delay going through the entire chain: number of packets */
	private int accumulatedCount = 0;
	
	/** Monitor end-to-end delay going through the entire chain: set of packets */
	private Set<Packet> monitoringPackets = new HashSet<Packet>();

	/** Monitor bandwidth utilization of network of this chain: total bytes
	 *  We use map for separate monitoring of different virtual channels between SF.
	 *  Key (Integer) : VM id of the source (from)
	 *  Value (Long) : processed bytes sent from the VM (key) */
	private Map<Integer, Long> bwMonitorAccumulatedNetworkBytes = new HashMap<Integer, Long>(); // fromVmId -> processedBytes
	
	/** Monitoring: last updated time */
	private double bwMonitorPrevResetTime = 0;
	
	public ServiceFunctionChainPolicy(int srcId, int dstId, int flowId, List<Integer> sfc, double expectedTime) {
		this.srcId = srcId;
		this.dstId = dstId;
		this.flowId = flowId;
		this.sfc = sfc;	// VM ID of SFs. VMs should be created with the assigned ID prior to creating a policy
		this.delayMaxThreshold = expectedTime;
		
		resetMonitoredBwData();
	}
	
	public ServiceFunctionChainPolicy(int srcId, int dstId, int flowId, List<Integer> sfc, double expectedTime, double startTime, double finishTime) {
		this(srcId, dstId, flowId, sfc, expectedTime);
		
		this.startTime = startTime;
		this.finishTime = finishTime;
	}
	
	public void setTotalBandwidth(long bandwidth) {
		this.bandwidth = bandwidth;
	}
	public void setInitialBandwidth(long bw) {
		this.initBandwidth = bw;
		setTotalBandwidth(bw);	
		
		for(int fromId:getServiceFunctionChainIncludeVM()) {
			setSectionBandwidth(fromId, bw);
		}
	}
	public long getTotalBandwidth() {
		return this.bandwidth;
	}
	public long getInitialBandwidth() {
		return this.initBandwidth;
	}
	public void setSectionBandwidth(int fromId, long bandwidth) {
		this.sectionBandwidth.put(fromId, bandwidth);
	}	
	public long getSectionBandwidth(int fromId) {
		return 	this.sectionBandwidth.get(fromId);
	}
	
	public int getSrcId() {
		return srcId;
	}
	public int getDstId() {
		return dstId;
	}
	public int getFlowId() {
		return flowId;
	}
	public List<Integer> getServiceFunctionChain() {
		return sfc;
	}
	public List<Integer> getServiceFunctionChainIncludeVM() {
		List<Integer> chainIncludeVm = new ArrayList<Integer>();
		chainIncludeVm.add(this.srcId);
		chainIncludeVm.addAll(this.sfc);
		chainIncludeVm.add(this.dstId);
		
		return chainIncludeVm;

	}
	
	public void setName(String flowName) {
		this.name = flowName;
	}
	
	public String getName() {
		return this.name;
	}
	
	public double getStartTime() {
		return startTime;
	}
	
	public double getFinishTime() {
		return finishTime;
	}
	
	public String toString() {
		return "SFC:"+getName()+" ("+getSrcId()+" -> "+getDstId()+") "+getFlowId();
	}
	
	public void addMonitoredDelayDataPacket(Packet packet) {
		this.monitoringPackets.add(packet);		
	}
	
	public void resetMonitoredDelayData() {
		updateMonitoredDelayData();
		accumulatedTime = 0;
		accumulatedCount = 0;
	}
	
	public double getMonitoredDelayAverage() {
		updateMonitoredDelayData();
		if(accumulatedCount == 0) {
			return -1;
		}
		return accumulatedTime / accumulatedCount;
	}
	
	public int getMonitoredNumRequests() {
		updateMonitoredDelayData();
		return accumulatedCount;
	}
	
	public double getDelayThresholdMax() {
		return delayMaxThreshold;
	}

	public double getDelayThresholdMin() {
		return delayMinThreshold;
	}
	
	private void updateMonitoredDelayData() {
		Set<Packet> toRemove = new HashSet<Packet>();
		
		//int countToRemove =0;

		for(Packet pkt:this.monitoringPackets) {
			if(pkt.getFinishTime() != -1) {
				double delay = pkt.getFinishTime() - pkt.getStartTime();
				accumulatedTime += delay;
				accumulatedCount++;
				toRemove.add(pkt);
			}
			/*
			else if(pkt.getStartTime() < CloudSim.clock()-Configuration.TIME_OUT) {
				countToRemove++;
			}
			*/
		}
		/*
		if(countToRemove != 0)
			System.err.println(this+"Timeout!"+countToRemove);
		*/
		this.monitoringPackets.removeAll(toRemove);
	}
	
	public void resetMonitoredBwData() {
		this.bwMonitorAccumulatedNetworkBytes.put(this.srcId, 0L);
		for(int sfId:this.sfc) {
			this.bwMonitorAccumulatedNetworkBytes.put(sfId, 0L);
		}
		this.bwMonitorPrevResetTime = CloudSim.clock();
	}
	
	public boolean addMonitoredBwData(int fromId, int toId, long processedBytes) {
		// store separately: from Source VM to SFs to Dest VM
		Long bytes = this.bwMonitorAccumulatedNetworkBytes.get(fromId);
		if(bytes == null)
			return false;
		bytes += processedBytes;
		this.bwMonitorAccumulatedNetworkBytes.put(fromId, bytes);
		
		return true;
	}

	public double getAverageBwUtil(int fromId, int toId) {
		double intervalTime = CloudSim.clock() - this.bwMonitorPrevResetTime;
		Long totalBytes = this.bwMonitorAccumulatedNetworkBytes.get(fromId);
		Long capacity = (long) (getSectionBandwidth(fromId) * intervalTime);
		double utilization = (double)totalBytes / capacity;
		
		return utilization;
	}
	
	public double getAverageBwUtil() {
		double utilSum = 0;
		int utilCount = 0;
		
		List<Integer> vmIds = this.getServiceFunctionChainIncludeVM();
		for(int i=0; i < vmIds.size()-1; i++) {
			int fromId = vmIds.get(i);
			int toId = vmIds.get(i+1);
			double util = getAverageBwUtil(fromId, toId);
			utilSum += util;
			utilCount++;
		}
		
		double utilization = utilSum / utilCount;
		return utilization;
	}
	
	public double getBwUtilThresholdMax() {
		return bwUtilThresholdMax;
	}

	public double getBwUtilThresholdMin() {
		return bwUtilThresholdMin;
	}
	
	public boolean isSFIncludedInChain(int sfId) {
		for(int vmId:getServiceFunctionChain()) {
			if(vmId == sfId)
				return true;
		}
		return false;
	}
	
	public int getPrevVmId(int sfId) {
		// function returns ID of the previous VM in a chain
		List<Integer> vmIds = this.getServiceFunctionChainIncludeVM();
		int prevId = vmIds.get(0);
		for(int nextVmId:vmIds) {
			if(nextVmId == sfId)
				break;
			prevId = nextVmId;
		}
		return prevId;
	}
	
}
