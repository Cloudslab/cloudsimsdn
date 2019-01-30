package org.cloudbus.cloudsim.sdn;

import java.util.LinkedList;

import org.cloudbus.cloudsim.sdn.workload.Transmission;

/**
 * Network packet scheduler interface which implements the simulation of network packet processing.
 * For example, if a physical link is shared by 5 network transmissions, the bandwidth can be shared equally (1/5) by each transmission,
 * or it can be allocated to the first flow in full until the transmission is completed, and then allocated to the second flow in full, etc.  
 * 
 * @author Jungmin Jay Son
 * @since CloudSimSDN 3.0
 */
public interface PacketScheduler {
	/**
	 * Calculate and update network transmission for the past time period
	 * 
	 * @return true if any transmission is completed in this round, false if no transmission is completed in this round.
	 */
	public long updatePacketProcessing();

	/**
	 * Calculate the next finish time of any transmissions.
	 *  
	 * @return the earliest next finish time of any transmission.
	 */
	public double nextFinishTime();

	/**
	 * Calculate the estimated finish of a specific transmission
	 * 
	 * @param t a transmission to calculate the estimated finish time.
	 * @return the estimated finish time of the transmission.
	 */
	public double estimateFinishTime(Transmission t);
	
	public double addTransmission(Transmission transmission);
	public void removeTransmission(Transmission transmission);
	
	public int getInTransmissionNum();
	
	public void setTimeOut(double timeoutSecond);
	public LinkedList<Transmission> getTimedOutTransmission();
	public void resetTimedOutTransmission();

	public LinkedList<Transmission> getCompletedTransmission();
	public void resetCompletedTransmission();
	
}
