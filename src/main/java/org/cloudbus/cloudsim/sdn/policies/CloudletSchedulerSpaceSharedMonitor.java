/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import java.util.List;

import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.ResCloudlet;

public class CloudletSchedulerSpaceSharedMonitor  extends CloudletSchedulerSpaceShared implements CloudletSchedulerMonitor {
	// For monitoring
	private double prevMonitoredTime = 0;
	
	@Override
	public long getTotalProcessingPreviousTime(double currentTime, List<Double> mipsShare) {
		long totalProcessedMIs = 0;
		double timeSpent = currentTime - prevMonitoredTime;
		double capacity = getCapacity(mipsShare);
		
		for (ResCloudlet rcl : getCloudletExecList()) {
			totalProcessedMIs += (long) (capacity * timeSpent * rcl.getNumberOfPes() * Consts.MILLION);
		}
		
		prevMonitoredTime = currentTime;
		return totalProcessedMIs;
	}

	protected double getCapacity(List<Double> mipsShare) {
		double capacity = 0.0;
		int cpus = 0;
		for (Double mips : mipsShare) {
			capacity += mips;
			if (mips > 0.0) {
				cpus++;
			}
		}
		capacity /= cpus;
		return capacity;
	}

	@Override
	public boolean isVmIdle() {
		if(runningCloudlets() > 0)
			return false;
		if(getCloudletWaitingList().size() > 0)
			return false;
		return true;
	}

	@Override
	public double getTimeSpentPreviousMonitoredTime(double currentTime) {
		double timeSpent = currentTime - prevMonitoredTime;
		return timeSpent;
	}

	@Override
	public int getCloudletTotalPesRequested() {
		return getCurrentMipsShare().size();
	}

}
