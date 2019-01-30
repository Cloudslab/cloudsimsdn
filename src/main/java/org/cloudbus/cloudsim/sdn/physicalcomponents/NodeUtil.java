package org.cloudbus.cloudsim.sdn.physicalcomponents;

public class NodeUtil {
	private static int nodeId=0;
	public static int assignAddress() { 
		return nodeId++;
	}
}
