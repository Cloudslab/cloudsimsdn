/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import java.util.LinkedList;

import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.SDNHost;


public class NetworkOperatingSystemOverbookable extends NetworkOperatingSystemSimple {

	public NetworkOperatingSystemOverbookable(String physicalTopologyFilename) {
		super(physicalTopologyFilename);
	}
	
	@Override
	public SDNHost createHost(int ram, long bw, long storage, long pes, double mips) {
		LinkedList<Pe> peList = new LinkedList<Pe>();
		int peId=0;
		
		for(int i=0;i<pes;i++) {
			PeProvisionerOverbookable prov = new PeProvisionerOverbookable(mips);
			Pe pe = new Pe(peId++, prov);
			peList.add(pe);
		}
		
		RamProvisioner ramPro = new RamProvisionerSimple(ram);
		BwProvisioner bwPro = new BwProvisionerOverbookable(bw);
		VmSchedulerTimeSharedOverSubscriptionDynamicVM vmScheduler = new VmSchedulerTimeSharedOverSubscriptionDynamicVM(peList);		
//		VmScheduler vmScheduler = new VmSchedulerTimeSharedOverSubscriptionDynamicCloudlets(peList);
		SDNHost newHost = new SDNHost(ramPro, bwPro, storage, peList, vmScheduler, this);
		vmScheduler.debugSetHost(newHost);
		
		return newHost;		
	}



}
