/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.physicalcomponents.switches;

import java.util.HashMap;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.LogWriter;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationEnergyModelSwitchActivePort;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMonitor;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.NodeUtil;
import org.cloudbus.cloudsim.sdn.physicalcomponents.RoutingTable;
import org.cloudbus.cloudsim.sdn.virtualcomponents.ForwardingRule;


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
		
	private HashMap<Node, Link> linkToNextHop = new HashMap<Node, Link>();

	protected ForwardingRule forwardingTable;
	protected RoutingTable routingTable;	
	
	public Switch(String name, long bw, long iops, int upports, int downports) {
		address = NodeUtil.assignAddress();
		
		this.name = name;
		this.bw = bw;
		this.iops = iops;

		this.forwardingTable = new ForwardingRule();
		this.routingTable = new RoutingTable();
	}
	

	public void addLink(Link l) {
		this.linkToNextHop.put(l.getOtherNode(this), l);
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
			System.err.println("Switch.getVMRoute() No route (might be due to error, or dynamic route updating..):" + 
					NetworkOperatingSystem.getVmName(src) + "->"+
					NetworkOperatingSystem.getVmName(dest) + ", flow ="+flowId);
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

	/*********************************************
	 * 
	 *********************************************/
	private long lastActivePortNum = 0;
	private double lastTotalEnergy = 0; 
	
	private PowerUtilizationMonitor powerMonitor = new PowerUtilizationMonitor(new PowerUtilizationEnergyModelSwitchActivePort());
	public double getConsumedEnergy() {
		return powerMonitor.getTotalEnergyConsumed();
	}
	
	public void updateMonitor(double logTime, double timeUnit) {
		updateNetworkUtilization(false); //force update.
		
		double totalEnergy = powerMonitor.getTotalEnergyConsumed();
		double energyPerTimeUnit = totalEnergy - lastTotalEnergy;
		
		LogWriter logEnergy = LogWriter.getLogger("sw_energy.csv");
		logEnergy.printLine(this.getName()+","+logTime+","+energyPerTimeUnit);
		lastTotalEnergy = totalEnergy;
	}
	
	public void updateNetworkUtilization() {
		updateNetworkUtilization(true);
	}
	
	public void updateNetworkUtilization(boolean enableSkipSamePort) {
		int currentPortNum = getCurrentActivePorts();
		if(enableSkipSamePort && lastActivePortNum == currentPortNum)
			return;
		
		double currentTime = CloudSim.clock();
		powerMonitor.addPowerConsumption(currentTime, lastActivePortNum);
		lastActivePortNum = currentPortNum;
	}
	
	private int getCurrentActivePorts() {
		int num = 0;
		for(Link l:linkToNextHop.values()) {
			if(l.isActive())
				num++;
		}
		return num;
	}


	@Override
	public Link getLinkTo(Node nextHop) {
		return this.linkToNextHop.get(nextHop);
	}
}
