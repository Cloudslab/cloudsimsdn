/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import java.util.List;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.sdn.Configuration;

public class CloudletSchedulerTimeSharedMonitor extends CloudletSchedulerTimeShared implements CloudletSchedulerMonitor {
	// For monitoring
	private double prevMonitoredTime = 0;
	private double vmMips = 0;
	
	
	public CloudletSchedulerTimeSharedMonitor(long vmMipsPerPE) {
		vmMips = vmMipsPerPE;
	}

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
	
	@Override
	public double getTimeSpentPreviousMonitoredTime(double currentTime) {
		double timeSpent = currentTime - prevMonitoredTime;
		return timeSpent;
	}

	@Override
	public boolean isVmIdle() {
		if(runningCloudlets() > 0)
			return false;
		return true;
	}
	
	@Override
	public double getCapacity(List<Double> mipsShare) {
		double capacity = super.getCapacity(mipsShare);
		double maxPeCapacityPerCloudlet = vmMips * Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT;
		if(capacity > maxPeCapacityPerCloudlet) {
			capacity = maxPeCapacityPerCloudlet;
//			System.out.println("Capacity is limited to "+ capacity);
		}
		return capacity;
	}

	@Override
	public int getCloudletTotalPesRequested() {
		int pesInUse = 0;
		for (ResCloudlet rcl : getCloudletExecList()) {
			pesInUse += rcl.getNumberOfPes();
		}
		return pesInUse;
	}
}
