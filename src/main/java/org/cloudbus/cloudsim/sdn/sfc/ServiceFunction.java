/*
 * Title:        CloudSimSDN + SFC
 * Description:  SFC extension for CloudSimSDN
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2018, The University of Melbourne, Australia
 */


package org.cloudbus.cloudsim.sdn.sfc;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

/**
 * ServiceFunction is a class to implement a VNF, which extends SDNVm including ServiceFunctionType and MI/operation. 
 * When one network packet passes through a ServiceFunction, it will take additional time here.
 * Additional processing time for a packet : MI per Operation / MIPS allocated for this SF (=VM)
 *
 * @author Jungmin Jay Son
 * @since CloudSimSDN 3.0
 */
public class ServiceFunction extends SDNVm {
	
	/** Initial MIPS for this SF. We retain this information for dynamic MIPS re-allocation. */
	private final double initMips;

	/** Initial Number of PEs for this SF. We retain this information for dynamic MIPS re-allocation. */
	private final int initNumberOfPes;

	/** NOS in charge of this SF in case of multiple DC / NOS running in the simulation */
	private NetworkOperatingSystem runningNOS;
	
	/** MI per operation. Additional process time will be calculated by mipOper / allocated MIPS */
	private long mipOper=0;

	public ServiceFunction(int id, int userId, double mips, int numberOfPes, int ram, long bw, long size, String vmm,
			CloudletScheduler cloudletScheduler, double startTime, double finishTime) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler, startTime, finishTime);
		initMips = mips;
		initNumberOfPes = numberOfPes;
	}

	public void setMIperOperation(long mipOperation) {
		this.mipOper = mipOperation; // MI per operation.
	}

	public long getMIperOperation() {
		return this.mipOper;
	}
	
	public double getInitialMips() {
		return initMips;
	}
	public int getInitialNumberOfPes() {
		return initNumberOfPes;
	}
	
	public void setNetworkOperatingSystem(NetworkOperatingSystem nos) {
		this.runningNOS = nos;
	}
	public NetworkOperatingSystem getNetworkOperatingSystem() {
		return this.runningNOS;
	}
}
