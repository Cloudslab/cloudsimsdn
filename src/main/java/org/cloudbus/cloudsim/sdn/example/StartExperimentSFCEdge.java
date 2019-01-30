/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.example;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.CloudSimEx;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.workload.Workload;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemGroupPriority;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemSimple;
import org.cloudbus.cloudsim.sdn.parsers.PhysicalTopologyParser;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;
import org.cloudbus.cloudsim.sdn.policies.selecthost.HostSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.selecthost.HostSelectionPolicyMostFull;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyBandwidthAllocation;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyCombinedLeastFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyCombinedMostFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyPriorityFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmMigrationPolicy;

/**
 * CloudSimSDN example main program for Edge experiment. It loads physical topology file, application
 * deployment configuration file and workload files, and run simulation.
 * Simulation result will be saved into files.
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 3.0
 */
public class StartExperimentSFCEdge {
	protected static String physicalTopologyFile 	= "dataset-energy/energy-physical.json";
	protected static String deploymentFile 		= "dataset-energy/energy-virtual.json";
	protected static String [] workload_files 			= { 
		"dataset-energy/energy-workload.csv",
		//"sdn-example-workload-normal-user.csv",	
		//"sdn-example-workload-prio-user-prio-ch.csv",
		//"sdn-example-workload-prio-user-normal-ch.csv",
		};
	
	protected static List<String> workloads;
	
	private  static boolean logEnabled = true;

	public interface VmAllocationPolicyFactory {
		public VmAllocationPolicy create(List<? extends Host> list,
				HostSelectionPolicy hostSelectionPolicy,
				VmMigrationPolicy vmMigrationPolicy
				);
	}
	enum VmAllocationPolicyEnum{ 
		LFF, 
		MFF, 
		HPF,
		MFFGroup,
		LFFFlow, 
		MFFFlow, 
		HPFFlow,
		MFFGroupFlow,
		Random,
		RandomFlow,
		MFFBW, MFFCPU,
		END
		}	
	
	private static void printUsage() {
		String runCmd = "java SDNExample";
		System.out.format("Usage: %s <LFF|MFF> <0|1> <physical.json> <virtual.json> <working_dir> [workload1.csv] [workload2.csv] [...]\n", runCmd);
	}
	
	public static String policyName = "";

	public static void setExpName(String policy, String sfOn) {
		if(Configuration.SFC_LATENCY_AWARE_ENABLE) {
			Configuration.experimentName = String.format("SFC_On_%s_%d_%s_", sfOn, (int)Configuration.migrationTimeInterval, 
					policy
				);
		}
		else {
			Configuration.experimentName = String.format("SFC_Off_%s_%d_%s_", sfOn, (int)Configuration.migrationTimeInterval,
					policy
				);
		}
	}

	/**
	 * Creates main() to run this example.
	 *
	 * @param args the args
	 * @throws FileNotFoundException 
	 */
	@SuppressWarnings("unused")
	public static void main(String[] args) throws FileNotFoundException {
		int n = 0;
		
		//SDNBroker.experimentStartTime = 73800;
		//SDNBroker.experimentFinishTime = 77400;
		
		CloudSimEx.setStartTime();
		
		// Parse system arguments
		if(args.length < 1) {
			printUsage();
			System.exit(1);
		}
		
		//1. Policy: MFF, LFF, ...
		String policy = args[n++];
		
		String sfcOn = args[n++];
		if("1".equals(sfcOn)) {
			Configuration.SFC_LATENCY_AWARE_ENABLE = true;
		} 
		else {
			Configuration.SFC_LATENCY_AWARE_ENABLE = false;
		}
		
		//Configuration.OVERBOOKING_RATIO_INIT = Double.parseDouble(args[n++]);

		setExpName(policy, sfcOn);
		VmAllocationPolicyEnum vmAllocPolicy = VmAllocationPolicyEnum.valueOf(policy);

		//2. Physical Topology filename
		if(args.length > n)
			physicalTopologyFile = args[n++];

		//3. Virtual Topology filename
		if(args.length > n)
			deploymentFile = args[n++];

		//4. Workload files
		//4-1. Group workloads: <start_index_1> <end_index_1> <file_suffix_1> ...
		//4-2. Normal workloads: <working_directory> <filename1> <filename2> ...
		if(args.length > n) {
			workloads = new ArrayList<String>();
			if(isInteger(args[n])) {
				// args: <startIndex1> <endIndex1> <filename_suffix1> ... 
				int i = n;
				while(i < args.length) {
					Integer startNum = Integer.parseInt(args[i++]);
					Integer endNum = Integer.parseInt(args[i++]);
					String filenameSuffix = args[i++];
					List<String> names = createGroupWorkloads(startNum, endNum, filenameSuffix);
					workloads.addAll(names);					
				}
			}
			else
			{
				int i=n;
				if(args.length > n+1) {
					// 4th arg is workload directory.
					Configuration.workingDirectory = args[n++];
					i=n;
				}
				// args: <filename1> <filename2> ...
				for(; i<args.length; i++) {
					workloads.add(args[i]);
				}
			}
		}
		else {
			workloads = (List<String>) Arrays.asList(workload_files);
		}
		
		FileOutputStream output = new FileOutputStream(Configuration.workingDirectory+Configuration.experimentName+"log.out.txt");
		Log.setOutput(output);
		
		printArguments(physicalTopologyFile, deploymentFile, Configuration.workingDirectory, workloads);
		Log.printLine("Starting CloudSim SDN...");

		try {
			// Initialize
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			CloudSim.init(num_user, calendar, trace_flag);
			
			VmAllocationPolicyFactory vmAllocationFac = null;
			NetworkOperatingSystem nos = null;
			HostSelectionPolicy hostSelectionPolicy = null;
			VmMigrationPolicy vmMigrationPolicy = null;
			LinkSelectionPolicy ls = new LinkSelectionPolicyBandwidthAllocation();
			
			switch(vmAllocPolicy) {
			case Random:
			case RandomFlow:
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list,
							HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy
							) { 
						return new VmAllocationPolicyCombinedLeastFullFirst(list); 
					}
				};
				nos = new NetworkOperatingSystemSimple();
				break;			
			case MFF:
			case MFFFlow:
			case MFFCPU:
			case MFFBW:
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list,
							HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy
							) {
						return new VmAllocationPolicyCombinedMostFullFirst(list); 
					}
				};
				nos = new NetworkOperatingSystemSimple();
				break;
			case LFF:
			case LFFFlow:
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list,
							HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy
							) { 
						return new VmAllocationPolicyCombinedLeastFullFirst(list); 
					}
				};
				nos = new NetworkOperatingSystemSimple();
				break;
			case HPF:	// High Priority First
			case HPFFlow:
				// Initial placement: overbooking, MFF
				// Initial placement connectivity: Connected VMs in one host
				// Migration: none
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list,
							HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy
							) { 
						return new VmAllocationPolicyPriorityFirst(list, hostSelectionPolicy, vmMigrationPolicy); 
					}
				};
				nos = new NetworkOperatingSystemGroupPriority();
				hostSelectionPolicy = new HostSelectionPolicyMostFull();
				vmMigrationPolicy = null;
				break;				
			default:
				System.err.println("Choose proper VM placement polilcy!");
				printUsage();
				System.exit(1);
			}
			
			switch(vmAllocPolicy) {
			case MFFCPU:
				Configuration.SFC_AUTOSCALE_ENABLE_VM = true;
				Configuration.SFC_AUTOSCALE_ENABLE_VM_VERTICAL = true;
				Configuration.SFC_AUTOSCALE_ENABLE_SCALE_DOWN_VM = true;
				Configuration.SFC_AUTOSCALE_ENABLE_BW = false;
				Configuration.SFC_AUTOSCALE_ENABLE_SCALE_DOWN_BW = false;
				break;
			case MFFBW:
				Configuration.SFC_AUTOSCALE_ENABLE_VM = false;
				Configuration.SFC_AUTOSCALE_ENABLE_VM_VERTICAL = false;
				Configuration.SFC_AUTOSCALE_ENABLE_SCALE_DOWN_VM = false;
				Configuration.SFC_AUTOSCALE_ENABLE_BW = true;
				Configuration.SFC_AUTOSCALE_ENABLE_SCALE_DOWN_BW = true;
				break;
			default:
				break;
			}


			// Create multiple Datacenters
			Map<NetworkOperatingSystem, SDNDatacenter> dcs = createPhysicalTopology(physicalTopologyFile, ls, vmAllocationFac,
					hostSelectionPolicy, vmMigrationPolicy);


//			nos.setLinkSelectionPolicy(ls);
//			snos.setMonitorEnable(false);

			// Create a Datacenter
//			SDNDatacenter datacenter = createSDNDatacenter("Datacenter_0", physicalTopologyFile, nos, vmAllocationFac,
//					hostSelectionPolicy, vmMigrationPolicy);

			// Broker
			SDNBroker broker = createBroker();
			int brokerId = broker.getId();

			// Submit virtual topology
			broker.submitDeployApplication(dcs.values(), deploymentFile);
			
			// Submit individual workloads
			submitWorkloads(broker);
			
			// Sixth step: Starts the simulation
			if(!StartExperimentSFCEdge.logEnabled) 
				Log.disable();
			
			startSimulation(broker, dcs.values());
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	public static void startSimulation(SDNBroker broker, Collection<SDNDatacenter> dcs) {
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
		List<Host> hostList = getAllHostList(dcs);
		List<Switch> switchList = getAllSwitchList(dcs);
		LogPrinter.printEnergyConsumption(hostList, switchList, finishTime);

		Log.printLine("Simultanously used hosts:"+maxHostHandler.getMaxNumHostsUsed());			
		Log.printLine("CloudSim SDN finished!");
	}
	
	private static List<Switch> getAllSwitchList(Collection<SDNDatacenter> dcs) {
		List<Switch> allSwitch = new ArrayList<Switch>();
		for(SDNDatacenter dc:dcs) {
			allSwitch.addAll(dc.getNOS().getSwitchList());
		}
		
		return allSwitch;
	}
	
	private static List<Host> getAllHostList(Collection<SDNDatacenter> dcs) {
		List<Host> allHosts = new ArrayList<Host>();
		for(SDNDatacenter dc:dcs) {
			allHosts.addAll(dc.getNOS().getHostList());
		}
		
		return allHosts;
	}
	
	public static Map<NetworkOperatingSystem, SDNDatacenter> createPhysicalTopology(String physicalTopologyFile, 
			LinkSelectionPolicy ls, VmAllocationPolicyFactory vmAllocationFac,
			HostSelectionPolicy hostSelectionPolicy,
			VmMigrationPolicy vmMigrationPolicy) {
		HashMap<NetworkOperatingSystem, SDNDatacenter> dcs = new HashMap<NetworkOperatingSystem, SDNDatacenter>();
		// This funciton creates Datacenters and NOS inside the data cetner.
		Map<String, NetworkOperatingSystem> dcNameNOS = PhysicalTopologyParser.loadPhysicalTopologyMultiDC(physicalTopologyFile);
		
		for(String dcName:dcNameNOS.keySet()) {
			NetworkOperatingSystem nos = dcNameNOS.get(dcName);
			nos.setLinkSelectionPolicy(ls);
			SDNDatacenter datacenter = createSDNDatacenter(dcName, nos, vmAllocationFac, hostSelectionPolicy,
					vmMigrationPolicy);
			
			dcs.put(nos, datacenter);
		}		
		return dcs;
	}
		
	public static void submitWorkloads(SDNBroker broker) {
		// Submit workload files individually
		if(workloads != null) {
			for(String workload:workloads)
				broker.submitRequests(workload);
		}
	}
	
	public static void printArguments(String physical, String virtual, String dir, List<String> workloads) {
		Log.printLine("Data center infrastructure (Physical Topology) : "+ physical);
		Log.printLine("Virtual Machine and Network requests (Virtual Topology) : "+ virtual);
		Log.printLine("Workloads in "+dir+" :");
		for(String work:workloads)
			Log.printLine("  "+work);		
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
	protected static SDNDatacenter createSDNDatacenter(String name, 
			NetworkOperatingSystem snos, 
			VmAllocationPolicyFactory vmAllocationFactory,
			HostSelectionPolicy hostSelectionPolicy,
			VmMigrationPolicy vmMigrationPolicy) {
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
			VmAllocationPolicy vmPolicy = vmAllocationFactory.create(hostList, hostSelectionPolicy, vmMigrationPolicy);
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
		SDNBroker broker = null;
		try {
			broker = new SDNBroker("Broker");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return broker;
	}
	

	private static List<String> createGroupWorkloads(int start, int end, String filename_suffix_group) {
		List<String> filenameList = new ArrayList<String>();
		
		for(int set=start; set<=end; set++) {
			String filename = Configuration.workingDirectory+set+"_" + filename_suffix_group;
			filenameList.add(filename);
		}
		return filenameList;
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
	
	public static boolean isInteger(String string) {
	    try {
	        Integer.valueOf(string);
	        return true;
	    } catch (NumberFormatException e) {
	        return false;
	    }
	}
}