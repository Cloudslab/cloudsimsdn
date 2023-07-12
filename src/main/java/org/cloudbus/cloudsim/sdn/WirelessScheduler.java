/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;
import org.cloudbus.cloudsim.sdn.workload.Transmission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Network data packet to transfer from source to destination.
 * Payload of Packet will have a list of activities.
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class WirelessScheduler {

	/**
	 * 一个 chankey 对应一个 timeslot 机制
	 * 不同的 chankeys 之间 timeslot 互不影响
	 */
	public static HashMap<String, List<Channel>> chanTable = new HashMap<String, List<Channel>>(); // 一个 gw->inter/inter->gw 对应多个 chan

	public WirelessScheduler() {
		chanTable.clear();
	}

	public void AddChannel(String from, String to, Channel chan) {
		String chankey = makeChanKey(from,to);
		List<Channel> list = chanTable.get(chankey);
		if (list == null){ // list不存在，新建
			list = new ArrayList<Channel>();
			list.add(chan);
			chanTable.put(chankey, list);
		} else {
			if (channelExisted(chankey, chan)) { // channel已存在
				System.err.println("\nWirelessChanTable已存在该chan!!!\n");
			} else {
				list.add(chan);
				chanTable.put(chankey, list);
			}
		}
	}
	public List<Channel> GetChanList(String chankey) {
		List<Channel> list = chanTable.get(chankey);
		return list;
	}

	private boolean channelExisted(String chankey, Channel chan) {
		List<Channel> list = chanTable.get(chankey);
		for (Channel list_i:list ) {
			if (list_i.getChId() == chan.getChId()) {
				return true;
			}
		}
		return false;
	}

	public String makeChanKey(String from, String to){
		return from + "_2_" + to;
	}

	public void PushBackAndDisableOthers(String chankey) {
		List<Channel> list = GetChanList(chankey);
		Channel chan = list.get(0);
		list.remove(chan);
		for (Channel chan_i:list) {
			chan_i.disableChannel();
		}
		chan.enableChannel();
		list.add(chan);// todo：考慮判断即将完成，若是则不放回list。
		chanTable.put(chankey, list);
	}

	public void RemoveChannel(Channel ch) {
		for (String chankey: chanTable.keySet()) {
			List<Channel> list = GetChanList(chankey);
			for (Channel chan_i:list) {
				if (chan_i.getChId() == ch.getChId()) {
					list.remove(chan_i);
					if (list.isEmpty()){
						chanTable.remove(chankey);
					} else {
						chanTable.put(chankey, list);
					}
					return;
				}
			}
		}
	}

	public boolean ChanKeyExist(String chankey) {
		List<Channel> list = chanTable.get(chankey);
		if (list == null || list.isEmpty()){
			return false;
		}
		return true;
	}
}
