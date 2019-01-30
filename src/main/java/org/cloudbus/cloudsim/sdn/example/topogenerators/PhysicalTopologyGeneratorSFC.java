/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.example.topogenerators;

/**
 * Generate Physical topology Json file for SFC experiment
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 3.0
 */
public class PhysicalTopologyGeneratorSFC extends PhysicalTopologyGenerator {

	public static void main(String [] argv) {
		startConst();
	}

	public static void startConst() {		
		String jsonFileName = "sfc.physical.fattree.json";
		
//		int fanout = 2;
		int numPods = 8;	// Total hosts = (numPods^3)/4
		double latency = 0.1;
		
		long iops = 1000000000L;
		
		int pe = 16;
		long mips = 10000;//8000;
		int ram = 10240;
		long storage = 10000000;
		long bw = 200000000; 
		
		PhysicalTopologyGeneratorSFC reqg = new PhysicalTopologyGeneratorSFC();
		HostSpec hostSpec = reqg.createHostSpec(pe, mips, ram, storage, bw);
		reqg.createTopologyFatTree(hostSpec, iops, bw, numPods, latency);
		reqg.wrtieJSON(jsonFileName);
	}

}
