/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

/**
 * Constant variables to use
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class CloudSimTagsSDN {
	/** Starting constant value for network-related tags. **/
	private static final int SDN_BASE = 89000000;
	
	public static final int SDN_PACKET_COMPLETE = SDN_BASE + 1;	// Deliver Cloudlet (computing workload) to VM
	public static final int SDN_PACKET_FAILED = SDN_BASE + 2;	// Deliver Cloudlet (computing workload) to VM
	public static final int SDN_INTERNAL_PACKET_PROCESS = SDN_BASE + 3; 
	public static final int SDN_VM_CREATE_IN_GROUP = SDN_BASE + 4;
	public static final int SDN_VM_CREATE_IN_GROUP_ACK = SDN_BASE + 5;
	public static final int SDN_VM_CREATE_DYNAMIC = SDN_BASE + 6;
	public static final int SDN_VM_CREATE_DYNAMIC_ACK = SDN_BASE + 7;
	public static final int SDN_INTERNAL_CHANNEL_PROCESS = SDN_BASE + 8;

	public static final int REQUEST_SUBMIT = SDN_BASE + 10;
	public static final int REQUEST_COMPLETED = SDN_BASE + 11;
	public static final int REQUEST_OFFER_MORE = SDN_BASE + 12;
	public static final int REQUEST_FAILED = SDN_BASE + 13;
	public static final int REQUEST_SUBMIT_TO_DC = SDN_BASE + 27;

	public static final int APPLICATION_SUBMIT = SDN_BASE + 20;	// Broker -> Datacenter.
	public static final int APPLICATION_SUBMIT_ACK = SDN_BASE + 21;

	public static final int MONITOR_UPDATE_UTILIZATION = SDN_BASE + 25;
//	public static final int CHECK_MIGRATION = SDN_BASE + 26;

	private CloudSimTagsSDN() {
		throw new UnsupportedOperationException("CloudSimTags cannot be instantiated");
	}
}
