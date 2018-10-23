/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.sdn.Arc;
import org.cloudbus.cloudsim.sdn.Middlebox;
import org.cloudbus.cloudsim.sdn.SDNVm;


public class NetworkOperatingSystemOverbookableRandom extends NetworkOperatingSystemOverbookable {

	public NetworkOperatingSystemOverbookableRandom(String physicalTopologyFilename) {
		super(physicalTopologyFilename);
	}
	
	@Override
	protected boolean deployApplication(List<Vm> vms,
			List<Middlebox> middleboxes, List<Arc> links) {
		Log.printLine(CloudSim.clock() + ": " + getName() + ": Starting deploying application..");

		// For randomize...
		Collections.shuffle(vms);
		
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
	
}
