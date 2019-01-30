package org.cloudbus.cloudsim.sdn;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;
import org.cloudbus.cloudsim.sdn.workload.Transmission;
/**
 * Network packet scheduler implementing space shared approach.
 * Physical bandwidth is shared equally by the number of transmissions.
 * For example, 1000 Byte/sec shared by 5 transmissions: each transmission will get 200 B/sec.
 * 
 * @author Jungmin Jay Son
 * @since CloudSimSDN 3.0
 */
public class PacketSchedulerSpaceShared implements PacketScheduler {
	protected LinkedList<Transmission> inTransmission;
	protected LinkedList<Transmission> completed;
	protected LinkedList<Transmission> timeoutTransmission;
	protected double previousTime;
	
	protected double timeoutLimit = Double.POSITIVE_INFINITY;	// INFINITE = Never timeout
	
	protected Channel channel;
	
	public PacketSchedulerSpaceShared(Channel ch) {
		this.channel = ch;
		this.inTransmission = new LinkedList<Transmission>();
		this.completed = new LinkedList<Transmission>();		
		this.timeoutTransmission = new LinkedList<Transmission>();
	}

	
	/* This function processes network transmission for the past time period.
	 * Return: True if any transmission is completed in this round.
	 *         False if no transmission is completed in this round.
	 */	
	@Override
	public long updatePacketProcessing() {
		double currentTime = CloudSim.clock();
		double timeSpent = currentTime - this.previousTime;//NetworkOperatingSystem.round(currentTime - this.previousTime);
		
		if(timeSpent <= 0 || this.getInTransmissionNum() == 0)
			return 0;	// Nothing changed

		//update the amount of transmission 
		long processedThisRound =  Math.round(timeSpent * getAllocatedBandwidthPerTransmission());
		long processedTotal = processedThisRound * inTransmission.size();
		
		//update transmission table; remove finished transmission
		List<Transmission> completedTransmissions = new ArrayList<Transmission>();
		for(Transmission transmission: inTransmission){
			transmission.addCompletedLength(processedThisRound);
			
			if (transmission.isCompleted()){
				completedTransmissions.add(transmission);
				//this.completed.add(transmission);
			}	
		}
		
		this.completed.addAll(completedTransmissions);
		this.inTransmission.removeAll(completedTransmissions);
		previousTime=currentTime;

		List<Transmission> timeoutTransmission = getTimeoutTransmissions();
		this.timeoutTransmission.addAll(timeoutTransmission);
		this.inTransmission.removeAll(timeoutTransmission);
		
		//Log.printLine(CloudSim.clock() + ": Channel.updatePacketProcessing() ("+this.toString()+"):Time spent:"+timeSpent+
		//		", BW/host:"+getAllocatedBandwidthPerTransmission()+", Processed:"+processedThisRound);
		return processedTotal;
	}
	/**
	 * Adds a new Transmission to be submitted via this Channel
	 * @param transmission transmission initiating
	 * @return estimated delay to complete this transmission
	 * 
	 */
	@Override
	public double addTransmission(Transmission transmission){
		if (this.inTransmission.isEmpty()) 
			previousTime=CloudSim.clock();
		
		this.inTransmission.add(transmission);
		double eft = estimateFinishTime(transmission);

		return eft;
	}

	/**
	 * Remove a transmission submitted to this Channel
	 * @param transmission to be removed
	 * 
	 */
	@Override
	public void removeTransmission(Transmission transmission){
		inTransmission.remove(transmission);
	}

	/**
	 * @return list of Packets whose transmission finished, or empty
	 *         list if no packet arrived.
	 */
	@Override
	public LinkedList<Transmission> getCompletedTransmission(){
		LinkedList<Transmission> returnList = new LinkedList<Transmission>();

		if (!completed.isEmpty()){
			returnList.addAll(completed);
		}

		return returnList;
	}
	

	@Override
	public void resetCompletedTransmission() {
		completed = new LinkedList<Transmission>();
	}

	@Override
	public void setTimeOut(double timeoutSecond) {
		timeoutLimit = timeoutSecond;
	}

	@Override
	public void resetTimedOutTransmission() {
		timeoutTransmission = new LinkedList<Transmission>();
	}

	@Override
	public LinkedList<Transmission> getTimedOutTransmission() {
		LinkedList<Transmission> returnList = new LinkedList<Transmission>();

		if (!timeoutTransmission.isEmpty()){
			returnList.addAll(timeoutTransmission);
		}
		return returnList;
	}

	@Override
	public int getInTransmissionNum() {
		return this.inTransmission.size();
	}

	
	protected List<Transmission> getTimeoutTransmissions() {
		List<Transmission> timeoutTransmissions = new ArrayList<Transmission>();
		if(this.timeoutLimit != Double.POSITIVE_INFINITY) {
			double currentTime = CloudSim.clock();
			double startTimeLimit = currentTime - this.timeoutLimit;
			
			for(Transmission tr:inTransmission) {
				if(tr.getPacket().getStartTime() < startTimeLimit) {
					// This Tr is started before (current time - timeout)
					// Cannot complete.
					timeoutTransmissions.add(tr);
				}
			}
		}
		return timeoutTransmissions;
	}

	// The earliest finish time among all transmissions in this channel 
	@Override
	public double nextFinishTime() {
		//now, predicts delay to next transmission completion
		double delay = Double.POSITIVE_INFINITY;

		for (Transmission transmission:this.inTransmission){
			double eft = estimateFinishTime(transmission);
			if (eft<delay)
				delay = eft;
		}
		
		if(delay == Double.POSITIVE_INFINITY) {
			return delay;
		}
		else if(delay < 0) {
			throw new IllegalArgumentException("Channel.nextFinishTime: delay"+delay);
		}
		return delay;
	}
	
	// Estimated finish time of one transmission
	@Override
	public double estimateFinishTime(Transmission t) {
		double bw = getAllocatedBandwidthPerTransmission();
		
		if(bw == 0) {
			return Double.POSITIVE_INFINITY;
		}
		
		double eft= (double)t.getSize()/bw;
		return eft;
	}
	
	private double getAllocatedBandwidthPerTransmission() {
		// If this channel shares a link with another channel, this channel might not get the full BW from link.
		if(inTransmission.size() == 0) {
			return channel.getAllocatedBandwidth();
		}
		
		return channel.getAllocatedBandwidth()/inTransmission.size();
	}
}
