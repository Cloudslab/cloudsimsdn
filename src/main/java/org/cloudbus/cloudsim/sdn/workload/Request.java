/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.workload;

import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.sdn.Packet;


/**
 * Request class represents a message submitted to VM. Each request has a list of activities
 * that should be performed at the VM. (Processing and Transmission)
 *   
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Request {
	
	private long requestId;
	private int userId;
	private LinkedList<Activity> activities;
	private LinkedList<Activity> removedActivites;	//Logging purpose only
	
	private static long numRequests = 0;

	public Request(int userId){
		this.requestId = numRequests++;
		this.userId = userId;
		this.activities = new LinkedList<Activity>();		
		this.removedActivites = new LinkedList<Activity>();		
	}
	
	public long getRequestId(){
		return requestId;
	}
	
	public int getUserId(){
		return userId;
	}
		
	public boolean isFinished(){
		return (activities.size()==0);
	}
	
	public void addActivity(Activity act){
		activities.add(act);
	}
	
	public Activity getNextActivity(){
		if(activities.size() > 0) {
			Activity act = activities.get(0);
			return act;
		}
		return null;
	}
	
	public Activity getPrevActivity(){
		if(removedActivites.size() == 0)
			return null;
		
		Activity act = removedActivites.get(removedActivites.size()-1);
		return act;
	}
	
	public Transmission getNextTransmission() {
		for(Activity act:activities) {
			if(act instanceof Transmission)
				return (Transmission) act;
		}
		return null;
	}
	
	public Activity removeNextActivity(){
		Activity act = activities.remove(0);
		
		this.removedActivites.add(act);

		return act;
	}
	public String toString() {
		return "Request. UserID:"+ this.userId + ",Req ID:"+this.requestId;
	}
	
	public List<Activity> getRemovedActivities() {
		return this.removedActivites;
	}
	
	private Transmission getLastTransmission() {
		for(int i=activities.size()-1; i>=0; i--) {
			Activity act = activities.get(i);
			if(act instanceof Transmission)
				return (Transmission) act;
		}
		return null;
	}

	public Request getTerminalRequest() {
		// The request that processes at last.
		Transmission t= getLastTransmission();
		if(t == null)
			return this;
		
		Packet p = t.getPacket();
		Request lastReq = p.getPayload();
		return lastReq.getTerminalRequest();
	}
	
	public void setFailedTime(double time) {
		for(Activity ac:activities) {
			ac.setFailedTime(time);
		}
	}
}
