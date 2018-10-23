/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.sdn.vmallocation.priority.VmAllocationPolicyPriorityFirst;

/**
 * Extended class of Datacenter that supports processing SDN-specific events.
 * In addtion to the default Datacenter, it processes Request submission to VM,
 * and application deployment request. 
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class SDNDatacenter extends Datacenter {
	private static final double PROCESSING_DELAY= 0.1;
	NetworkOperatingSystem nos;
	private HashMap<Integer,Request> requestsTable;
	private static boolean isMigrateEnabled = false;
	
	public SDNDatacenter(String name, DatacenterCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval, NetworkOperatingSystem nos) throws Exception {
		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);
		
		this.nos=nos;
		this.requestsTable = new HashMap<Integer, Request>();
		
		//nos.init();
		if(vmAllocationPolicy instanceof VmAllocationPolicyPriorityFirst) {
			((VmAllocationPolicyPriorityFirst)vmAllocationPolicy).setTopology(nos.getPhysicalTopology());
		}
	}
	
	public void addVm(Vm vm){
		getVmList().add(vm);
		if (vm.isBeingInstantiated()) vm.setBeingInstantiated(false);
		vm.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(vm).getVmScheduler().getAllocatedMipsForVm(vm));
	}
		
	@Override
	protected void processVmCreate(SimEvent ev, boolean ack) {
		super.processVmCreate(ev, ack);
		if(ack) {
			send(nos.getId(), 0/*CloudSim.getMinTimeBetweenEvents()*/, CloudSimTags.VM_CREATE_ACK, ev.getData());
		}
			
	}

	protected void processVmCreateInGroup(SimEvent ev, boolean ack) {
		@SuppressWarnings("unchecked")
		List<Object> params =(List<Object>)ev.getData();
		
		Vm vm = (Vm)params.get(0);
		VmGroup vmGroup=(VmGroup)params.get(1);

		boolean result = ((VmAllocationInGroup)getVmAllocationPolicy()).allocateHostForVmInGroup(vm, vmGroup);

		if (ack) {
			int[] data = new int[3];
			data[0] = getId();
			data[1] = vm.getId();

			if (result) {
				data[2] = CloudSimTags.TRUE;
			} else {
				data[2] = CloudSimTags.FALSE;
			}
			send(vm.getUserId(), 0, CloudSimTags.VM_CREATE_ACK, data);
			send(nos.getId(), 0, CloudSimTags.VM_CREATE_ACK, vm);
		}

		if (result) {
			getVmList().add(vm);

			if (vm.isBeingInstantiated()) {
				vm.setBeingInstantiated(false);
			}

			vm.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(vm).getVmScheduler()
					.getAllocatedMipsForVm(vm));
		}
	}	
	
	public static int migrationCompleted = 0;

	@Override
	protected void processVmMigrate(SimEvent ev, boolean ack) {
		migrationCompleted++;
		
		// Change network routing.
		@SuppressWarnings("unchecked")
		Map<String, Object> migrate = (HashMap<String, Object>) ev.getData();

		Vm vm = (Vm) migrate.get("vm");
		Host newHost = (Host) migrate.get("host");
		Host oldHost = vm.getHost();

		// Migrate the VM to another host.
		super.processVmMigrate(ev, ack);
		
		nos.processVmMigrate(vm, (SDNHost)oldHost, (SDNHost)newHost);
	}
	
	@Override
	public void processOtherEvent(SimEvent ev){
		switch(ev.getTag()){
			case Constants.REQUEST_SUBMIT: 
				processRequestSubmit((Request) ev.getData());
				break;
			case Constants.APPLICATION_SUBMIT: 
				processApplication(ev.getSource(),(String) ev.getData()); 
				break;
			case Constants.SDN_PACKET_COMPLETE: 
				processPacketCompleted((Packet)ev.getData()); 
				break;
			case Constants.SDN_VM_CREATE_IN_GROUP:
				processVmCreateInGroup(ev, false);; 
				break;
			case Constants.SDN_VM_CREATE_IN_GROUP_ACK: 
				processVmCreateInGroup(ev, true);; 
				break;
			default: 
				System.out.println("Unknown event recevied by SdnDatacenter. Tag:"+ev.getTag());
		}
	}

	public void processUpdateProcessing() {
		updateCloudletProcessing(); // Force Processing - TRUE!
		checkCloudletCompletion();
	}
	
	protected void processCloudletSubmit(SimEvent ev, boolean ack) {

		// gets the Cloudlet object
		Cloudlet cl = (Cloudlet) ev.getData();
		
		// Clear out the processed data for the previous time slot before Cloudlet submitted
		updateCloudletProcessing();

		try {
			// checks whether this Cloudlet has finished or not
			if (cl.isFinished()) {
				String name = CloudSim.getEntityName(cl.getUserId());
				Log.printLine(getName() + ": Warning - Cloudlet #" + cl.getCloudletId() + " owned by " + name
						+ " is already completed/finished.");
				Log.printLine("Therefore, it is not being executed again");
				Log.printLine();

				// NOTE: If a Cloudlet has finished, then it won't be processed.
				// So, if ack is required, this method sends back a result.
				// If ack is not required, this method don't send back a result.
				// Hence, this might cause CloudSim to be hanged since waiting
				// for this Cloudlet back.
				if (ack) {
					int[] data = new int[3];
					data[0] = getId();
					data[1] = cl.getCloudletId();
					data[2] = CloudSimTags.FALSE;

					// unique tag = operation tag
					int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
					sendNow(cl.getUserId(), tag, data);
				}

				sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);

				return;
			}

			// process this Cloudlet to this CloudResource
			cl.setResourceParameter(getId(), getCharacteristics().getCostPerSecond(), getCharacteristics()
					.getCostPerBw());

			int userId = cl.getUserId();
			int vmId = cl.getVmId();
			// time to transfer the files
			double fileTransferTime = predictFileTransferTime(cl.getRequiredFiles());

			SDNHost host = (SDNHost)getVmAllocationPolicy().getHost(vmId, userId);
			Vm vm = host.getVm(vmId, userId);
			CloudletScheduler scheduler = vm.getCloudletScheduler();
			
			double estimatedFinishTime = scheduler.cloudletSubmit(cl, fileTransferTime); // This estimated time is useless

			//host.adjustMipsShare();
			//estimatedFinishTime = scheduler.getNextFinishTime(CloudSim.clock(), scheduler.getCurrentMipsShare());

			// Check the new estimated time by using host's update VM processing funciton.
			// This function is called only to check the next finish time
			estimatedFinishTime = host.updateVmsProcessing(CloudSim.clock());
			
			estimatedFinishTime -= CloudSim.clock();

			// if this cloudlet is in the exec queue
			if (estimatedFinishTime > 0.0 && !Double.isInfinite(estimatedFinishTime)) {
				estimatedFinishTime += fileTransferTime;
//				Log.printLine(getName() + ".processCloudletSubmit(): " + "Cloudlet is going to be processed at: "+(estimatedFinishTime + CloudSim.clock()));
				
				// gurantees a minimal interval before scheduling the event
				if (estimatedFinishTime < CloudSim.getMinTimeBetweenEvents()) {
					estimatedFinishTime = CloudSim.getMinTimeBetweenEvents();
				}				
				send(getId(), estimatedFinishTime, CloudSimTags.VM_DATACENTER_EVENT);
			}

			if (ack) {
				int[] data = new int[3];
				data[0] = getId();
				data[1] = cl.getCloudletId();
				data[2] = CloudSimTags.TRUE;

				// unique tag = operation tag
				int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
				sendNow(cl.getUserId(), tag, data);
			}
		} catch (ClassCastException c) {
			Log.printLine(getName() + ".processCloudletSubmit(): " + "ClassCastException error.");
			c.printStackTrace();
		} catch (Exception e) {
			Log.printLine(getName() + ".processCloudletSubmit(): " + "Exception error.");
			e.printStackTrace();
		}

		checkCloudletCompletion();
	}

	@Override
	protected void checkCloudletCompletion() {
		if(!nos.isApplicationDeployed())
		{
			super.checkCloudletCompletion();
			return;
		}
		
		List<? extends Host> list = getVmAllocationPolicy().getHostList();
		for (int i = 0; i < list.size(); i++) {
			Host host = list.get(i);
			for (Vm vm : host.getVmList()) {
				while (vm.getCloudletScheduler().isFinishedCloudlets()) {
					Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
					if (cl != null) {
						// For completed cloudlet -> process next activity.
						Request req = requestsTable.remove(cl.getCloudletId());
						if (req.isFinished()){
							// All requests are finished, no more activities to do. Return to user
							send(req.getUserId(), PROCESSING_DELAY, Constants.REQUEST_COMPLETED, req);
						} else {
							//consume the next activity from request. It should be a transmission.
							processNextActivity(req);
						}
					}
				}
			}
		}
	}

	private void processRequestSubmit(Request req) {
		Activity ac = req.getNextActivity();
		if(ac instanceof Processing) {
			processNextActivity(req);
			
//			Processing proc = (Processing) ac;
//			Cloudlet cl = proc.getCloudlet();
//			
//			//for this first package, size doesn't matter
//			Packet pkt = new Packet(super.getId(), cl.getVmId(), -1, -1, req);
//			//sendNow(host.getAddress(), Constants.SDN_PACKAGE, pkt);
//			processPacketCompleted(pkt);
		}
		else {
			System.err.println("Request should start with Processing!!");
		}
	}
	
	private void processPacketCompleted(Packet pkt) {
		//int vmId = pkt.getDestination();
		pkt.setFinishTime(CloudSim.clock());
		Request req = pkt.getPayload();
		processNextActivity(req);
	}
	
	private void processNextActivity(Request req) {
//		Log.printLine(CloudSim.clock() + ": " + getName() + ": Process next activity: " +req);		
		Activity ac = req.removeNextActivity();

		if(ac instanceof Transmission) {
			processNextActivityTransmission((Transmission)ac);
		}
		else if(ac instanceof Processing) {
			processNextActivityProcessing(((Processing) ac), req);
		} else {
			Log.printLine(CloudSim.clock() + ": " + getName() + ": Activity is unknown..");
		}
	}
	
	private void processNextActivityTransmission(Transmission tr) {
		Packet pkt = tr.getPacket();
		//send package to router via channel (NOS)
		nos.addPacketToChannel(pkt);
		
		pkt.setStartTime(CloudSim.clock());
		tr.setRequestedBW(nos.getRequestedBandwidth(pkt));
	}
	
	private void processNextActivityProcessing(Processing proc, Request reqAfterCloudlet) {
		Cloudlet cl = proc.getCloudlet();
		
		requestsTable.put(cl.getCloudletId(), reqAfterCloudlet);
		sendNow(getId(), CloudSimTags.CLOUDLET_SUBMIT, cl);
		
		// Set the requested MIPS for this cloudlet.
		int userId = cl.getUserId();
		int vmId = cl.getVmId();
		Host host = getVmAllocationPolicy().getHost(vmId, userId);
		if(host == null) {
			throw new NullPointerException("Error! cannot find a host for Workload:"+ proc);
		}
		Vm vm = host.getVm(vmId, userId);
		double mips = vm.getMips();
		proc.setVmMipsPerPE(mips);
	}

	private void processApplication(int userId, String filename) {
		nos.deployApplication(userId,filename);
		send(userId, 0, Constants.APPLICATION_SUBMIT_ACK, filename);
	}
	
	public Map<String, Integer> getVmNameIdTable() {
		return this.nos.getVmNameIdTable();
	}
	public Map<String, Integer> getFlowNameIdTable() {
		return this.nos.getFlowNameIdTable();
	}

	// For results
	public static int migrationAttempted = 0;
	
	public void startMigrate() {
		if (isMigrateEnabled) {
			Log.printLine(CloudSim.clock()+": Migration started..");

			List<Map<String, Object>> migrationMap = getVmAllocationPolicy().optimizeAllocation(
					getVmList());

			if (migrationMap != null && migrationMap.size() > 0) {

				migrationAttempted += migrationMap.size();
				
				// Process cloudlets before migration because cloudlets are processed during migration process..
				updateCloudletProcessing();
				checkCloudletCompletion();

				for (Map<String, Object> migrate : migrationMap) {
					Vm vm = (Vm) migrate.get("vm");
					Host targetHost = (Host) migrate.get("host");
//					Host oldHost = vm.getHost();
					
					Log.formatLine(
							"%.2f: Migration of %s to %s is started",
							CloudSim.clock(),
							vm,
							targetHost);
					
					targetHost.addMigratingInVm(vm);


					/** VM migration delay = RAM / bandwidth **/
					// we use BW / 2 to model BW available for migration purposes, the other
					// half of BW is for VM communication
					// around 16 seconds for 1024 MB using 1 Gbit/s network
					send(
							getId(),
							vm.getRam() / ((double) targetHost.getBw() / (2 * 8000)),
							CloudSimTags.VM_MIGRATE,
							migrate);
				}
			}
			else {
				//Log.printLine(CloudSim.clock()+": No VM to migrate");
			}
		}		
	}

}
