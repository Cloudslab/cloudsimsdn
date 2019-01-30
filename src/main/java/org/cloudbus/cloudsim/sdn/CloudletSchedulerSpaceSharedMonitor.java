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
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.ResCloudlet;

public class CloudletSchedulerSpaceSharedMonitor extends CloudletSchedulerSpaceShared implements CloudletSchedulerMonitor {
	// For monitoring
	private double prevMonitoredTime = 0;
	private double timeoutLimit = Double.POSITIVE_INFINITY;
	
	public CloudletSchedulerSpaceSharedMonitor(double timeOut) {
		super();
		this.timeoutLimit = timeOut;
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
			{
				List<ResCloudlet> timeoutCloudlet = new ArrayList<ResCloudlet>();
				for (ResCloudlet rcl : getCloudletExecList()) {
					if(rcl.getCloudletArrivalTime() < timeout) {
						rcl.setCloudletStatus(Cloudlet.FAILED);
						rcl.finalizeCloudlet();
						timeoutCloudlet.add(rcl);
						usedPes -= rcl.getNumberOfPes();					
					}
				}
				getCloudletExecList().removeAll(timeoutCloudlet);
				getCloudletFailedList().addAll(timeoutCloudlet);
			}
			{			
				List<ResCloudlet> timeoutCloudlet = new ArrayList<ResCloudlet>();
				for (ResCloudlet rcl : getCloudletWaitingList()) {
					if(rcl.getCloudletArrivalTime() < timeout) {
						rcl.setCloudletStatus(Cloudlet.FAILED);
						rcl.finalizeCloudlet();
						timeoutCloudlet.add(rcl);
					}
				}
				getCloudletWaitingList().removeAll(timeoutCloudlet);
				getCloudletFailedList().addAll(timeoutCloudlet);
			}
			
		}
	}

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
	
	
	public int getNumAllCloudlets() {
		return super.cloudletExecList.size() + super.cloudletFailedList.size() + super.getCloudletFinishedList().size() +
				super.cloudletPausedList.size() + super.cloudletWaitingList.size();
	}
}
