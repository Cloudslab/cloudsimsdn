/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.sdn.Constants;
import org.cloudbus.cloudsim.sdn.Request;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;

/**
 * Broker class for CloudSimSDN example. This class represents a broker (Service Provider)
 * who uses the Cloud data center.
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class SDNBroker extends SimEntity {

	public static int lastAppId = 0;
	
	private SDNDatacenter datacenter = null;
	private String applicationFileName = null;
	private HashMap<WorkloadParser, Integer> workloadId=null;
	private HashMap<Long, Workload> requestMap=null;
	private List<String> workloadFileNames=null;

//	private List<Cloudlet> cloudletList;
//	private List<Workload> workloads;
	
	public SDNBroker(String name) throws Exception {
		super(name);
		this.workloadFileNames = new ArrayList<String>();
//		this.cloudletList = new ArrayList<Cloudlet>();
//		this.workloads = new ArrayList<Workload>();
		workloadId = new HashMap<WorkloadParser, Integer>();
		requestMap = new HashMap<Long, Workload>();
	}
	
	@Override
	public void startEntity() {
		sendNow(this.datacenter.getId(), Constants.APPLICATION_SUBMIT, this.applicationFileName);
	}
	@Override
	public void shutdownEntity() {
		List<Vm> vmList = this.datacenter.getVmList();
		for(Vm vm:vmList) {
			Log.printLine(CloudSim.clock() + ": " + getName() + ": Shuttingdown.. VM:" + vm.getId());
		}
	}
	public void printResult() {
		int numWorkloads=0, numWorkloadsCPU=0, numWorkloadsNetwork =0, 
				numWorkloadsOver=0, numWorkloadsNetworkOver=0, numWorkloadsCPUOver=0;
		double totalServetime=0, totalServetimeCPU=0, totalServetimeNetwork=0;
		
		// For group analysis		
		int[] groupNumWorkloads = new int[SDNBroker.lastAppId];
		double[] groupTotalServetime = new double[SDNBroker.lastAppId];
		double[] groupTotalServetimeCPU = new double[SDNBroker.lastAppId];
		double[] groupTotalServetimeNetwork = new double[SDNBroker.lastAppId];
		
		for(WorkloadParser wp:workloadId.keySet()) {
			WorkloadResultWriter wrw = wp.getResultWriter(); 
			wrw.printStatistics();
			
			numWorkloads += wrw.getWorklaodNum();
			numWorkloadsOver += wrw.getWorklaodNumOvertime();
			numWorkloadsCPU += wrw.getWorklaodNumCPU();
			numWorkloadsCPUOver += wrw.getWorklaodNumCPUOvertime();
			numWorkloadsNetwork += wrw.getWorklaodNumNetwork();
			numWorkloadsNetworkOver += wrw.getWorklaodNumNetworkOvertime();
			
			totalServetime += wrw.getServeTime();
			totalServetimeCPU += wrw.getServeTimeCPU();
			totalServetimeNetwork += wrw.getServeTimeNetwork();
			
			// For group analysis
			groupNumWorkloads[wp.getGroupId()] += wrw.getWorklaodNum();
			groupTotalServetime[wp.getGroupId()] += wrw.getServeTime();
			groupTotalServetimeCPU[wp.getGroupId()] += wrw.getServeTimeCPU();
			groupTotalServetimeNetwork[wp.getGroupId()] += wrw.getServeTimeNetwork();
		}
		
		Log.printLine("============= SDNBroker.printResult() =============================");
		Log.printLine("Workloads Num: "+ numWorkloads);
		Log.printLine("Workloads CPU Num: "+ numWorkloadsCPU);
		Log.printLine("Workloads Network Num: "+ numWorkloadsNetwork);
		Log.printLine("Total serve time: "+ totalServetime);
		Log.printLine("Total serve time CPU: "+ totalServetimeCPU);
		Log.printLine("Total serve time Network: "+ totalServetimeNetwork);
		if(numWorkloads!=0) {
			Log.printLine("Avg serve time: "+ totalServetime/numWorkloads);
			Log.printLine("Overall overtime percentage: "+ (double)numWorkloadsOver/numWorkloads);
		}
		if(numWorkloadsCPU!=0) {
			Log.printLine("Avg serve time CPU: "+ totalServetimeCPU/numWorkloadsCPU);
			Log.printLine("CPU overtime percentage: "+ (double)numWorkloadsCPUOver/numWorkloadsCPU);
		}
		if(numWorkloadsNetwork!=0) {
			Log.printLine("Avg serve time Network: "+ totalServetimeNetwork/numWorkloadsNetwork);
			Log.printLine("Network overtime percentage: "+ (double)numWorkloadsNetworkOver/numWorkloadsNetwork);
		}
			
		// For group analysis
		Log.printLine("============= SDNBroker.printResult() Group analysis =======================");
		for(int i=0; i<SDNBroker.lastAppId; i++) {
			if(groupNumWorkloads[i] != 0) {
				Log.printLine("Group num: "+i+", groupNumWorkloads:"+groupNumWorkloads[i]);
				Log.printLine("Group num: "+i+", groupTotalServetime:"+groupTotalServetime[i]);
				Log.printLine("Group num: "+i+", groupTotalServetimeCPU:"+groupTotalServetimeCPU[i]);
				Log.printLine("Group num: "+i+", groupTotalServetimeNetwork:"+groupTotalServetimeNetwork[i]);
				Log.printLine("Group num: "+i+", group avg Serve time:"+groupTotalServetime[i]/groupNumWorkloads[i]);
				Log.printLine("Group num: "+i+", group avg Serve time CPU:"+groupTotalServetimeCPU[i]/groupNumWorkloads[i]);
				Log.printLine("Group num: "+i+", group avg Serve time Network:"+groupTotalServetimeNetwork[i]/groupNumWorkloads[i]);				
			}
			
		}
	}
	public void submitDeployApplication(SDNDatacenter dc, String filename) {
		this.datacenter = dc;
		this.applicationFileName = filename;
	}
	
	public void submitRequests(String filename) {
		this.workloadFileNames.add(filename);
	}

	@Override
	public void processEvent(SimEvent ev) {
		int tag = ev.getTag();
		
		switch(tag){
			case CloudSimTags.VM_CREATE_ACK:
				processVmCreate(ev);
				break;
			case Constants.APPLICATION_SUBMIT_ACK:
				applicationSubmitCompleted(ev); 
				break;
			case Constants.REQUEST_COMPLETED:
				requestCompleted(ev); 
				break;
			case Constants.REQUEST_OFFER_MORE:
				requestOfferMode(ev);
				break;					
			default: 
				System.out.println("Unknown event received by "+super.getName()+". Tag:"+ev.getTag());
				break;
		}
	}
	private void processVmCreate(SimEvent ev) {
		
	}
	
	private void requestCompleted(SimEvent ev) {
		Request req = (Request) ev.getData();
		Workload wl = requestMap.remove(req.getRequestId());
		wl.writeResult();
	}
	
	private void applicationSubmitCompleted(SimEvent ev) {
		for(String filename: this.workloadFileNames) {
			WorkloadParser wParser = startWorkloadParser(filename);
			workloadId.put(wParser, SDNBroker.lastAppId);
			SDNBroker.lastAppId++;
			
			scheduleRequest(wParser);
		}
	}
	
	private void requestOfferMode(SimEvent ev) {
		WorkloadParser wp = (WorkloadParser) ev.getData();
		scheduleRequest(wp);
	}
	
	private WorkloadParser startWorkloadParser(String workloadFile) {
		WorkloadParser workParser = new WorkloadParser(workloadFile, this.getId(), new UtilizationModelFull(), 
				this.datacenter.getVmNameIdTable(), this.datacenter.getFlowNameIdTable());
		
		return workParser;
		
	}
	private void scheduleRequest(WorkloadParser workParser) {
		int workloadId = this.workloadId.get(workParser);
		workParser.parseNextWorkloads();
		List<Workload> parsedWorkloads = workParser.getParsedWorkloads();
		
		if(parsedWorkloads.size() > 0) {
			// Schedule the parsed workloads 
			for(Workload wl: parsedWorkloads) {
				double scehduleTime = wl.time - CloudSim.clock();
				if(scehduleTime <0) {
					//throw new IllegalArgumentException("SDNBroker.scheduleRequest(): Workload's start time is negative: " + wl);
					Log.printLine("**"+CloudSim.clock()+": SDNBroker.scheduleRequest(): abnormal start time." + wl);
					continue;
				}
				wl.appId = workloadId;
				
				send(this.datacenter.getId(), scehduleTime, Constants.REQUEST_SUBMIT, wl.request);
				requestMap.put(wl.request.getTerminalRequest().getRequestId(), wl);
			}
			
//			this.cloudletList.addAll(workParser.getParsedCloudlets());
//			this.workloads.addAll(parsedWorkloads);
			
			// Schedule the next workload submission
			Workload lastWorkload = parsedWorkloads.get(parsedWorkloads.size()-1);
			send(this.getId(), lastWorkload.time - CloudSim.clock(), Constants.REQUEST_OFFER_MORE, workParser);
		}
	}
	
	public List<Workload> getWorkloads() {
//		return workloads;
		return null;
	}
}
