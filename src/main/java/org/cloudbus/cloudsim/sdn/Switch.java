/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;


/**
 * This represents switches that maintain routing information.
 * Note that all traffic estimation is calculated within NOS class, not in Switch class.
 * Energy consumption of Switch is calculated in this class by utilization history.
 * 
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Switch implements Node{
	protected int address;
	protected String name;
	
	protected long bw;
	protected long iops;
	protected int rank = -1;
	
	protected NetworkOperatingSystem nos;
	
	protected ArrayList<Link> links = new ArrayList<Link>();

	protected ForwardingRule forwardingTable;
	protected RoutingTable routingTable;	
	
	public Switch(String name, long bw, long iops, int upports, int downports, NetworkOperatingSystem nos) {
		address = NodeUtil.assignAddress();
		
		this.name = name;
		this.bw = bw;
		this.iops = iops;
		this.nos=nos;
		
		this.forwardingTable = new ForwardingRule();
		this.routingTable = new RoutingTable();
	}
	

	public void addLink(Link l){
		this.links.add(l);
	}

	/******* Routeable interface implementation methods ******/
	
	@Override
	public int getAddress() {
		return address;
	}
	
	@Override
	public long getBandwidth() {
		return bw;
	}
	
	@Override
	public void clearVMRoutingTable(){
		this.forwardingTable.clear();
	}
	
	@Override
	public void addVMRoute(int src, int dest, int flowId, Node to){
		this.forwardingTable.addRule(src, dest, flowId, to);
	}
	
	@Override
	public Node getVMRoute(int src, int dest, int flowId){
		Node route= this.forwardingTable.getRoute(src, dest, flowId);
		if(route == null) {
			this.printVMRoute();
			System.err.println("Switch.getVMRoute() ERROR: Cannot find route:" + 
					NetworkOperatingSystem.debugVmIdName.get(src) + "->"+
					NetworkOperatingSystem.debugVmIdName.get(dest) + ", flow ="+flowId);
		}
			
		return route;
	}
	
	@Override
	public void removeVMRoute(int src, int dest, int flowId){
		forwardingTable.removeRule(src, dest, flowId);
	}

	@Override
	public void setRank(int rank) {
		this.rank = rank;
	}

	@Override
	public int getRank() {
		return rank;
	}
	
	@Override
	public void printVMRoute() {
		forwardingTable.printForwardingTable(getName());
	}
	
	public String toString() {
		return "Switch: "+this.getName();
	}
	
	public String getName() {
		return name;
	}

	@Override
	public void addRoute(Node destHost, Link to) {
		this.routingTable.addRoute(destHost, to);
		
	}
	@Override
	public List<Link> getRoute(Node destHost) {
		return this.routingTable.getRoute(destHost);
	}
	@Override
	public RoutingTable getRoutingTable() {
		return this.routingTable;
	}


	/************************************************
	 *  Calculate Utilization history
	 ************************************************/
	private List<HistoryEntry> utilizationHistories = null;
	private static double powerOffDuration = 0; //if switch was idle for 1 hours, it's turned off.
	
	/* based on CARPO: Correlation-Aware Power Optimization in Data Center Networks by Xiaodong Wang et al. */
	private static double POWER_CONSUMPTION_IDLE = 66.7;
	private static double POWER_CONSUMPTION_PER_ACTIVE_PORT = 1; 
	

	public class HistoryEntry {
		public double startTime;
		public int numActivePorts;
		HistoryEntry(double t, int n) { startTime=t; numActivePorts=n;}
	}
	public List<HistoryEntry> getUtilizationHisotry() {
		return utilizationHistories;
	}
	
	public double getUtilizationEnergyConsumption() {
		
		double total=0;
		double lastTime=0;
		int lastPort=0;
		if(this.utilizationHistories == null)
			return 0;
		
		double logTime = 0, logPower =0;
		
		for(HistoryEntry h:this.utilizationHistories) {
			double duration = h.startTime - lastTime;
			double power = calculatePower(lastPort);
			double energyConsumption = power * duration /3600; // transform to Whatt*hour from What*seconds
			
			// Assume that the host is turned off when duration is long enough
			if(duration > powerOffDuration && lastPort == 0)
				energyConsumption = 0;
			
			total += energyConsumption;
			lastTime = h.startTime;
			lastPort = h.numActivePorts;

			// Cut every interval
			logPower += energyConsumption;			
			if(h.startTime >= logTime + Configuration.monitoringTimeInterval) {
				logTime +=Configuration.monitoringTimeInterval;
				LogWriter logEnergy = LogWriter.getLogger("sw_energy.csv");
				logEnergy.printLine(this.getName()+","+logTime+","+logPower);
				logPower = 0;
			}
		}
		return total;	
	}
	public void updateNetworkUtilization() {
		this.addUtilizationEntry();
	}

	public void addUtilizationEntryTermination(double finishTime) {
		if(this.utilizationHistories != null)
			this.utilizationHistories.add(new HistoryEntry(finishTime, 0));		
	}

	private void addUtilizationEntry() {
		double time = CloudSim.clock();
		int totalActivePorts = getTotalActivePorts();
		if(utilizationHistories == null)
			utilizationHistories = new ArrayList<HistoryEntry>();
		else {
			HistoryEntry hist = this.utilizationHistories.get(this.utilizationHistories.size()-1);
			if(hist.numActivePorts == totalActivePorts) {
				return;
			}
		}		
		this.utilizationHistories.add(new HistoryEntry(time, totalActivePorts));
	}
	private double calculatePower(int numActivePort) {
		double power = POWER_CONSUMPTION_IDLE + POWER_CONSUMPTION_PER_ACTIVE_PORT * numActivePort;
		return power;
	}
	private int getTotalActivePorts() {
		int num = 0;
		for(Link l:this.links) {
			if(l.isActive())
				num++;
		}
		return num;
	}
}
