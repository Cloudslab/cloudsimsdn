/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationEnergyModelHostLinear;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMonitor;
import org.cloudbus.cloudsim.sdn.policies.VmSchedulerTimeSharedOverSubscriptionDynamicVM;


/**
 * Extended class of Host to support SDN.
 * Added function includes data transmission after completion of Cloudlet compute processing.
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class SDNHost extends Host implements Node {
		
	//Hashtable<Integer,Vm> vms;
	private Hashtable<Integer,Middlebox> middleboxes;
	private ForwardingRule forwardingTable;
	private RoutingTable routingTable;
	private int rank = -1;
	
	private List<Link> links = new ArrayList<Link>();

	public SDNHost(
			RamProvisioner ramProvisioner,
			BwProvisioner bwProvisioner,
			long storage,
			List<? extends Pe> peList,
			VmScheduler vmScheduler,
			NetworkOperatingSystem nos){
		super(NodeUtil.assignAddress(), ramProvisioner, bwProvisioner, storage,peList,vmScheduler);
			
		//this.vms = new Hashtable<Integer,Vm>();
		this.middleboxes = new Hashtable<Integer, Middlebox>();
		this.forwardingTable = new ForwardingRule();
		this.routingTable = new RoutingTable();
	}
	
	/**
	 * Requests updating of processing of cloudlets in the VMs running in this host.
	 * 
	 * @param currentTime the current time
	 * @return expected time of completion of the next cloudlet in all VMs in this host.
	 *         Double.MAX_VALUE if there is no future events expected in this host
	 * @pre currentTime >= 0.0
	 * @post $none
	 */
	public double updateVmsProcessing(double currentTime) {
		double smallerTime = Double.MAX_VALUE;
		
		// Update VM's processing for the previous time.
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			List<Double> mipsAllocated = getVmScheduler().getAllocatedMipsForVm(vm);
			
//			System.err.println(CloudSim.clock()+":"+vm + " is allocated: "+ mipsAllocated);
			vm.updateVmProcessing(currentTime, mipsAllocated);
		}

		// Change MIPS share proportion depending on the remaining Cloudlets.
		adjustMipsShare();
		
		// Check the next event time based on the updated MIPS share proportion 
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			List<Double> mipsAllocatedAfter = getVmScheduler().getAllocatedMipsForVm(vm);

//			System.err.println(CloudSim.clock()+":"+vm + " is reallocated: "+ mipsAllocatedAfter);
			double time = vm.updateVmProcessing(currentTime, mipsAllocatedAfter);
			
			if (time > 0.0 && time < smallerTime) {
				smallerTime = time;
			}
		}

		return smallerTime;
	}
	
	public void adjustMipsShare() {
		if(getVmScheduler() instanceof VmSchedulerTimeSharedOverSubscriptionDynamicVM){
			VmSchedulerTimeSharedOverSubscriptionDynamicVM sch = (VmSchedulerTimeSharedOverSubscriptionDynamicVM) getVmScheduler();
			double scaleFactor = sch.redistributeMipsDueToOverSubscriptionDynamic();

			logOverloadLogger(scaleFactor);
			for (SDNVm vm : this.<SDNVm>getVmList()) {
				vm.logOverloadLogger(scaleFactor);
			}
		}
	}
	
	// Check how long this Host is overloaded (The served capacity is less than the required capacity)
	private double overloadLoggerPrevTime =0;
	private double overloadLoggerPrevScaleFactor= 1.0;
	private double overloadLoggerTotalDuration =0;
	private double overloadLoggerOverloadedDuration =0;
	private double overloadLoggerScaledOverloadedDuration =0;

	private void logOverloadLogger(double scaleFactor) {
		// scaleFactor == 1 means enough resource is served
		// scaleFactor < 1 means less resource is served (only requested * scaleFactor is served) 
		double currentTime = CloudSim.clock();
		double duration = currentTime - overloadLoggerPrevTime;
		
		if(scaleFactor > 1) {
			System.err.println("scale factor cannot be >1!");
			System.exit(1);
		}
		
		if(duration > 0) {
			if(overloadLoggerPrevScaleFactor < 1.0) {
				// Host was overloaded for the previous time period
				overloadLoggerOverloadedDuration += duration;
			}
			overloadLoggerTotalDuration += duration;
			overloadLoggerScaledOverloadedDuration += duration * overloadLoggerPrevScaleFactor;
			updateOverloadMonitor(currentTime, overloadLoggerPrevScaleFactor);
		}				
		overloadLoggerPrevTime = currentTime;
		overloadLoggerPrevScaleFactor = scaleFactor;		
	}
	
	public double overloadLoggerGetOverloadedDuration() {
		return overloadLoggerOverloadedDuration;
	}
	public double overloadLoggerGetTotalDuration() {
		return overloadLoggerTotalDuration;
	}
	public double overloadLoggerGetScaledOverloadedDuration() {
		return overloadLoggerScaledOverloadedDuration;
	}
	
	public double overloadLoggerGetOverloadedDurationVM() {
		double total = 0;
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			total += vm.overloadLoggerGetOverloadedDuration();
		}
		return total;
	}
	public double overloadLoggerGetTotalDurationVM() {
		double total = 0;
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			total += vm.overloadLoggerGetTotalDuration();
		}
		return total;
	}
	public double overloadLoggerGetScaledOverloadedDurationVM() {
		double total = 0;
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			total += vm.overloadLoggerGetScaledOverloadedDuration();
		}
		return total;
	}
	
	// For monitor
	private MonitoringValues mvOverload = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);

	private void updateOverloadMonitor(double logTime, double scaleFactor) {
		double scaleReverse = (scaleFactor != 0 ? 1/scaleFactor : Float.POSITIVE_INFINITY);
		mvOverload.add(scaleReverse, logTime);
	}

	public MonitoringValues getMonitoringValuesOverloadMonitor() { 
		return mvOverload;
	}
	//////////////////////////////////////////////////////
	
	public void addMiddlebox(Middlebox m){
		middleboxes.put(m.getId(), m);
		vmCreate(m.getVm());
	}

	public Vm getVm(int vmId) {
		for (Vm vm : getVmList()) {
			if (vm.getId() == vmId) {
				return vm;
			}
		}
		return null;
	}
	
	public boolean isSuitableForVm(Vm vm) {
		if (getStorage() < vm.getSize()) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by storage");
			return false;
		}

		if (!getRamProvisioner().isSuitableForVm(vm, vm.getCurrentRequestedRam())) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by RAM");
			return false;
		}

		if (!getBwProvisioner().isSuitableForVm(vm, vm.getBw())) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by BW");
			return false;
		}

//		if (!getVmScheduler().allocatePesForVm(vm, vm.getCurrentRequestedMips())) {
//			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
//					+ " failed by MIPS");
//			return false;
//		}
		return true;
	}
	public boolean processPacketForMiddlebox(Packet data) {
		int vmId = data.getDestination();
		if (middleboxes.containsKey(vmId)){//Try to deliver package to a hosted middlebox
			Request req = data.getPayload();
			Middlebox m = middleboxes.get(vmId);
			m.submitRequest(req);
			
			return true;
		}
		return false;
	}

	/******* Routeable interface implementation methods ******/

	@Override
	public int getAddress() {
		return super.getId();
	}
	
	@Override
	public long getBandwidth() {
		return getBw();
	}
	
	public long getAvailableBandwidth() {
		return getBwProvisioner().getAvailableBw();
	}

	@Override
	public void clearVMRoutingTable(){
		this.forwardingTable.clear();
	}

	@Override
	public void addVMRoute(int src, int dest, int flowId, Node to){
		forwardingTable.addRule(src, dest, flowId, to);
	}
	
	@Override
	public Node getVMRoute(int src, int dest, int flowId){
		Node route= this.forwardingTable.getRoute(src, dest, flowId);
		if(route == null) {
			this.printVMRoute();
			System.err.println(toString()+" getVMRoute(): ERROR: Cannot find route:" + src + "->"+dest + ", flow ="+flowId);
		}
			
		return route;
	}
	
	@Override
	public void removeVMRoute(int src, int dest, int flowId){
		forwardingTable.removeRule(src, dest, flowId);
	}

	@Override
	public void setRank(int rank) {
		this.rank=rank;
	}

	@Override
	public int getRank() {
		return rank;
	}
	
	@Override
	public void printVMRoute() {
		forwardingTable.printForwardingTable(getName());
	}
	
	private String getName() {
		return "SDNHost "+getId();
	}

	public String toString() {
		return this.getName();
	}

	@Override
	public void addLink(Link l) {
		links.add(l);
	}

	@Override
	public void updateNetworkUtilization() {
		// TODO Auto-generated method stub
		
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

	// For monitor
	private MonitoringValues mv = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
	private long monitoringProcessedMIsPerUnit = 0;
	
	private PowerUtilizationMonitor powerMonitor = new PowerUtilizationMonitor(new PowerUtilizationEnergyModelHostLinear());
	public double getConsumedEnergy() {
		return powerMonitor.getTotalEnergyConsumed();
	}
	
	public void updateMonitor(double logTime, double timeUnit) {
		long capacity = (long) (this.getTotalMips() *timeUnit);
		double utilization = (double)monitoringProcessedMIsPerUnit / capacity / Consts.MILLION;
		mv.add(utilization, logTime);
		
		monitoringProcessedMIsPerUnit = 0;
		
		LogWriter log = LogWriter.getLogger("host_utilization.csv");
		log.printLine(this.getName()+","+logTime+","+utilization);
		
		double energy = powerMonitor.addPowerConsumption(logTime, utilization);
		LogWriter logEnergy = LogWriter.getLogger("host_energy.csv");
		logEnergy.printLine(this.getName()+","+logTime+","+energy);
		
		// Also update hosting VMs in this machine
		updateVmMonitor(timeUnit);
	}

	private void updateVmMonitor(double timeUnit) {
		for(Vm vm: getVmList()) {
			SDNVm tvm = (SDNVm)vm;
			tvm.updateMonitor(CloudSim.clock(), timeUnit);
		}
	}	
	
	public MonitoringValues getMonitoringValuesHostCPUUtilization() { 
		return mv;
	}

	public void increaseProcessedMIs(long processedMIs) {
//		System.err.println(this.toString() +","+ processedMIs);
		this.monitoringProcessedMIsPerUnit += processedMIs;
	}
	
	public MonitoringValues getMonitoringValuesHostBwUtilization() {
		if(links.size() != 1) {
			System.err.println(this+": Multiple links found!!");
		}
		
		if(links.size() > 0) {
			return links.get(0).getMonitoringValuesLinkUtilizationUp();
		}
		return null;
	}
}
