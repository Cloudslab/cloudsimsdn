/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.HostFactory;
import org.cloudbus.cloudsim.sdn.HostFactorySimple;
import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.workload.Workload;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemSimple;
import org.cloudbus.cloudsim.sdn.parsers.PhysicalTopologyParser;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyDestinationAddress;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyCombinedLeastFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyCombinedMostFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyMipsLeastFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyMipsMostFullFirst;

/**
 * CloudSimSDN example main program. It loads physical topology file, application
 * deployment configuration file and workload files, and run simulation.
 * Simulation result will be shown on the console 
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class SimpleExample {
	protected static String physicalTopologyFile 	= "dataset-energy/energy-physical.json";
	protected static String deploymentFile 		= "dataset-energy/energy-virtual.json";
	protected static String [] workload_files 			= { 
		"dataset-energy/energy-workload.csv"
		};
	
	protected static List<String> workloads;
	
	private  static boolean logEnabled = true;

	public interface VmAllocationPolicyFactory {
		public VmAllocationPolicy create(List<? extends Host> list);
	}
	enum VmAllocationPolicyEnum{ CombLFF, CombMFF, MipLFF, MipMFF, OverLFF, OverMFF, LFF, MFF, Overbooking}	
	
	private static void printUsage() {
		String runCmd = "java SDNExample";
		System.out.format("Usage: %s <LFF|MFF> [physical.json] [virtual.json] [workload1.csv] [workload2.csv] [...]\n", runCmd);
	}

	/**
	 * Creates main() to run this example.
	 *
	 * @param args the args
	 */
	@SuppressWarnings("unused")
	public static void main(String[] args) {

		String policyName = "LFF";
		workloads = new ArrayList<String>();
		
		// Parse system arguments
		if(args.length >= 1) {
			//printUsage();
			//System.exit(1);
			policyName = args[0];
		}
		
		VmAllocationPolicyEnum vmAllocPolicy = VmAllocationPolicyEnum.valueOf(policyName);
		if(args.length > 1)
			physicalTopologyFile = args[1];
		if(args.length > 2)
			deploymentFile = args[2];
		if(args.length > 3)
			for(int i=3; i<args.length; i++) {
				workloads.add(args[i]);
			}
		else
			workloads = Arrays.asList(workload_files);
		
		printArguments(physicalTopologyFile, deploymentFile, workloads);
		Log.printLine("Starting CloudSim SDN...");

		try {
			// Initialize
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			CloudSim.init(num_user, calendar, trace_flag);
			
			VmAllocationPolicyFactory vmAllocationFac = null;
			NetworkOperatingSystem nos = new NetworkOperatingSystemSimple();
			HostFactory hsFac = new HostFactorySimple();
			LinkSelectionPolicy ls = null;
			switch(vmAllocPolicy) {
			case CombMFF:
			case MFF:
				vmAllocationFac = VmAllocationPolicyCombinedMostFullFirst::new;
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyDestinationAddress();
				break;
			case CombLFF:
			case LFF:
				vmAllocationFac = hostList -> new VmAllocationPolicyCombinedLeastFullFirst(hostList);
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyDestinationAddress();
				break;
			case MipMFF:
				vmAllocationFac = hostList -> new VmAllocationPolicyMipsMostFullFirst(hostList);
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyDestinationAddress();
				break;
			case MipLFF:
				vmAllocationFac = hostList -> new VmAllocationPolicyMipsLeastFullFirst(hostList);
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyDestinationAddress();
				break;
//			case Overbooking:
//				vmAllocationFac = new VmAllocationPolicyFactory() {
//					public VmAllocationPolicy create(List<? extends Host> hostList) { return new OverbookingVmAllocationPolicy(hostList); }
//				};
//				snos = new OverbookingNetworkOperatingSystem(physicalTopologyFile);
//				break;
			default:
				System.err.println("Choose proper VM placement polilcy!");
				printUsage();
				System.exit(1);
			}
			
			// Set LinkSelectionPolicy
			nos.setLinkSelectionPolicy(ls);

			// Create a Datacenter
			SDNDatacenter datacenter = createSDNDatacenter("Datacenter_0", physicalTopologyFile, nos, vmAllocationFac);

			// Broker
			SDNBroker broker = createBroker();
			if (broker != null) {
				int brokerId = broker.getId();
			}

			// Submit virtual topology
			broker.submitDeployApplication(datacenter, deploymentFile);
			
			// Submit individual workloads
			submitWorkloads(broker);
			
			// Sixth step: Starts the simulation
			if(!SimpleExample.logEnabled) 
				Log.disable();
			
			double finishTime = CloudSim.startSimulation();
			CloudSim.stopSimulation();
			Log.enable();
			
			broker.printResult();
			
			Log.printLine(finishTime+": ========== EXPERIMENT FINISHED ===========");
			
			// Print results when simulation is over
			List<Workload> wls = broker.getWorkloads();
			if(wls != null)
				LogPrinter.printWorkloadList(wls);
			
			// Print hosts' and switches' total utilization.
			List<Host> hostList = nos.getHostList();
			List<Switch> switchList = nos.getSwitchList();
			LogPrinter.printEnergyConsumption(hostList, switchList, finishTime);

			Log.printLine("Simultanously used hosts:"+maxHostHandler.getMaxNumHostsUsed());			
			Log.printLine("CloudSim SDN finished!");

		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	public static void submitWorkloads(SDNBroker broker) {
		// Submit workload files individually
		if(workloads != null) {
			for(String workload:workloads)
				broker.submitRequests(workload);
		}
		
		// Or, Submit groups of workloads
		//submitGroupWorkloads(broker, WORKLOAD_GROUP_NUM, WORKLOAD_GROUP_PRIORITY, WORKLOAD_GROUP_FILENAME, WORKLOAD_GROUP_FILENAME_BG);
	}
	
	public static void printArguments(String physical, String virtual, List<String> workloads) {
		System.out.println("Data center infrastructure (Physical Topology) : "+ physical);
		System.out.println("Virtual Machine and Network requests (Virtual Topology) : "+ virtual);
		System.out.println("Workloads: ");
		for(String work:workloads)
			System.out.println("  "+work);		
	}
	
	/**
	 * Creates the datacenter.
	 *
	 * @param name the name
	 *
	 * @return the datacenter
	 */
	protected static NetworkOperatingSystem nos;
	protected static PowerUtilizationMaxHostInterface maxHostHandler = null;
	protected static SDNDatacenter createSDNDatacenter(String name, String physicalTopology, NetworkOperatingSystem snos, VmAllocationPolicyFactory vmAllocationFactory) {
		// In order to get Host information, pre-create NOS.
		nos=snos;
		List<Host> hostList = nos.getHostList();

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
				arch, os, vmm, hostList, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		// Create Datacenter with previously set parameters
		SDNDatacenter datacenter = null;
		try {
			VmAllocationPolicy vmPolicy = vmAllocationFactory.create(hostList);
			maxHostHandler = (PowerUtilizationMaxHostInterface)vmPolicy;
			datacenter = new SDNDatacenter(name, characteristics, vmPolicy, storageList, 0, nos);
			
			
			nos.setDatacenter(datacenter);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return datacenter;
	}

	// We strongly encourage users to develop their own broker policies, to
	// submit vms and cloudlets according
	// to the specific rules of the simulated scenario
	/**
	 * Creates the broker.
	 *
	 * @return the datacenter broker
	 */
	protected static SDNBroker createBroker() {
		SDNBroker broker;
		try {
			broker = new SDNBroker("Broker");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return broker;
	}
	

	static String WORKLOAD_GROUP_FILENAME = "workload_10sec_100_default.csv";	// group 0~9
	static String WORKLOAD_GROUP_FILENAME_BG = "workload_10sec_100.csv"; // group 10~29
	static int WORKLOAD_GROUP_NUM = 50;
	static int WORKLOAD_GROUP_PRIORITY = 1;
	
	public static void submitGroupWorkloads(SDNBroker broker, int workloadsNum, int groupSeperateNum, String filename_suffix_group1, String filename_suffix_group2) {
		for(int set=0; set<workloadsNum; set++) {
			String filename = filename_suffix_group1;
			if(set>=groupSeperateNum) 
				filename = filename_suffix_group2;
			
			filename = set+"_"+filename;
			broker.submitRequests(filename);
		}
	}

	
	/// Under development
	/*
	static class WorkloadGroup {
		static int autoIdGenerator = 0;
		final int groupId;
		
		String groupFilenamePrefix;
		int groupFilenameStart;
		int groupFileNum;
		
		WorkloadGroup(int id, String groupFilenamePrefix, int groupFileNum, int groupFilenameStart) {
			this.groupId = id;
			this.groupFilenamePrefix = groupFilenamePrefix;
			this.groupFileNum = groupFileNum;
		}
		
		List<String> getFileList() {
			List<String> filenames = new LinkedList<String>();
			
			for(int fileId=groupFilenameStart; fileId< this.groupFilenameStart+this.groupFileNum; fileId++) {
				String filename = groupFilenamePrefix + fileId;
				filenames.add(filename);
			}
			return filenames;
		}
		
		public static WorkloadGroup createWorkloadGroup(String groupFilenamePrefix, int groupFileNum) {
			return new WorkloadGroup(autoIdGenerator++, groupFilenamePrefix, groupFileNum, 0);
		}
		public static WorkloadGroup createWorkloadGroup(String groupFilenamePrefix, int groupFileNum, int groupFilenameStart) {
			return new WorkloadGroup(autoIdGenerator++, groupFilenamePrefix, groupFileNum, groupFilenameStart);
		}
	}
	
	static LinkedList<WorkloadGroup> workloadGroups = new LinkedList<WorkloadGroup>();
	 */
}