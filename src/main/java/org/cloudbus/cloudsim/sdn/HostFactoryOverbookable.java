package org.cloudbus.cloudsim.sdn;

import java.util.LinkedList;

import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.provisioners.BwProvisionerOverbookable;
import org.cloudbus.cloudsim.sdn.provisioners.PeProvisionerOverbookable;

public class HostFactoryOverbookable implements HostFactory {
	@Override
	public SDNHost createHost(int ram, long bw, long storage, long pes, double mips, String name) {
		LinkedList<Pe> peList = new LinkedList<Pe>();
		int peId=0;
		
		for(int i=0;i<pes;i++) {
			PeProvisionerOverbookable prov = new PeProvisionerOverbookable(mips);
			Pe pe = new Pe(peId++, prov);
			peList.add(pe);
		}
		
		RamProvisioner ramPro = new RamProvisionerSimple(ram);
		BwProvisioner bwPro = new BwProvisionerOverbookable(bw);
		VmScheduler vmScheduler = new VmSchedulerTimeSharedEnergy(peList);		
//		VmScheduler vmScheduler = new VmSchedulerTimeSharedOverSubscriptionDynamicVM(peList);		
//		VmScheduler vmScheduler = new VmSchedulerTimeSharedOverSubscriptionDynamicCloudlets(peList);
		SDNHost newHost = new SDNHost(ramPro, bwPro, storage, peList, vmScheduler, name);
		
		//vmScheduler.debugSetHost(newHost);
		
		return newHost;		
	}
}
