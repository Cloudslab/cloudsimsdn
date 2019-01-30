/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.nos;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionChainPolicy;
import org.cloudbus.cloudsim.sdn.virtualcomponents.FlowConfig;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

/**
 * Simple network operating system class for the example. 
 * In this example, network operating system (aka SDN controller) finds shortest path
 * when deploying the application onto the cloud. 
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class NetworkOperatingSystemSimple extends NetworkOperatingSystem {

	public NetworkOperatingSystemSimple(String name) {
		super(name);
	}
	public NetworkOperatingSystemSimple() {
		super("NOS");		
	}

	@Override
	protected boolean deployApplication(List<Vm> vms, Collection<FlowConfig> links, List<ServiceFunctionChainPolicy> sfcPolicy) {
		Log.printLine(CloudSim.clock() + ": " + getName() + ": Starting deploying application..");

		// Sort VMs in decending order of the required MIPS
		Collections.sort(vms, new Comparator<Vm>() {
		    public int compare(Vm o1, Vm o2) {
		        return (int) (o2.getMips()*o2.getNumberOfPes() - o1.getMips()*o1.getNumberOfPes());
		    }
		});
				
				
		for(Vm vm:vms)
		{
			SDNVm tvm = (SDNVm)vm;
			Log.printLine(CloudSim.clock() + ": " + getName() + ": Trying to Create VM #" + tvm.getId()
					+ " in " + datacenter.getName() + ", (" + tvm.getStartTime() + "~" +tvm.getFinishTime() + ")");
			send(datacenter.getId(), tvm.getStartTime(), CloudSimTags.VM_CREATE_ACK, tvm);
			
			if(tvm.getFinishTime() != Double.POSITIVE_INFINITY) {
				//System.err.println("VM will be terminated at: "+tvm.getFinishTime());
				send(datacenter.getId(), tvm.getFinishTime(), CloudSimTags.VM_DESTROY, tvm);
				send(this.getId(), tvm.getFinishTime(), CloudSimTags.VM_DESTROY, tvm);
			}
		}
		return true;
	}
	
	@Override
	public void processVmCreateAck(SimEvent ev) {
		super.processVmCreateAck(ev);
		
		// print the created VM info
		SDNVm vm = (SDNVm) ev.getData();
		Log.printLine(CloudSim.clock() + ": " + getName() + ": VM Created: " +  vm + " in " + vm.getHost());
		deployFlow(this.flowMapVmId2Flow.values());
	}
	
	private boolean deployFlow(Collection<FlowConfig> arcs) {
		for(FlowConfig arc:arcs) {
			vnMapper.buildForwardingTable(arc.getSrcId(), arc.getDstId(), arc.getFlowId());			
		}
		
		/*/ Print all routing tables.
		for(Node node:this.topology.getAllNodes()) {
			node.printVMRoute();
		}
		//*/
		return true;
	}
	
}
