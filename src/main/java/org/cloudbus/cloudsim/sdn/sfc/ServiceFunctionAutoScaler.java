/*
 * Title:        CloudSimSDN + SFC
 * Description:  SFC extension for CloudSimSDN
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2018, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.sfc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.CloudletSchedulerSpaceSharedMonitor;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

/**
 * This class defines SFC auto-scale policy. It auto-scales ServiceFunction in SFCs. 
 * NOS periodically calls scaleSFC() function during the monitoring.
 * It detects overloaded/underloaded SF chains and scales up/down the chain.
 * When SLA violation is detected, it adds more MIPS to the SF or more bandwidth to the network flow.
 * If no more resource is available from physical machine, it duplicates SF in another physical machine.
 *
 * @author Jungmin Jay Son
 * @since CloudSimSDN 3.0
 */
public class ServiceFunctionAutoScaler {

	/** NOS which will call this auto-scale policy. */
	protected NetworkOperatingSystem nos;

	/** Packet forwarder in charge of the duplicated SFs (1 forwarder per NOS). */
	protected ServiceFunctionForwarder sfForwarder;

	/** Historical data of scaled MIPS of each SF(VM). It will be used for scale down. */
	private Map<ServiceFunction, List<CPUPeMips>> vmMipsHistory = new HashMap<ServiceFunction, List<CPUPeMips>>();

	/** Historical data of scaled bandwidth for each SFC. It will be used for scale down. */
	private Map<ServiceFunctionChainPolicy, List<Long>> chainBwHistory =  new HashMap<ServiceFunctionChainPolicy, List<Long>>();
	
	public ServiceFunctionAutoScaler(NetworkOperatingSystem nos, ServiceFunctionForwarder sfForwarder) {
		this.nos = nos;
		this.sfForwarder = sfForwarder;		
	}
	
	/**
	 * Starts auto-scale policy defined in the class.
	 * This function can be called periodically or whenever auto-scale policy has to be invoked.
	 * 
	 * @return none
	 */
	public void scaleSFC() {
		if(Configuration.SFC_AUTOSCALE_ENABLE == false) {
			sfForwarder.resetSFCMonitor();
			return;
		}
		
		Map<ServiceFunction, Set<ServiceFunctionChainPolicy>> vmsToScaleUp = new HashMap<>();
		Set<ServiceFunction> vmsToScaleDown = new HashSet<ServiceFunction>();
		Set<ServiceFunctionChainPolicy> sfcToScaleUp = new HashSet<ServiceFunctionChainPolicy>();
		Set<ServiceFunctionChainPolicy> sfcToScaleDown = new HashSet<ServiceFunctionChainPolicy>();
		
		//printMonitoredValue();
		
		// Check the delay of each SF chain, if it needs to be scaled up or down.
		for(ServiceFunctionChainPolicy policy:sfForwarder.getAllPolicies()) {
			double endToEndDelay = policy.getMonitoredDelayAverage();
			int numRequests = policy.getMonitoredNumRequests();
			
			if(endToEndDelay > policy.getDelayThresholdMax()) {
				double bwUtilAverage = policy.getAverageBwUtil();
				// If delay is too long, find a problem and scale up / out BW or VM
				Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.scaleSFC(): SLA violated: "+policy+", delay="+endToEndDelay+"/"+policy.getDelayThresholdMax()
					+", req="+numRequests+", BW_util_all_chain="+bwUtilAverage);
				
				// Scale BW
				if(Configuration.SFC_AUTOSCALE_ENABLE_BW && endToEndDelay > policy.getDelayThresholdMax()) {
					List<Integer> vmIds = policy.getServiceFunctionChainIncludeVM();
					for(int i=0; i < vmIds.size()-1; i++) {
						int fromId = vmIds.get(i);
						int toId = vmIds.get(i+1);
						
						double bwUtil = policy.getAverageBwUtil(fromId, toId);
						
						if(bwUtil > policy.getBwUtilThresholdMax()) {
							Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.scaleSFC(): Network is overloded!!! BW Util: "+fromId+"->"+toId+": "
									+ bwUtil + "/" + policy.getBwUtilThresholdMax());
							
							sfcToScaleUp.add(policy);
							break;
						}
					}
				}

				// Scale VM
				if(Configuration.SFC_AUTOSCALE_ENABLE_VM) {
					List<Integer> sfc = policy.getServiceFunctionChain();
					for(int sfId:sfc) {
						ServiceFunction sf = (ServiceFunction)NetworkOperatingSystem.findVmGlobal(sfId);
						if(sf != null && isVmOverloaded(sf)) {
							Set<ServiceFunctionChainPolicy> s = vmsToScaleUp.get(sf);
							if(s==null)
								s = new HashSet<>();
							s.add(policy);
							vmsToScaleUp.put(sf, s);
						}
					}
				}
			}
			else {
				// Scale down BW
				if(Configuration.SFC_AUTOSCALE_ENABLE_SCALE_DOWN_BW) {
					boolean isScaleDown = true;
					
					List<Integer> vmIds = policy.getServiceFunctionChainIncludeVM();
					for(int i=0; i < vmIds.size()-1; i++) {
						int fromId = vmIds.get(i);
						int toId = vmIds.get(i+1);
						
						double bwUtil = policy.getAverageBwUtil(fromId, toId);
						if(bwUtil > policy.getBwUtilThresholdMin()) {
							isScaleDown = false;
							break;
						}
					}
					
					if(isScaleDown)
					{
						/*
						Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.scaleSFC(): Network is underutilized!!!" + policy+" overall util = "+policy.getAverageBwUtil());
						for(int i=0; i < vmIds.size()-1; i++) {
							int fromId = vmIds.get(i);
							int toId = vmIds.get(i+1);
							double bwUtil = policy.getAverageBwUtil(fromId, toId);
							Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.scaleSFC(): Network is underutilized!!! BW Util: "+fromId+"->"+toId+": "
									+ bwUtil + "/" + policy.getBwUtilThresholdMin());
						}*/
						sfcToScaleDown.add(policy);
					}
				}
				
				// Scale down VM
				if(Configuration.SFC_AUTOSCALE_ENABLE_SCALE_DOWN_BW) {
					List<Integer> sfc = policy.getServiceFunctionChain();
					for(int sfId:sfc) {
						ServiceFunction sf = (ServiceFunction)nos.findVmLocal(sfId);
						if(sf != null && isVmUnderloaded(sf)) {
							vmsToScaleDown.add(sf);
						}
					}
				}				
			}
		}
		
		sfForwarder.resetSFCMonitor();
		
		// Check if a VM or policy needs to be both scale UP and DOWN simultaneously (error!)
		for(ServiceFunction sfDown:vmsToScaleDown) {
			if(vmsToScaleUp.containsKey(sfDown)) {
				throw new RuntimeException("This VM is about to SCALE UP & SCALE DOWN at the same time!"+sfDown);
			}
		}
		for(ServiceFunctionChainPolicy sfcDown:sfcToScaleDown) {
			if(sfcToScaleUp.contains(sfcDown)) {
				throw new RuntimeException("This policy is about to SCALE UP & SCALE DOWN! at the same time"+sfcDown);
			}
		}

		// Process scale up or down here.
		for(ServiceFunctionChainPolicy policy:sfcToScaleUp) {
			bwScaleUp(policy);
		}
		for(ServiceFunction sf:vmsToScaleUp.keySet()) {
			Set<ServiceFunctionChainPolicy> policies = vmsToScaleUp.get(sf);
			
			Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.scaleSFC(): SF("+ sf+") is overloded!!! util ="+ getVMCpuUtilization(sf));
			vmScaleUp(sf, policies);
		}
		for(ServiceFunctionChainPolicy policy:sfcToScaleDown) {
			bwScaleDown(policy);
		}
		for(ServiceFunction sf:vmsToScaleDown) {
			Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.scaleSFC(): SF("+ sf+") is underutilized!!! util ="+ getVMCpuUtilization(sf));
			vmScaleDown(sf);
		}
	}

	private void vmScaleUp(ServiceFunction sf, Set<ServiceFunctionChainPolicy> overloadedPolicy) {
		if(Configuration.SFC_AUTOSCALE_ENABLE_VM_VERTICAL && increaseVmCapacity(sf)) {
			// Size up: give more resources to VM
			Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.vmScaleUp(): VM("+sf+") capacity is increased to:"+
			sf.getNumberOfPes()+"*"+sf.getMips());
		}
		else {
			// Scale out: create an extra VM to load-balance
			Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.vmScaleUp(): New VM will be added for:"+sf);
			ServiceFunction newSf = duplicateSF(sf, overloadedPolicy);
			sfForwarder.addDuplicatedSF(sf, newSf);
			sfForwarder.redistributeDuplicatedPathBandwidthAllChain(sf);
		}
		
	}
	
	private void bwScaleUp(ServiceFunctionChainPolicy policy) {
		long newBw = getSFCBandwidthStepUp(policy);
		pushSFCBandwidthStepUp(policy);
		Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.bwScaleUp(): BW update:"+policy+" (New BW:"+newBw+")");
		
		changePolicyBandwidth(policy, newBw);
	}
	
	private void vmScaleDown(ServiceFunction sf) {
		CPUPeMips newMips = popVmMipsStepDown(sf);
		
		if(newMips != null) {
			Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.vmScaleDown(): Scale down SF:"+sf+" -> new MIPS:"+newMips);
			List<ServiceFunction> allSf = sfForwarder.getAllDuplicatedSF(sf);
			for(ServiceFunction subSf:allSf) {
				NetworkOperatingSystem runningNOS = this.nos;
				if(subSf.getNetworkOperatingSystem() != null)
					runningNOS = subSf.getNetworkOperatingSystem();
				
				runningNOS.updateVmMips(subSf, newMips.numberOfPes, newMips.mips);
			}
		}
		else {
			// Cannot scale down any more. Scale in.
			boolean removed = sfForwarder.removeDuplicatedSF(sf);
			if(removed) {
				Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.vmScaleDown(): One VM is deleted for SF:"+sf);
				sfForwarder.redistributeDuplicatedPathBandwidthAllChain(sf);
			}
		}
	}
	
	private void bwScaleDown(ServiceFunctionChainPolicy policy) {
		long newBw = popSFCBandwidthStepDown(policy);
		if(newBw != -1) {
			// Give less bandwidth to the channels.
			Log.printLine(CloudSim.clock() + ": ServiceFunctionAutoScaler.bwScaleDown(): SFC:"+policy+" -> new BW:"+newBw);
			changePolicyBandwidth(policy, newBw);
		}
		
	}

	private boolean increaseVmCapacity(ServiceFunction sf) {
		CPUPeMips newMips = getVmMipsStepUp(sf); // increase # of PEs
		boolean isHostAvailable = true;
		
		List<ServiceFunction> allSf = sfForwarder.getAllDuplicatedSF(sf);
		for(ServiceFunction subSf:allSf) {
			if(isHostAvailable(subSf, newMips.numberOfPes, newMips.mips) == false) {
				isHostAvailable = false;
			}
		}
		
		if(isHostAvailable) {
			pushVmMipsStepUp(sf);
			
			// Let Datacenter to replace the old VM with the new one.
			for(ServiceFunction subSf:allSf) {
				NetworkOperatingSystem sfNos = subSf.getNetworkOperatingSystem();
				if(sfNos == null)
					sfNos = this.nos;
				sfNos.updateVmMips(subSf, newMips.numberOfPes, newMips.mips);
			}
			return true;
		}
		
		return false;
	}
	
	private boolean isHostAvailable(ServiceFunction sf, int newPe, double newMips) {
		// This function only tests if the host can allocate newMips to the VM
		int orgPes = sf.getNumberOfPes();
		double orgMips = sf.getMips();
		SDNHost host = (SDNHost) sf.getHost();
		
		// Find if the new VM is fit in?
		host.vmDestroy(sf);
		sf.updatePeMips(newPe, newMips);
		boolean isHostAvailable = host.isSuitableForVm(sf);
		
		sf.updatePeMips(orgPes, orgMips); // restore
		host.vmCreate(sf);
		
		return isHostAvailable;
	}
	
	private ServiceFunction duplicateSF(ServiceFunction orgSf, Set<ServiceFunctionChainPolicy> overloadedPolicy) {
		// Create an identical VM for scale out.
		CloudletScheduler clSch = new CloudletSchedulerSpaceSharedMonitor(Configuration.TIME_OUT);
		ServiceFunction newSF = new ServiceFunction(
				SDNVm.getUniqueVmId(),
				orgSf.getUserId(), orgSf.getMips(), orgSf.getNumberOfPes(), orgSf.getRam(),
				orgSf.getBw(), orgSf.getSize(), orgSf.getVmm(), clSch,
				orgSf.getStartTime(), orgSf.getFinishTime());
		
		newSF.setName(orgSf.getName()+"-dup"+newSF.getId());
		newSF.setMIperOperation(orgSf.getMIperOperation());
		newSF.setMiddleboxType(orgSf.getMiddleboxType());
		
		return newSF;
	}
	
	private void changePolicyBandwidth(ServiceFunctionChainPolicy policy, long newBw) {
		policy.setTotalBandwidth(newBw);
		sfForwarder.redistributeDuplicatedPathBandwidth(policy);
	}
	
	/*
	private double getChannelUtilization(Channel ch) {
		double currentTime = CloudSim.clock();
		double startTime = currentTime - Configuration.migrationTimeInterval;
		return ch.getMonitoredUtilization(startTime, currentTime);
	}
	*/
	
	private double getVMCpuUtilization(SDNVm vm) {
		double currentTime = CloudSim.clock();
		double startTime = currentTime - Configuration.migrationTimeInterval;
		return vm.getMonitoredUtilizationCPU(startTime, currentTime);
	}
	
	public boolean isVmOverloaded(SDNVm vm) {
		double util = getVMCpuUtilization(vm);
		if( util > Configuration.SFC_OVERLOAD_THRESHOLD_VM )
			return true;

		return false;
	}
	
	public boolean isVmUnderloaded(SDNVm vm) {
		double util = getVMCpuUtilization(vm);
		if( util < Configuration.SFC_UNDERLOAD_THRESHOLD_VM )
			return true;

		return false;
	}
	
	private CPUPeMips getVmMipsStepUp(ServiceFunction sf) {
		double orgMips = sf.getMips();
		double newMips = orgMips * 4;//2; // does not change
		int orgPe = sf.getNumberOfPes();
		int newPe = orgPe + sf.getInitialNumberOfPes();
		
		return new CPUPeMips(newPe, newMips);
	}
	
	private void pushVmMipsStepUp(ServiceFunction sf) {
		List<CPUPeMips> mipsHistory = this.vmMipsHistory.get(sf);
		if(mipsHistory == null) {
			mipsHistory = new ArrayList<CPUPeMips>();
		}
		mipsHistory.add(new CPUPeMips(sf));	// add the original mips
		
		this.vmMipsHistory.put(sf, mipsHistory);
		
	}
	
	private CPUPeMips popVmMipsStepDown(ServiceFunction sf) {
		List<CPUPeMips> mipsHistory = this.vmMipsHistory.get(sf);
		if(mipsHistory == null || mipsHistory.size() == 0) {
			return null;
		}
		CPUPeMips lastMips = mipsHistory.remove(mipsHistory.size()-1); // Get the last mips
		return lastMips;
		
	}
	
	private long getSFCBandwidthStepUp(ServiceFunctionChainPolicy policy) {
		//long orgBw = nos.getRequestedBandwidth(policy.getSrcId(), policy.getDstId(), policy.getFlowId());
		long orgBw = policy.getTotalBandwidth();
		long newBw = orgBw + policy.getInitialBandwidth();
		return newBw;
	}
	
	private void pushSFCBandwidthStepUp(ServiceFunctionChainPolicy policy) {
		List<Long> bwHistory = this.chainBwHistory.get(policy);
		if(bwHistory == null) {
			bwHistory = new ArrayList<Long>();
		}
		bwHistory.add(policy.getTotalBandwidth());	// add the original mips
		
		this.chainBwHistory.put(policy, bwHistory);
		
	}
	
	private long popSFCBandwidthStepDown(ServiceFunctionChainPolicy policy) {
		List<Long> bwHistory = this.chainBwHistory.get(policy);
		if(bwHistory == null || bwHistory.size() == 0) {
			return -1;
		}
		long lastBw = bwHistory.remove(bwHistory.size()-1); // Get the last mips
		return lastBw;		
	}
	
	protected void printMonitoredValue() {
		for(ServiceFunctionChainPolicy policy:this.sfForwarder.getAllPolicies()) {
			List<Integer> vmIds = policy.getServiceFunctionChainIncludeVM();
			for(int i=0; i < vmIds.size()-1; i++) {
				// Build channel chain: SrcVM ---> SF1 ---> SF2 ---> DstVM
				int fromId = vmIds.get(i);
				int toId = vmIds.get(i+1);
				
				if(i != 0) {
					SDNVm vm = (SDNVm)NetworkOperatingSystem.findVmGlobal(fromId);  
					double cpuUtil = getVMCpuUtilization(vm);
					System.err.println("SFC VM Utilization:"+vm+", util="+cpuUtil);
				}
				
				double bwUtil = policy.getAverageBwUtil(fromId, toId);
				System.err.println("SFC Bw Utilization: "+fromId+"->"+toId+" , util="+bwUtil);
			}
		}
	}
	
	class CPUPeMips {
		double mips;
		int numberOfPes;
		
		public CPUPeMips(int numberOfPes, double mips) {
			this.mips = mips;
			this.numberOfPes = numberOfPes;
		}

		public CPUPeMips(SDNVm vm) {
			this(vm.getNumberOfPes(), vm.getMips());
		}
		
		public String toString() {
			return "[ "+numberOfPes+"*"+mips+" ]";
		}
	}
}
