/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies;

import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Link;
import org.cloudbus.cloudsim.sdn.Node;

public class LinkSelectionPolicyFlowCapacity implements LinkSelectionPolicy {
	// Compare the total amount of dedicated bandwidth in links, and choose the least full one.
	public Link selectLink(List<Link> links, int flowId, Node src, Node dest, Node prevNode) {
		if(links.size() == 1) {
			return links.get(0);
		}
		
		int numLinks = links.size();
		int linkid = dest.getAddress() % numLinks;
		Link link = links.get(linkid);
		
		// Choose the least full one.
		for(Link l:links) {
			int linkCn = link.getChannelCount(prevNode);
			int lCn = l.getChannelCount(prevNode);
			//double linkBw = link.getAllocatedBandwidthForDedicatedChannels(prevNode);
			//double lBw = l.getAllocatedBandwidthForDedicatedChannels(prevNode);
			if( lCn < linkCn) {
				//Log.printLine(CloudSim.clock() + ": LinkSelectionPolicyFlowCapacity: Found less crowded link: " + lBw + "<" + linkBw+". old="+l+", new="+link);
				Log.printLine(CloudSim.clock() + ": LinkSelectionPolicyFlowCapacity: Found less crowded link: " + lCn + "<" + linkCn+". old="+l+", new="+link);
				link = l; 
			}
		}
		return link;
	}

	@Override
	public boolean isDynamicRoutingEnabled() {
		return true;
	}
}
