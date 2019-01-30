/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.workload;

/**
 * Activities that can be performed by VM. (Transmission or Processing)
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public interface Activity {
	public abstract double getExpectedTime();
	public abstract double getServeTime();
	public abstract double getStartTime();
	public abstract double getFinishTime();
	public abstract void setStartTime(double currentTime);
	public abstract void setFinishTime(double currentTime);
	public abstract void setFailedTime(double currentTime);
	
}
