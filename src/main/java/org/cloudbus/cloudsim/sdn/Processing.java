/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn;

import org.cloudbus.cloudsim.Cloudlet;

/**
 * Processing activity to compute in VM. Basically a wrapper of Cloudlet. 
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Processing implements Activity {

	long requestId;
	Cloudlet cl;
	
	private double vmMipsPerPE=0;
	
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
		double maxMipsForCloudlet = getMaxMipsForCloudlet();
		if(maxMipsForCloudlet != 0)
			time = cl.getCloudletTotalLength() / maxMipsForCloudlet;
		return time;
	}

	@Override
	public double getServeTime() {
		//return getCloudlet().getActualCPUTime();
		return getCloudlet().getFinishTime() - getCloudlet().getSubmissionTime();
	}
}