/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import java.util.List;

import org.cloudbus.cloudsim.sdn.Link;
import org.cloudbus.cloudsim.sdn.Node;

public class LinkSelectionPolicyRandom implements LinkSelectionPolicy {

	// Choose a random link regardless of the flow
	int i=0;

	public Link selectLink(List<Link> links, int flowId, Node src, Node dest, Node prevNode) {
		return links.get(i++ % links.size());
	}

	@Override
	public boolean isDynamicRoutingEnabled() {
		return false;
	}
}
