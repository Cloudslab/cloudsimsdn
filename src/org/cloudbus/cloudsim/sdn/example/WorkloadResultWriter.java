/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.example;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Activity;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.LogWriter;
import org.cloudbus.cloudsim.sdn.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.Processing;
import org.cloudbus.cloudsim.sdn.Request;
import org.cloudbus.cloudsim.sdn.Transmission;

public class WorkloadResultWriter {
	private boolean headPrinted=false;
	private String filename;
	private LogWriter out = null;
	
	// For statistics
	private double totalServeTime;	// All serve time
	private double cpuServeTime; // CPU only
	private double networkServeTime; // Network only
	
	private int printedWorkloadNum; 	// Number of workloads
	private int overNum;	// Number of workloads exceeds estimated response time
	
	private int cloudletNum;	// Number of Cloudlets 
	private int cloudletOverNum;	// Number of Cloudlets exceeds estimated finish time
	private int transmissionNum;	// Number of transmissions
	private int transmissionOverNum;	// Number of transmissions exceeds estimated transmission time

//	private PriorityQueue<Workload> workloadToPrint;
//	private int nextId = 0;
	public WorkloadResultWriter(String file) {
		this.filename = file;
//		workloadToPrint = new PriorityQueue<Workload>();
		out = LogWriter.getLogger(filename);
	}
	
	public void writeResult(Workload wl) {
		printWorkload(wl);
//		workloadToPrint.add(wl);
//		flushWorkloads();
	}
//	
//	private void flushWorkloads() {
//		Workload wl;
//		while((wl = workloadToPrint.peek()) != null && wl.workloadId == nextId) {
//			wl = workloadToPrint.poll();
//			printWorkload(wl);
//			nextId++;
//		}
//	}
//	
	private void printWorkload(Workload wl) {
		if(!headPrinted) {
			this.printHead(wl);
			headPrinted = true;
		}
		
		double serveTime;
		
		print(String.format(LogPrinter.fInt, wl.workloadId));
		print(String.format(LogPrinter.fInt, wl.appId));
		printRequest(wl.request);
		
		serveTime= getWorkloadFinishTime(wl) - getWorkloadStartTime(wl);
		
		print(String.format(LogPrinter.fFloat, serveTime));
		printLine();
		
		this.totalServeTime += serveTime;
		printedWorkloadNum++;
		if(isOverTime(wl, serveTime)) {
			overNum++;
		}
	}
	
	private void printRequestTitle(Request req) {
		List<Activity> acts = req.getRemovedActivities();
		for(Activity act:acts) {
			if(act instanceof Transmission) {
				Transmission tr=(Transmission)act;
				print(String.format(LogPrinter.fString, "Tr:Size"));
				print(String.format(LogPrinter.fString, "Tr:Channel"));
				
				print(String.format(LogPrinter.fString, "Tr:time"));
				print(String.format(LogPrinter.fString, "Tr:Start"));
				print(String.format(LogPrinter.fString, "Tr:End"));
				
				printRequestTitle(tr.getPacket().getPayload());
			}
			else {
				print(String.format(LogPrinter.fString, "Pr:Size"));
				
				print(String.format(LogPrinter.fString, "Pr:time"));
				print(String.format(LogPrinter.fString, "Pr:Start"));
				print(String.format(LogPrinter.fString, "Pr:End"));
			}
		}
	}

	public void printRequest(Request req) {
		
		List<Activity> acts = req.getRemovedActivities();
		for(Activity act:acts) {
			if(act instanceof Transmission) {
				Transmission tr=(Transmission)act;
				double serveTime = tr.getServeTime();
				networkServeTime += serveTime;
				transmissionNum++;
				if(isOverTime(tr))
					transmissionOverNum++;
				
				print(String.format(LogPrinter.fInt, tr.getPacket().getSize()));
				print(String.format(LogPrinter.fInt, tr.getPacket().getFlowId()));
				
				print(String.format(LogPrinter.fFloat, serveTime));
				print(String.format(LogPrinter.fFloat, tr.getPacket().getStartTime()));
				print(String.format(LogPrinter.fFloat, tr.getPacket().getFinishTime()));
				
				printRequest(tr.getPacket().getPayload());
			}
			else {
				Processing pr=(Processing)act;
				double serveTime = pr.getServeTime();
				cpuServeTime += serveTime;
				cloudletNum++;
				if(isOverTime(pr))
					cloudletOverNum++;
				
				print(String.format(LogPrinter.fInt, pr.getCloudlet().getCloudletLength()));

				print(String.format(LogPrinter.fFloat, serveTime));
				print(String.format(LogPrinter.fFloat, pr.getCloudlet().getSubmissionTime()));
				print(String.format(LogPrinter.fFloat, pr.getCloudlet().getFinishTime()));
			}
		}
	}
	
	private void printHead(Workload sample) {
		print(String.format(LogPrinter.fString, "Workload_ID"));
		print(String.format(LogPrinter.fString, "App_ID"));
		printRequestTitle(sample.request);
		print(String.format(LogPrinter.fString, "ResponseTime"));
		printLine();
	}
	
	public void printWorkloadList(List<Workload> wls) {
		for(Workload wl:wls) {
			printWorkload(wl);
		}
	}
	
	public void printStatistics() {
		printLine("#======================================");
		printLine("#Number of workloads:" + printedWorkloadNum);
		printLine("#Over workloads:" + overNum);
		if(printedWorkloadNum != 0)
			printLine("#Over workloads per cent:" + overNum / printedWorkloadNum);
		printLine("#Number of Cloudlets:" + cloudletNum);
		printLine("#Over Cloudlets:" + cloudletOverNum);
		if(cloudletNum != 0)
			printLine("#Over Cloudlets per cent:" + cloudletOverNum / cloudletNum);
		printLine("#Number of transmissions:" + transmissionNum);
		printLine("#Over transmissions:" + transmissionOverNum);
		if(transmissionNum != 0)
			printLine("#Over transmissions per cent:" + transmissionOverNum / transmissionNum);
		printLine("#======================================");
		printLine("#Total serve time:" + totalServeTime);
		printLine("#CPU serve time:" + cpuServeTime);
		printLine("#Network serve time:" + networkServeTime);
		if(printedWorkloadNum != 0)
		{
			printLine("#Average total serve time:" + totalServeTime/printedWorkloadNum);
			printLine("#Average CPU serve time per workload:" + cpuServeTime/printedWorkloadNum);
			printLine("#Average network serve time per workload:" + networkServeTime/printedWorkloadNum);
		}
		if(cloudletNum != 0)
			printLine("#Average CPU serve time per Cloudlet:" + cpuServeTime/cloudletNum);
		if(transmissionNum != 0)
			printLine("#Average network serve time per transmission:" + networkServeTime/transmissionNum);
	}
	
	public int getWorklaodNum() {
		return printedWorkloadNum;
	}
	public int getWorklaodNumOvertime() {
		return overNum;
	}
	public int getWorklaodNumCPU() {
		return cloudletNum;
	}
	public int getWorklaodNumCPUOvertime() {
		return cloudletOverNum;
	}
	public int getWorklaodNumNetwork() {
		return transmissionNum;
	}
	public int getWorklaodNumNetworkOvertime() {
		return transmissionOverNum;
	}
	public double getServeTime() {
		return totalServeTime;
	}
	public double getServeTimeCPU() {
		return cpuServeTime;
	}
	public double getServeTimeNetwork() {
		return networkServeTime;
	}

	public static double getWorkloadStartTime(Workload w) {
		List<Activity> acts = getAllActivities(w.request);
		
		for(Activity act:acts) {
			if(act instanceof Processing) {
				Processing pr=(Processing)act;
				return pr.getCloudlet().getSubmissionTime();
			}
		}
		return -1;
	}
	
	public static double getWorkloadFinishTime(Workload w) {
		double finishTime = -1;
		List<Activity> acts = getAllActivities(w.request);
		
		for(Activity act:acts) {
			if(act instanceof Processing) {
				Processing pr=(Processing)act;
				finishTime=pr.getCloudlet().getFinishTime();
			}
		}
		return finishTime;
	}

	private boolean isOverTime(final Activity ac) {
		double serveTime = ac.getServeTime();
		double expectedTime = getExpectedTime(ac);
		
		if(serveTime > expectedTime * Configuration.DECIDE_SLA_VIOLATION_GRACE_ERROR ) {
			// SLA viloated. Served too late.
			return true;
		}
		return false;
	}
	
	private boolean isOverTime(final Workload wl, double serveTime) {
		double expectedTime = 0;
		List<Activity> acts = getAllActivities(wl.request);
		
		for(Activity ac:acts) {
			expectedTime += getExpectedTime(ac);
		}
		
		if(serveTime > expectedTime * Configuration.DECIDE_SLA_VIOLATION_GRACE_ERROR ) {
			// SLA viloated. Served too late.
			return true;
		}
		return false;
	}
	
	private double getExpectedTime(Activity ac) {
		double expectedTime = ac.getExpectedTime();
		
		if(ac instanceof Transmission) {
			if(expectedTime < NetworkOperatingSystem.getMinTimeBetweenNetworkEvents())
				expectedTime = NetworkOperatingSystem.getMinTimeBetweenNetworkEvents();
		}
		else {
			if(expectedTime < CloudSim.getMinTimeBetweenEvents())
				expectedTime = CloudSim.getMinTimeBetweenEvents();
		}
		return expectedTime;
	}
	

	protected void printLine() {
		out.printLine();
	}
	
	protected void print(String s) {
		out.print(s);
	}
	
	protected void printLine(String s) {
		out.printLine(s);
	}

	private static List<Activity> getAllActivities(Request req) {
		List<Activity> outputActList = new ArrayList<Activity>();
		getAllActivities(req, outputActList);
		return outputActList;
	}
	private static void getAllActivities(Request req, List<Activity> outputActList) {
		List<Activity> acts = req.getRemovedActivities();
		for(Activity act:acts) {
			outputActList.add(act);
			if(act instanceof Transmission) {
				getAllActivities(((Transmission)act).getPacket().getPayload(), outputActList);
			}
		}
	}
}
