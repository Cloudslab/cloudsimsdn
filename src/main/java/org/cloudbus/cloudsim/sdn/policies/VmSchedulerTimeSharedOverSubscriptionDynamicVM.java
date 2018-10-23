/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmSchedulerTimeSharedOverSubscription;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.lists.PeList;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationEnergyModelHostLinear;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationHistoryEntry;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationInterface;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMonitor;

/**
 * VmSchedulerSpaceShared is a VMM allocation policy that allocates one or more Pe to a VM, and
 * doesn't allow sharing of PEs. If there is no free PEs to the VM, allocation fails. Free PEs are
 * not allocated to VMs
 * 
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class VmSchedulerTimeSharedOverSubscriptionDynamicVM extends VmSchedulerTimeSharedOverSubscription implements PowerUtilizationInterface{
	private HashMap<String, Vm> vmMap = new HashMap<String, Vm>();
	
	public VmSchedulerTimeSharedOverSubscriptionDynamicVM(List<? extends Pe> pelist) {
		super(pelist);
	}
	
	/**
	 * Check the number of cloudlets processing in each VM. Steal MIPS from idle VM to give to busy VMs 
	 */
	public double redistributeMipsDueToOverSubscriptionDynamic() {
		if(getAvailableMips() > 0)
			return 1.0;
		
		double totalRequiredMipsByAllVms = 0;
		int idlePeNum=0;

		Map<String, List<Double>> mipsMapCapped = new HashMap<String, List<Double>>();
		for (Entry<String, List<Double>> entry : getMipsMapRequested().entrySet()) {
			String vmId = entry.getKey();
			SDNVm vm = getVm(vmId);
			
			List<Double> mipsShareRequested = getNecessaryMipsForVm(vm);

			// get capped mips map for each VM
			double requiredMipsByThisVm = 0.0;
			List<Double> mipsShareRequestedCapped = new ArrayList<Double>();
			double peMips = getPeCapacity();
			for (Double mips : mipsShareRequested) {
				double cappedMips = Math.min(peMips, mips);
				if(vm.isIdle()) {
					cappedMips = 0;	// Don't give any MIPS to idle VM
					idlePeNum++;
				}
				
				mipsShareRequestedCapped.add(cappedMips);
				requiredMipsByThisVm += cappedMips;
			}
			mipsMapCapped.put(vmId, mipsShareRequestedCapped);

			if (getVmsMigratingIn().contains(entry.getKey())) {
				// the destination host only experience 10% of the migrating VM's MIPS
				requiredMipsByThisVm *= 0.1;
			}
			totalRequiredMipsByAllVms += requiredMipsByThisVm; 
		}

		double totalAvailableMips = PeList.getTotalMips(getPeList());
		double mipsForIdlePe = 0;
		double scalingFactor = totalAvailableMips / totalRequiredMipsByAllVms;
		if(scalingFactor > 1.0) {
			// Leftover mips will be distributed idle PEs
			if(idlePeNum != 0)
				mipsForIdlePe = (totalAvailableMips - totalRequiredMipsByAllVms) / idlePeNum;
			scalingFactor = 1.0;
		}

		// Clear the old MIPS allocation
		getMipsMap().clear();

		// Update the actual MIPS allocated to the VMs
		for (Entry<String, List<Double>> entry : mipsMapCapped.entrySet()) {
			String vmUid = entry.getKey();
			List<Double> requestedMips = entry.getValue();

			List<Double> updatedMipsAllocation = new ArrayList<Double>();
			for (Double mips : requestedMips) {
				if (getVmsMigratingOut().contains(vmUid)) {
					// the original amount is scaled
					mips *= scalingFactor;
					// performance degradation due to migration = 10% MIPS
					mips *= 0.9;
				} else if (getVmsMigratingIn().contains(vmUid)) {
					// the destination host only experiences 10% of the migrating VM's MIPS
					mips *= 0.1;
					// the final 10% of the requested MIPS are scaled
					mips *= scalingFactor;
				} else {
					mips *= scalingFactor;
				}
				
				if(mips == 0)
					mips = mipsForIdlePe;

				updatedMipsAllocation.add(Math.floor(mips));
			}

			// add in the new map
			getMipsMap().put(vmUid, updatedMipsAllocation);
		}
		verifyMipsAllocation();
		
		return scalingFactor;
	}
	
	protected List<Double> getNecessaryMipsForVm(SDNVm vm) {
		return getMipsMapRequested().get(vm.getUid());
	}

	protected void verifyMipsAllocation() {
		double totalAvailableMips = PeList.getTotalMips(getPeList());
		
		double allocatedMips = 0;
		for(List<Double> mpslist:getMipsMap().values()) {
			for(double mips:mpslist)
				allocatedMips += mips;
		}
		
		if(allocatedMips > totalAvailableMips) {
			System.err.println("verifyMipsAllocation: cannot allocate");
			System.exit(1);
		}
	}

	@Override
	public boolean allocatePesForVm(Vm vm, List<Double> mipsShareRequested) { 
		vmMap.put(vm.getUid(), vm);
		return super.allocatePesForVm(vm, mipsShareRequested);
	}
	
	protected SDNVm getVm(String vmId) {
		return (SDNVm) vmMap.get(vmId);
	}
	
	//////////////////////////////////////////////////////////////////////
	// Energy consumption calculation part
	//////////////////////////////////////////////////////////////////////
	
	//*
	private PowerUtilizationMonitor powerMonitor = new PowerUtilizationMonitor(new PowerUtilizationEnergyModelHostLinear());

	@Override
	public void addUtilizationEntryTermination(double terminatedTime) {
		powerMonitor.addPowerConsumption(terminatedTime, getCPUUtilization());
	}

	@Override
	public List<PowerUtilizationHistoryEntry> getUtilizationHisotry() {
		return null;
	}

	@Override
	public double getUtilizationEnergyConsumption() {
		return powerMonitor.getTotalEnergyConsumed();
	}
	
	@Override
	protected void setAvailableMips(double availableMips) {
		if(powerMonitor != null)
			powerMonitor.addPowerConsumption(CloudSim.clock(), getCPUUtilization());

		super.setAvailableMips(availableMips);
	}
	
	private double getCPUUtilization() {
		double totalMips = getPeList().size() * getPeCapacity();
		double oldMips = totalMips - getAvailableMips();
		double utilization = oldMips / totalMips;
		return utilization;
	}
	
	Host host= null;
	public void debugSetHost(Host host) {
		this.host = host;
	}
	/*/
	Host host= null;
	public void debugSetHost(Host host) {
		this.host = host;
	}

	private List<PowerUtilizationHistoryEntry> utilizationHistories = null;
	private static double powerOffDuration = 0; //if host is idle for 1 hours, it's turned off.
		
	@Override
	protected void setAvailableMips(double availableMips) {
		super.setAvailableMips(availableMips);
		addUtilizationEntry();		
	}
	
	public void addUtilizationEntryTermination(double terminatedTime) {
		if(this.utilizationHistories != null)
			this.utilizationHistories.add(new PowerUtilizationHistoryEntry(terminatedTime, 0));
	}
	
	public List<PowerUtilizationHistoryEntry> getUtilizationHisotry() {
		return utilizationHistories;
	}

	public double getUtilizationEnergyConsumption() {
		
		double total=0;
		double lastTime=0;
		double lastUtilPercentage=0;
		if(this.utilizationHistories == null)
			return 0;
		
		for(PowerUtilizationHistoryEntry h:this.utilizationHistories) {
			double duration = h.startTime - lastTime;
			double utilPercentage = lastUtilPercentage;
			double power = calculatePower(utilPercentage);
			double energyConsumption = power * duration;
			
			// Assume that the host is turned off when duration is long enough
			if(duration > powerOffDuration && lastUtilPercentage == 0)
				energyConsumption = 0;

			////////////////////////////////
			// DEBUG
			boolean debugPrint = false;
			if(host != null && host.getId() ==28)
				debugPrint = true;
			if(debugPrint && energyConsumption != 0) {
				System.err.println("DEBUG!! from: "+lastTime+", to: "+h.startTime+", utilization: "+utilPercentage+", power: "+energyConsumption/3600);
			}
			///////////////////////////////
			
			total += energyConsumption;
			lastTime = h.startTime;
			lastUtilPercentage = h.utilPercentage;
		}
		return total/3600;	// transform to Whatt*hour from What*seconds
	}
	
	private double calculatePower(double u) {
		double power = 120 + 154 * u;
		return power;
	}

	private void addUtilizationEntry() {
		double time = CloudSim.clock();
		double totalMips = getTotalMips();
		double usingMips = totalMips - this.getAvailableMips();
		if(usingMips < 0) {
			System.err.println("addUtilizationEntry : using mips is negative, No way!");
		}
		if(utilizationHistories == null)
			utilizationHistories = new ArrayList<PowerUtilizationHistoryEntry>();
		this.utilizationHistories.add(new PowerUtilizationHistoryEntry(time, usingMips/ getTotalMips()));
	}
	
	private double getTotalMips() {
		return this.getPeList().size() * this.getPeCapacity();
	}
	//*/
}
