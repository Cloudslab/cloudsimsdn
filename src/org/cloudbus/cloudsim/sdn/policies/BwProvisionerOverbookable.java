/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;

/**
 * BwProvisionerSimple is a class that implements a simple best effort allocation policy: if there
 * is bw available to request, it allocates; otherwise, it fails.
 * 
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class BwProvisionerOverbookable extends BwProvisionerSimple {
	private static final double overbookingRatioBw = 4.0;	// 20% overbooking allowed for BW

	public BwProvisionerOverbookable(long bw) {
		super(bw);
		setAvailableBw((long) getOverbookableBw(bw));	//overwrite available BW to overbookable BW
	}


	@Override
	public void deallocateBwForAllVms() {
		super.deallocateBwForAllVms();
		
		setAvailableBw((long) getOverbookableBw(getBw()));	//Overbooking
		getBwTable().clear();
	}

	private static double getOverbookableBw(long capacity) {
		double overbookedBw = capacity * overbookingRatioBw;
		return overbookedBw;		
	}

}
