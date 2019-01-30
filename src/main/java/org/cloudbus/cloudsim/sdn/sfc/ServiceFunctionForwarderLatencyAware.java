/*
 * Title:        CloudSimSDN + SFC
 * Description:  SFC extension for CloudSimSDN
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2018, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.sfc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;

import java.util.Comparator;

/**
 * Network packet forwarding extension for Latency-aware forwarding policy.
 * Forwarding rule: Latency-aware forwarding which gives priority to the delay-sensitive SFC policy.
 * If a SF is shared by several SFCs with different latency requirement, 
 * packets for lower latency requirement will be forwarded to the nearest SF from the source. 
 *
 * @author Jungmin Jay Son
 * @since CloudSimSDN 3.0
 */
public class ServiceFunctionForwarderLatencyAware extends ServiceFunctionForwarder {
	/** A map to store all SFC policies passing through a specific SF. 
	 *  Key: VM ID of the SF
	 *  Value: List of SFCPolicy which contains the SF of the key */
	private Map<Integer, List<ServiceFunctionChainPolicy>> policiesOfSf = null;
	
	/** A map to store measured latency for all SFC policies. 
	 *  SFCPolicy -> Duplicated SF -> Network delay between "From" and duplicated SF in SFC. 
	 *  Assumed that a SFC includes a SF only once in the chain ( SF1->SF2->SF1 not allowed because of SF1 twice ).
	 *  For example, Policy1 defines --  src:VM1 -> SF2 -> SF3 -> dst:VM4, and there are SF2-1, SF2-2 as duplicated SFs,
	 *  then latencyMap.get(Policy1).get(SF2-1) stores the latency from VM1(source of SF2) to SF2-1. */
	private Map<ServiceFunctionChainPolicy, Map<ServiceFunction, Double>> latencyMap = new HashMap<>();

	/** Preference list of duplicated SFs for the original SF (key) and the SFCpolicy. */
	private Map<ServiceFunction, Map<ServiceFunctionChainPolicy, List<ServiceFunction>>> policyPrefSf = new HashMap<>();
	
	/** Counter for every SFs including duplicated SFs. Different from super.orgSfCounter which is only for the original SF. */
	private Map<ServiceFunction, Long> sfCounter = new HashMap<>();

	/** A constant value for how many extra packets can be forwarded to the preferred SF for time-critical applications. */
	private final int ALLOWED_GAP_FOR_PRIORITY = 0; 
	
	public ServiceFunctionForwarderLatencyAware(NetworkOperatingSystem netOS) {
		super(netOS);
	}


	/**
	 * Select a SF among duplicated SFs for the SFC policy which has to go through the orgSF.
	 * If the orgSF has been duplicated, this method chooses and returns one of them.
	 * If no duplicated SFs exists, simply orgSF itself is returned.   
	 * 
	 * @param orgSF the original SF that might have duplicated SFs by auto-scale method.
	 * @param policy the SFCPolicy enforcing to pass through the orgSF. It can be used as a SF selection criteria.
	 * @return the selected SF among duplicated ones for orgSF.
	 */
	@Override
	protected ServiceFunction loadbalanceSF(ServiceFunction orgSF, ServiceFunctionChainPolicy policy) {
		List<ServiceFunction> candidateSFs = sfPool.get(orgSF);
		if(candidateSFs == null || candidateSFs.size() <= 1) // no alternative
			return orgSF; 
		
		int orgSfId = orgSF.getId();
		List<ServiceFunctionChainPolicy> passingPolicies = policiesOfSf.get(orgSfId);
		if(passingPolicies == null || passingPolicies.size() <= 1) {
			// Only this policy is passing through the SF. Use a normal load balancing method.
			return super.loadbalanceSF(orgSF, policy);
		}
		
		// Multiple policies pass through the orgSF.
		ServiceFunction selectedSF = null;
		long minCount = Long.MAX_VALUE;
		long maxCount = 0L;
		for(int i=0; i<candidateSFs.size(); i++) {
			ServiceFunction sf = candidateSFs.get(i);
			if(sfCounter.get(sf) == null)
				continue;
			
			if(sfCounter.get(sf) <= minCount) {
				minCount = sfCounter.get(sf);
				selectedSF = sf;
			}
			if(sfCounter.get(sf) > maxCount) {
				maxCount = sfCounter.get(sf);
			}
		}
		
		Map<ServiceFunctionChainPolicy, List<ServiceFunction>> m = policyPrefSf.get(orgSF);
		if(m != null && m.get(policy) != null) {
			candidateSFs = m.get(policy);
		}
		
		//int allowedGap = ALLOWED_GAP_FOR_NORMAL;
		if(passingPolicies.indexOf(policy) < passingPolicies.size()/2) { 
			// priority policy
			int allowedGap = ALLOWED_GAP_FOR_PRIORITY;
		
			for(ServiceFunction sf: candidateSFs) {
				if(sfCounter.get(sf) == null)
					continue;

				long thisCount = sfCounter.get(sf);
				if(thisCount - minCount <= allowedGap) {
					selectedSF = sf;
					
					NetworkOperatingSystem sfNOS = selectedSF.getNetworkOperatingSystem();
					if(sfNOS == null)
						sfNOS = this.nos;
					//Log.printLine("Priority policy selects:"+sfNOS.getName());
					break;
				}
			}
		}
		else {
			NetworkOperatingSystem sfNOS = selectedSF.getNetworkOperatingSystem();
			if(sfNOS == null)
				sfNOS = this.nos;
			//Log.printLine("Normal policy selects:"+sfNOS.getName());
		}
		if(selectedSF == null) {
			throw new IllegalArgumentException("Error here!");
		}
		
		sfCounter.put(selectedSF, sfCounter.get(selectedSF) +1);
		return selectedSF;
	}

	/**
	 * Create a network flow for a newly created SF that is duplicated from orgSF.
	 * This method is called after completion of creating a new VM (SF) in a data center.
	 * The function is Overrided in order to build latency map after the completion of SF duplication.
	 * 
	 * @param orgSF the original SF that was overloaded and scaled out.
	 * @param newSf the new SF created for duplicating the original VM.
	 */
	@Override
	protected void addDuplicatedPath(ServiceFunction orgSf, ServiceFunction newSf) {
		super.addDuplicatedPath(orgSf, newSf);

		if(policiesOfSf == null)
			buildSFPolicyMap();
		updateLatencyMap(orgSf, newSf);
		resetSFCounter(orgSf);
	}
	
	private void buildSFPolicyMap() {
		policiesOfSf = new HashMap<>();
		for(ServiceFunctionChainPolicy p:this.getAllPolicies()) {
			for(int sfId:p.getServiceFunctionChain()) {
				List<ServiceFunctionChainPolicy> policyList = policiesOfSf.get(sfId);
				if(policyList == null)
					policyList = new ArrayList<>();
				policyList.add(p);
				policiesOfSf.put(sfId, policyList);
			}
		}
		for(int sfId:policiesOfSf.keySet()) {
			List<ServiceFunctionChainPolicy> passingPolicies = policiesOfSf.get(sfId);
			// Sort the policies based on the expected latency (short latency = first in list = high priority) 
			Collections.sort(passingPolicies, new Comparator<ServiceFunctionChainPolicy>() {
			    public int compare(ServiceFunctionChainPolicy o1, ServiceFunctionChainPolicy o2) {
			    	return Double.compare(o1.getDelayThresholdMax(), o2.getDelayThresholdMax());
			    }
			});
		}
	}
	
	private void updatePreferedSF(ServiceFunction orgSf, ServiceFunctionChainPolicy policy) {
		Map<ServiceFunctionChainPolicy, List<ServiceFunction>> m = policyPrefSf.get(orgSf);
		if(m == null)
			m = new HashMap<>();
		List<ServiceFunction> candidateSFs = new ArrayList<>(sfPool.get(orgSf));
		
		// Sort the list based on the latency (low -> high)
		Collections.sort(candidateSFs, new Comparator<ServiceFunction>() {
		    public int compare(ServiceFunction o1, ServiceFunction o2) {
		    	return Double.compare(latencyMap.get(policy).get(o1), latencyMap.get(policy).get(o2));
		    }
		});
		
		m.put(policy, candidateSFs);
		policyPrefSf.put(orgSf, m);
	}
	
	private void updateLatencyMap(ServiceFunction orgSf, ServiceFunction newSf) {
		// Calculate estimated latencies between srcVMs in policies to candidateSFs
		List<ServiceFunctionChainPolicy> passingPolicies = policiesOfSf.get(orgSf.getId());

		for(ServiceFunctionChainPolicy passingPolicy:passingPolicies) {
			int fromId = passingPolicy.getPrevVmId(orgSf.getId());
			double latency = nos.calculateLatency(fromId, orgSf.getId(), passingPolicy.getFlowId());
			
			Map<ServiceFunction, Double> m = latencyMap.get(passingPolicy);
			if(m==null) m = new HashMap<>();
			m.put(orgSf, latency);
			latencyMap.put(passingPolicy, m);
		}
		
		for(ServiceFunctionChainPolicy passingPolicy:passingPolicies) {
			int fromId = passingPolicy.getPrevVmId(orgSf.getId());
			double latency = nos.calculateLatency(fromId, newSf.getId(), passingPolicy.getFlowId());

			Map<ServiceFunction, Double> m = latencyMap.get(passingPolicy);
			if(m==null) m = new HashMap<>();
			m.put(newSf, latency);
			latencyMap.put(passingPolicy, m);
		}
		
		for(ServiceFunctionChainPolicy passingPolicy:passingPolicies) {
			updatePreferedSF(orgSf, passingPolicy);
		}
	}
	
	private void resetSFCounter(ServiceFunction orgSf) {
		for(ServiceFunction sf: sfPool.get(orgSf)) {
			this.sfCounter.put(sf, 0L);
		}
	}

}
