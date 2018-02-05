/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;


/**
 * Network data packet to transfer from source to destination.
 * Payload of Packet will have a list of activities. 
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Packet {
	
	final int origin;			// origin VM adress (vm.getId())
	final int destination;	// destination VM adress (vm.getId())
	final long size;
	final int flowId;
	Request payload;

	private double startTime=-1;
	private double finishTime=-1;
	
	public Packet(int origin, int destination, long size, int flowId, Request payload) {
		this.origin = origin;
		this.destination = destination;
		this.size = size;
		this.flowId = flowId;
		this.payload = payload;
		
	}

	public int getOrigin() {
		return origin;
	}

	public int getDestination() {
		return destination;
	}

	public long getSize() {
		return size;
	}

	public Request getPayload() {
		return payload;
	}
	
	public int getFlowId() {
		return flowId;
	}
	
	public String toString() {
		return "PKG:"+origin + "->" + destination + " - " + payload.toString();
	}

	public void setStartTime(double time) {
		this.startTime = time;
	}
	public void setFinishTime(double time) {
		this.finishTime = time;
	}
	public double getStartTime() {
		return this.startTime;
	}
	public double getFinishTime() {
		if(finishTime < 0) {
			throw new IllegalArgumentException("Finish time is negative. This workload might not be completed yet!!"+this);
		}
		
		return this.finishTime;
	}
}
