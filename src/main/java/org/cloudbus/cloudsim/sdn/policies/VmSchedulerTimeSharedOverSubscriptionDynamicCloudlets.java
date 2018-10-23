/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.SDNVm;

public class VmSchedulerTimeSharedOverSubscriptionDynamicCloudlets extends VmSchedulerTimeSharedOverSubscriptionDynamicVM {

	public VmSchedulerTimeSharedOverSubscriptionDynamicCloudlets(List<? extends Pe> pelist) {
		super(pelist);
	}
	
	@Override
	protected List<Double> getNecessaryMipsForVm(SDNVm vm) {
		List<Double> mipsNecessary = new ArrayList<Double>();
		double mipsRatio = 1.0;
		
		if(vm.getCloudletScheduler() instanceof CloudletSchedulerMonitor) {
			// Calculate total mips required for all cloudlets
			CloudletSchedulerMonitor cls = (CloudletSchedulerMonitor)vm.getCloudletScheduler();
			double maxCapacityPerCloudletPe = vm.getMips() * Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT;
			int totalPesInUse = cls.getCloudletTotalPesRequested();
			int vmPes = vm.getNumberOfPes();
			double requiredCapacityTotal = 0;
	
			if (totalPesInUse > vmPes) {
				requiredCapacityTotal = maxCapacityPerCloudletPe * totalPesInUse;
			} else {
				requiredCapacityTotal = maxCapacityPerCloudletPe * vmPes;
			}
			
			double vmCapacity = vm.getMips()*vmPes;
			mipsRatio = requiredCapacityTotal / vmCapacity;
			
			if(mipsRatio > 1 )
				mipsRatio = 1;			
		}
		
		for(int i=0; i<vm.getNumberOfPes(); i++) {
			mipsNecessary.add(vm.getMips()*mipsRatio);
		}
		
		return mipsNecessary;
	}	
}
