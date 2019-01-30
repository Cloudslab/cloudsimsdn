/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.workload;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.sdn.Configuration;

/**
 * CPU Processing activity to compute in VM. Basically a wrapper of Cloudlet. 
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Processing implements Activity {
	Cloudlet cl;
	double startTime = 0;
	double finishTime = 0;
	
	private double vmMipsPerPE=0;
	double maxMipsForCloudlet;
	public long cloudletTotalLength;

	public Processing(Cloudlet cl){
		this.cl=cl;
	}
	
	public Cloudlet getCloudlet(){
		return cl;
	}
	
	public void setVmMipsPerPE(double mips) {
		vmMipsPerPE = mips;
		
		//cl.setMaxMipsLimit(getMaxMipsForCloudlet());
	}
	
	public void clearCloudlet() {
		maxMipsForCloudlet = getMaxMipsForCloudlet();
		cloudletTotalLength = cl.getCloudletTotalLength();
		cl = null;
	}
	
	private double getMaxMipsForCloudlet() {
		double mipsPercent = Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT;
		/*
		long cloudletLen = cl.getCloudletLength();

		if(cloudletLen < vmMipsPerPE * 0.5) {
			mipsPercent = Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT[0];
		}
		else if(cloudletLen < vmMipsPerPE * 1.0) {
			mipsPercent = Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT[1];
		}
		else if(cloudletLen < vmMipsPerPE * 1.5) {
			mipsPercent = Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT[2];
		}
		else {
			mipsPercent = Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT[3];
		}
		*/
		
		return vmMipsPerPE * mipsPercent;
	}

	@Override	
	public double getExpectedTime() {
		double time = Double.POSITIVE_INFINITY;
		if(cl != null) {
			double maxMipsForCloudlet = getMaxMipsForCloudlet();
			if(maxMipsForCloudlet != 0)
				time = cl.getCloudletTotalLength() / maxMipsForCloudlet;
		}
		else if(this.maxMipsForCloudlet > 0) {
			time = this.cloudletTotalLength / this.maxMipsForCloudlet;
		}
		return time;
	}

	@Override
	public double getServeTime() {
		//return getCloudlet().getActualCPUTime();
		return finishTime - startTime;
	}
	
	public String toString() {
		if(cl != null)
			return "Processing:"+"VM="+cl.getVmId()+",Len="+cl.getCloudletLength();
		return "Processing:"+"Len="+this.cloudletTotalLength+",Start="+this.startTime + ",Finish="+this.finishTime;
	}

	@Override
	public double getStartTime() {
		return startTime;
	}

	@Override
	public double getFinishTime() {
		return finishTime;
	}

	@Override
	public void setStartTime(double currentTime) {
		startTime = currentTime;
		
	}

	@Override
	public void setFinishTime(double currentTime) {
		finishTime = currentTime;
	}
	
	@Override
	public void setFailedTime(double currentTime) {
		finishTime = currentTime;
	}
}