package org.cloudbus.cloudsim.sdn;

import org.cloudbus.cloudsim.core.CloudSim;

public class CloudSimEx extends CloudSim {
	public static int getNumFutureEvents() {
		return future.size();
	}
}
