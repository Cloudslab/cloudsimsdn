/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.ResCloudlet;

public class CloudletSchedulerTimeSharedMonitor extends CloudletSchedulerTimeShared implements CloudletSchedulerMonitor {
	private double timeoutLimit = Double.POSITIVE_INFINITY;
	// For monitoring
	private double prevMonitoredTime = 0;
	private double vmMips = 0;
	
	
	public CloudletSchedulerTimeSharedMonitor(long vmMipsPerPE, double timeout) {
		vmMips = vmMipsPerPE;
		timeoutLimit = timeout;
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

	@Override
	public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
		double ret = super.updateVmProcessing(currentTime, mipsShare);
		processTimeout(currentTime);
		return ret;
	}
	
	@Override
	public List<Cloudlet> getFailedCloudlet() {
		List<Cloudlet> failed = new ArrayList<Cloudlet>();
		for(ResCloudlet cl:getCloudletFailedList()) {
			failed.add(cl.getCloudlet());
		}
		getCloudletFailedList().clear();
		return failed;
	}

	protected void processTimeout(double currentTime) {
		// Check if any cloudlet is timed out.
		if(timeoutLimit > 0 && Double.isFinite(timeoutLimit)) {
			double timeout = currentTime - this.timeoutLimit;
			List<ResCloudlet> timeoutCloudlet = new ArrayList<ResCloudlet>();
			
			for (ResCloudlet rcl : getCloudletExecList()) {
				if(rcl.getCloudletArrivalTime() < timeout) {
					timeoutCloudlet.add(rcl);
				}
			}
			getCloudletFailedList().addAll(timeoutCloudlet);
			getCloudletExecList().removeAll(timeoutCloudlet);			
		}
		
	}	
}
