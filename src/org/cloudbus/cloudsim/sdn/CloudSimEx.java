/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */
 
 package org.cloudbus.cloudsim.sdn;

import java.util.Iterator;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

public class CloudSimEx extends CloudSim {
	private static long startTime;
	
	private static void setStartTimeMillis(long startedTime) {
		startTime=startedTime;
	}
	public static void setStartTime() {
		setStartTimeMillis(System.currentTimeMillis());
	}
	
	public static long getElapsedTimeSec() {
		long currentTime = System.currentTimeMillis();
		long elapsedTime = currentTime - startTime;
		elapsedTime /= 1000;
		
		return elapsedTime;
	}
	public static String getElapsedTimeString() {
		String ret ="";
		long elapsedTime = getElapsedTimeSec();
		ret = ""+elapsedTime/3600+":"+ (elapsedTime/60)%60+ ":"+elapsedTime%60;
		
		return ret;
	}
	
	public static int getNumFutureEvents() {
		return future.size();
	}
	
	public static double getNextEventTime() {
		Iterator<SimEvent> fit = future.iterator();
		SimEvent first = fit.next();
		if(first != null)
			return first.eventTime();
		
		return -1;
	}
}
