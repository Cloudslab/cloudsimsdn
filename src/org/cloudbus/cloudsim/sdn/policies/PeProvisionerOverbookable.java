/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;

/**
 * The Class PeProvisionerSimple.
 * 
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 2.0
 */
public class PeProvisionerOverbookable extends PeProvisionerSimple {
	public static final double overbookingRatioMips = 4.0;	// 10% overbooking allowed for MIPS

	public PeProvisionerOverbookable(double availableMips) {
		super(availableMips);
		setAvailableMips(PeProvisionerOverbookable.getOverbookableMips(availableMips));
	}

	@Override
	public void deallocateMipsForAllVms() {
		super.deallocateMipsForAllVms();
		
		setAvailableMips(PeProvisionerOverbookable.getOverbookableMips(getMips()));	//Overbooking
	}


	private static double getOverbookableMips(double availableMips) {
		double overbookedMips = availableMips * PeProvisionerOverbookable.overbookingRatioMips;
		return overbookedMips;		
	}

}
