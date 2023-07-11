/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;
import org.cloudbus.cloudsim.sdn.workload.Transmission;

/**
 * Network data packet to transfer from source to destination.
 * Payload of Packet will have a list of activities.
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class ChanAndTrans {
	public Channel chan;
	public Transmission tr;

	public ChanAndTrans(Channel chan, Transmission tr) {
		this.chan = chan;
		this.tr = tr;
	}
}
