/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.workload;

import org.cloudbus.cloudsim.sdn.Packet;

/**
 * This class represents transmission of a package. It controls
 * amount of data transmitted in a shared data medium. Relation between
 * Transmission and Channel is the same as Cloudlet and CloudletScheduler,
 * but here we consider only the time shared case, representing a shared
 * channel among different simultaneous package transmissions.
 * Note that estimated transmission time is calculated in NOS.
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Transmission implements Activity {
	private Packet pkt = null;
	private long amountToBeProcessed;

	private double requestedBw =0;

	public Transmission(Packet pkt) {
		this.pkt = pkt;
		this.amountToBeProcessed=pkt.getSize();
	}
	
	public Transmission(int origin, int destination, long size, int flowId, Request payload) {
		this(new Packet(origin, destination, size, flowId, payload));
	}
	
	public Transmission(int origin, int destination, long size, int flowId, Request payload, Packet encapsulatedPkt) {
		this(new Packet(origin, destination, size, flowId, payload, encapsulatedPkt));
	}
	
	public long getSize(){
		return amountToBeProcessed;
	}
	
	public Packet getPacket() {
		return pkt;
	}
	
	/**
	 * Sums some amount of data to the already transmitted data
	 * @param completed amount of data completed since last update
	 */
	public void addCompletedLength(long completed){
		amountToBeProcessed-=completed;
		if (amountToBeProcessed<=0) amountToBeProcessed = 0;
	}
	
	/**
	 * Say if the Package transmission finished or not.
	 * @return true if transmission finished; false otherwise
	 */
	public boolean isCompleted(){
		return amountToBeProcessed==0;
	}
	
	public String toString() {
		return "Transmission:"+this.pkt.toString();
	}

	public void setRequestedBW(double bw) {
		this.requestedBw = bw;
	}
	public double getExpectedDuration() {
		double time = Double.POSITIVE_INFINITY;
		if(requestedBw != 0)
			time = pkt.getSize() / requestedBw;
		return time;
	}

	@Override
	public double getExpectedTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getServeTime() {
		return getPacket().getFinishTime() - getPacket().getStartTime();
	}

	@Override
	public double getStartTime() {
		return getPacket().getStartTime();
	}

	@Override
	public double getFinishTime() {
		return getPacket().getFinishTime();
	}

	@Override
	public void setStartTime(double currentTime) {
		getPacket().setPacketStartTime(currentTime);
	}

	@Override
	public void setFinishTime(double currentTime) {
		getPacket().setPacketFinishTime(currentTime);		
	}

	@Override
	public void setFailedTime(double currentTime) {
		getPacket().setPacketFailedTime(currentTime);
	}
}
