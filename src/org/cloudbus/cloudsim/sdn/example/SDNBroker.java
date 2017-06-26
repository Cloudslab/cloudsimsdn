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
					//throw new IllegalArgumentException("Previous workload submitting...");
					continue;
				}
				wl.appId = workloadId;
				double delay = wl.time - CloudSim.clock();
				if(delay < 0 ) {
					Log.printLine("SDNBroker.scheduleRequest(): Workload's start time is after now: " + wl);
				}
				send(this.datacenter.getId(), delay, Constants.REQUEST_SUBMIT, wl.request);
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
//		return this.workloads;
		return null;
	}
	/*
	private static int reqId=0; 
	private void scheduleRequestTest() {
		
		cloudletList = new ArrayList<Cloudlet>();
		int cloudletId = 0;
		
		List<Vm> vmList = this.datacenter.getVmList();
		
		Vm vm1 = vmList.get(0);
		Vm vm2 = vmList.get(1);
		Vm vm3 = vmList.get(2);

		///////////////////////////////////////
		// req = vm1:p1 -> tr1 -> vm2:p2 -> tr2 -> vm3:p3 -> tr3 -> vm1:p4
		// req                    r1               r2               r3    
		long fileSize = 300;
		long outputSize = 300;
		UtilizationModel utilizationModel = new UtilizationModelFull();
		
		Cloudlet cloudlet1 = new Cloudlet(cloudletId++, 4000, 1, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
		Cloudlet cloudlet2 = new Cloudlet(cloudletId++, 30000, 1, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
		Cloudlet cloudlet3 = new Cloudlet(cloudletId++, 6000, 1, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
		Cloudlet cloudlet4 = new Cloudlet(cloudletId++, 10000, 1, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
		cloudlet1.setUserId(getId());
		cloudlet2.setUserId(getId());
		cloudlet3.setUserId(getId());
		cloudlet4.setUserId(getId());
		cloudlet1.setVmId(vm1.getId());
		cloudletList.add(cloudlet1);
		cloudletList.add(cloudlet2);
		cloudletList.add(cloudlet3);
		cloudletList.add(cloudlet4);
		Processing p1 = new Processing(cloudlet1);
		Processing p2 = new Processing(cloudlet2);
		Processing p3 = new Processing(cloudlet3);
		Processing p4 = new Processing(cloudlet4);

		Request req = new Request(reqId++, getId(), getId());
		Request r1 = new Request(reqId++, getId(), getId());
		Request r2 = new Request(reqId++, getId(), getId());
		Request r3 = new Request(reqId++, getId(), getId());
		
		r3.addActivity(p4);
		
		Transmission tr3 = new Transmission(vm3.getId(), vm1.getId(), 30000, r3);
		r2.addActivity(p3);
		r2.addActivity(tr3);
		
		Transmission tr2 = new Transmission(vm2.getId(), vm3.getId(), 7000, r2);
		r1.addActivity(p2);
		r1.addActivity(tr2);

		Transmission tr1 = new Transmission(vm1.getId(), vm2.getId(), 3000, r1);
		req.addActivity(p1);
		req.addActivity(tr1);
		sendNow(this.datacenter.getId(), Constants.REQUEST_SUBMIT, req);
	}

	*/
}
