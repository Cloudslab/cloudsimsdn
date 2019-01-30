package org.cloudbus.cloudsim.sdn;

import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;
import org.cloudbus.cloudsim.sdn.workload.Transmission;
/**
 * Network packet scheduler implementing time shared approach.
 * Total physical bandwidth will be allocated to the first transmission in full. 
 * Once the 1st transmission is completed, the 2nd transmission will get the full bandwidth, and so on.
 * 
 * @author Jungmin Jay Son
 * @since CloudSimSDN 3.0
 */

public class PacketSchedulerTimeShared extends PacketSchedulerSpaceShared {
	public PacketSchedulerTimeShared(Channel ch) {
		super(ch);
	}
	
	@Override
	public long updatePacketProcessing() {
		double currentTime = CloudSim.clock();
		double timeSpent = currentTime - this.previousTime;//NetworkOperatingSystem.round(currentTime - this.previousTime);
		
		if(timeSpent <= 0 || this.getInTransmissionNum() == 0)
			return 0;	// Nothing changed

		//update the amount of transmission 
		long processedThisRound =  Math.round(timeSpent * channel.getAllocatedBandwidth());
		
		//update transmission table; remove finished transmission
		Transmission transmission = inTransmission.get(0);
		transmission.addCompletedLength(processedThisRound);
		
		if (transmission.isCompleted()){
			this.completed.add(transmission);
			this.inTransmission.remove(transmission);
		}
		
		previousTime = currentTime;

		//Log.printLine(CloudSim.clock() + ": Channel.updatePacketProcessing() ("+this.toString()+"):Time spent:"+timeSpent+
		//		", BW/host:"+channel.getAllocatedBandwidth()+", Processed:"+processedThisRound);
		
		List<Transmission> timeoutTransmission = getTimeoutTransmissions();
		this.timeoutTransmission.addAll(timeoutTransmission);
		this.inTransmission.removeAll(timeoutTransmission);
		
		return processedThisRound;
	}

	// The earliest finish time among all transmissions in this channel 
	@Override
	public double nextFinishTime() {
		//now, predicts delay to next transmission completion
		double delay = Double.POSITIVE_INFINITY;

		Transmission transmission = this.inTransmission.get(0);
		double eft = estimateFinishTime(transmission);
		if (eft<delay)
			delay = eft;
		
		if(delay == Double.POSITIVE_INFINITY) {
			return delay;
		}
		else if(Math.abs(delay) < CloudSim.getMinTimeBetweenEvents()) {
			return CloudSim.getMinTimeBetweenEvents();
		}
		if(delay < 0) {
			throw new RuntimeException("Channel.nextFinishTime: delay: "+delay);
			//System.err.println("Channel.nextFinishTime: delay is minus: "+delay);
		}
		return delay;
	}
	
	// Estimated finish time of one transmission
	@Override
	public double estimateFinishTime(Transmission t) {
		double bw = channel.getAllocatedBandwidth();
		
		if(bw == 0) {
			return Double.POSITIVE_INFINITY;
		}
		
		if(bw < 0 || t.getSize() < 0) {
			throw new RuntimeException("PacketSchedulerTimeShared.estimateFinishTime(): Channel:"+channel+", BW: "+bw + ", Transmission:"+t+", Tr Size:"+t.getSize());
		}
		
		double eft= (double)t.getSize()/bw;
		return eft;
	}
}
