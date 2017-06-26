/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.monitor.power;

/**
 *  To log utilization history, this class holds power utilization information
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class PowerUtilizationHistoryEntry {
	public float startTime;
	public float utilPercentage;
	public PowerUtilizationHistoryEntry(double t, double percentage) { startTime=(float)t; utilPercentage=(float)percentage;}
}
