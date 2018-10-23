/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.monitor.power;

public class PowerUtilizationEnergyModelHostLinear implements PowerUtilizationEnergyModel {
	
	private final static int idleWatt = 120;
	private final static int workingWattProportional = 154;
	private final static double powerOffDuration = 0; //3600 if host is idle for longer than 3600 sec (1hr), it's turned off.

	private double calculatePower(double u) {
		double power = (double)idleWatt + (double)workingWattProportional * u;
		return power;
	}

	@Override
	public double calculateEnergyConsumption(double duration, double utilization) {
		double power = calculatePower(utilization);
		double energyConsumption = power * duration;
		
		// Assume that the host is turned off when duration is long enough
		if(duration > powerOffDuration && utilization == 0)
			energyConsumption = 0;
				
		return energyConsumption / 3600;
	}
}
