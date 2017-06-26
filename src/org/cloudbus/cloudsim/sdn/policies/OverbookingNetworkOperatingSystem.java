/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.Arc;
import org.cloudbus.cloudsim.sdn.Middlebox;
import org.cloudbus.cloudsim.sdn.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;

public class OverbookingNetworkOperatingSystem extends NetworkOperatingSystem {

	public OverbookingNetworkOperatingSystem(String fileName) {
		super(fileName);
	}

	
	/*
	private Link selectLinkFirst(List<Link> links) {
		return links.get(0);
	}
	
	int i=0;
	private Link selectLinkRandom(List<Link> links) {
		return links.get(i++ % links.size());
	}

	private Link selectLinkByFlow(List<Link> links, int flowId) {
		if(flowId == -1)
			return links.get(0);
		else
			return links.get(1 % links.size());
			
	}
	
	private Link selectLinkByChannelCount(Node from, List<Link> links) {
		Link lighter = links.get(0);
		for(Link l:links) {
			if(l.getChannelCount(from) < lighter.getChannelCount(from)) {
				// Less traffic flows using this link
				lighter = l; 
			}
		}
		return lighter;
	}

	private Link selectLinkByDestination(List<Link> links, SDNHost destHost) {
		int numLinks = links.size();
		int linkid = destHost.getAddress() % numLinks;
		Link link = links.get(linkid);
		return link;
	}
*/
	
	@Override
	protected Middlebox deployMiddlebox(String type,Vm vm) {
		return null;
	}
	
	@Override
	public void processVmCreateAck(SimEvent ev) {
		super.processVmCreateAck(ev);

		// print the created VM info
		SDNVm vm = (SDNVm) ev.getData();
		Log.printLine(CloudSim.clock() + ": " + getName() + ": VM Created: " +  vm.getId() + " in " + vm.getHost());
		deployFlow(this.arcList);
	}

	public boolean deployFlow(List<Arc> links) {
		for(Arc link:links) {
			int srcVm = link.getSrcId();
			int dstVm = link.getDstId();
			int flowId = link.getFlowId();
			
			SDNHost srchost = (SDNHost) findHost(srcVm);
			SDNHost dsthost = (SDNHost) findHost(dstVm);
			if(srchost == null || dsthost == null) {
				continue;
			}
			
			if(srchost.equals(dsthost)) {
				Log.printLine(CloudSim.clock() + ": " + getName() + ": Source SDN Host is same as Destination. Go loopback");
				srchost.addVMRoute(srcVm, dstVm, flowId, dsthost);
			}
			else {
				Log.printLine(CloudSim.clock() + ": " + getName() + ": VMs are in different hosts. Create entire routing table (hosts, switches)");
				boolean findRoute = buildForwardingTable(srchost, srcVm, dstVm, flowId, null);
				
				if(!findRoute) {
					System.err.println("SimpleNetworkOperatingSystem.deployFlow: Could not find route!!" + 
							NetworkOperatingSystem.debugVmIdName.get(srcVm) + "->"+NetworkOperatingSystem.debugVmIdName.get(dstVm));
				}
			}
			
		}
		
		// Print all routing tables.
		for(Node node:this.topology.getAllNodes()) {
			node.printVMRoute();
		}
		return true;
	}
	
	@Override
	public SDNHost createHost(int ram, long bw, long storage, long pes, double mips) {
		LinkedList<Pe> peList = new LinkedList<Pe>();
		int peId=0;
		
		for(int i=0;i<pes;i++) {
			PeProvisionerOverbooking prov = new PeProvisionerOverbooking(mips);
			Pe pe = new Pe(peId++, prov);
			peList.add(pe);
		}
		
		RamProvisioner ramPro = new RamProvisionerSimple(ram);
		BwProvisioner bwPro = new BwProvisionerOverbooking(bw);
		VmScheduler vmScheduler = new VmSchedulerTimeSharedOverSubscriptionDynamicVM(peList);		
//		VmScheduler vmScheduler = new VmSchedulerTimeSharedOverSubscriptionDynamicCloudlets(peList);
		SDNHost newHost = new SDNHost(ramPro, bwPro, storage, peList, vmScheduler, this);
		((VmSchedulerTimeSharedOverSubscriptionDynamicVM)vmScheduler).debugSetHost(newHost);
		
		return newHost;		
	}


	@Override
	protected boolean deployApplication(List<Vm> vms,
			List<Middlebox> middleboxes, List<Arc> links) {
		Log.printLine(CloudSim.clock() + ": " + getName() + ": Starting deploying application..");
		
		for(Integer i=0;i<vms.size();i++)
		{
			SDNVm vm = (SDNVm) vms.get(i);
			Log.printLine(CloudSim.clock() + ": " + getName() + ": Trying to Create VM #" + vm.getId()
					+ " in " + datacenter.getName() + ", (" + vm.getStartTime() + "~" +vm.getFinishTime() + ")");
			send(datacenter.getId(), vm.getStartTime(), CloudSimTags.VM_CREATE_ACK, vm);
			
			if(vm.getFinishTime() != Double.POSITIVE_INFINITY) {
				//System.err.println("VM will be terminated at: "+tvm.getFinishTime());
				send(datacenter.getId(), vm.getFinishTime(), CloudSimTags.VM_DESTROY, vm);
				send(this.getId(), vm.getFinishTime(), CloudSimTags.VM_DESTROY, vm);
			}
		}
		return true;
	}
}
