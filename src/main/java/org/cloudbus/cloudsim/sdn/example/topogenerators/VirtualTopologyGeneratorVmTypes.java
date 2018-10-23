/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.example.topogenerators;

import java.util.Random;

/**
 * This class specifies different type of VMs that will be generated from VirtualTopoGenerator.
 * Please change the configurations of VMs (MIPs, bandwidth, etc) here.
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class VirtualTopologyGeneratorVmTypes extends VirtualTopologyGenerator{
	
	public static void main(String [] argv) {
		//String jsonFileName = "virtual.wiki.complex.json";

		VirtualTopologyGeneratorVmTypes vmGenerator = new VirtualTopologyGeneratorVmTypes();
		//vmGenerator.generatePriorityTopology(jsonFileName);
		//vmGenerator.generateWikiTopology("virtual.wiki.json");
		//vmGenerator.generateLarge3TierTopology("virtual.wiki.onlyap0.json");
		vmGenerator.generateDumb("virtual.wiki.complex.dumb.json");
		//vmGenerator.generateLarge3TierTopologyFullNetwork("virtual.wiki.complex.fullnetwork.json");
	}
	
	public void generateWikiTopology(String jsonFileName) {
		final int groupNum = 3;
		final int groupSubNum = 20;
		
		final int TIER = 3;
		final Long[] linkBW = new Long[]{125000000L/4, 125000000L/8, 125000000L/16};
		
		for(int vmGroupId = 0;vmGroupId < groupNum; vmGroupId++) {
			for(int vmGroupSubId = 0;vmGroupSubId < groupSubNum; vmGroupSubId++) {
				generateVMGroup(TIER, -1, -1, linkBW[vmGroupId], vmGroupId, vmGroupSubId); // Priority VMs
			}
		}
		wrtieJSON(jsonFileName);
	}
	
	private final int SEED = 210;
	
	class TimeGen {
		boolean isRandom;
		double setTime;
		private Random rand = null;
		private double startrange, endrange;
		
		private double _prev_start = 0;
		double maxEndTime;
		
		TimeGen(double time) {
			isRandom=false;
			setTime = time;
			maxEndTime = -1;
		}
		public TimeGen(double time, boolean isrand, Random random) {
			this.setTime = time;
			this.isRandom = isrand;
			startrange=endrange=1;
			maxEndTime = -1;
			if(isrand)
				rand = random;
		}
		public TimeGen(double time, boolean isrand, Random random, double startScale, double endScale) {
			this(time, isrand, random);
			this.startrange = startScale;			
			this.endrange = endScale;
		}
		double getStartTime() {
			if(isRandom) {
				_prev_start = round(setTime + rand.nextDouble()*startrange);
				return _prev_start;
			}
			return setTime;
		}
		double getEndTime() {
			if(isRandom) {
				double endTime= round(_prev_start + rand.nextDouble()*endrange);
				if(maxEndTime != -1) {
					endTime = Double.min(endTime, maxEndTime);
				}
				return endTime;
			}
			return setTime;
		}
		public void setMaxEndTime(double d) {
			this.maxEndTime = d;
		}
	}

	public void generateLarge3TierTopologyFullNetwork(String jsonFileName) {
		final int groupNum = 16;
		final int vmNum = 16;
		final Long[] linkBW = new Long[]{125000000L/2, 
				-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,
				-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,-1L,};
		int vmGroupId = 0;
		
		double endTime = -1;
		
		VMSpec[][] vms = new VMSpec[groupNum][vmNum];
		
		for(vmGroupId = 0;vmGroupId < groupNum; vmGroupId++) {
			Long bw = linkBW[vmGroupId];
			for(int vn=0; vn<vmNum; vn++) {
				double startTime = 0.05*(double)vn;
				vms[vmGroupId][vn]  = this.createVM(VMtype.WebServer, startTime, endTime, vmGroupId, vn, bw);

			}
		}
		
		for(vmGroupId = 0;vmGroupId < groupNum; vmGroupId++) {
			Long bw = linkBW[vmGroupId];			
			addLinkAutoNameBoth(vms[vmGroupId][0], vms[vmGroupId][1], bw); // within a pod
			
			addLinkAutoNameBoth(vms[vmGroupId][0], vms[vmGroupId][4], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][2], vms[vmGroupId][6], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][4], vms[vmGroupId][8], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][6], vms[vmGroupId][10], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][8], vms[vmGroupId][12], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][10], vms[vmGroupId][14], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][12], vms[vmGroupId][2], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][14], vms[vmGroupId][4], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][1], vms[vmGroupId][3], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][3], vms[vmGroupId][5], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][5], vms[vmGroupId][7], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][7], vms[vmGroupId][9], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][9], vms[vmGroupId][11], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][11], vms[vmGroupId][13], bw); // between pod
			addLinkAutoNameBoth(vms[vmGroupId][13], vms[vmGroupId][15], bw); // between pod
		}
		
		wrtieJSON(jsonFileName);
	}

	public void generateLarge3TierTopology(String jsonFileName) {
		final int numWeb=8;
		final int numApp=24;
		final int numDB=2;		
		
		final int groupNum = 10;
		final Long[] linkBW = new Long[]{125000000L/2, 125000000L/4, 125000000L/8, 
				125000000L/8, 125000000L/8, 125000000L/8, 125000000L/8, 125000000L/8, 125000000L/8, 125000000L/8, 125000000L/8, 125000000L/8, 125000000L/8, 125000000L/8, 125000000L/8};
		
		Random rand = new Random(SEED);
		for(int vmGroupId = 0;vmGroupId < groupNum; vmGroupId++) {
			TimeGen startTime = new TimeGen(-1);
			TimeGen endTime = new TimeGen(-1);
			
			if(vmGroupId == 0)
				startTime = new TimeGen(1.1);
			else
				startTime = new TimeGen(0, true, rand);
			
			generateVMGroupComplex(numWeb, numApp, numDB, startTime, endTime, linkBW[vmGroupId], vmGroupId);
		}
		wrtieJSON(jsonFileName);
	}
	
	private final static int PLACES = 10;
	
	private static double round(double value) {
	    int factor = PLACES;
	    long tmp = Math.round(value * factor);
	    return (double) tmp / factor;
	}
	
	public void generateDumb(String jsonFileName) {
		final int numWeb=2;
		final int numApp=0;
		final int numDB=2;

		Random rand = new Random(SEED*152);
		for(int i=0; i<10; i++) {
			int vmGroupId = 98+i;
			TimeGen startTime = new TimeGen(0, true, rand, 1, 1);
			TimeGen endTime = startTime;
			endTime.setMaxEndTime(Double.min(0.999, 0.80+0.2*(double)i));
			Long bw = 0L;
			
			generateVMGroupComplex(numWeb, numApp, numDB, startTime, endTime, bw, vmGroupId);
		}
		
		for(int i=0; i<40; i++)
		{
			// Reserve for priority ones 
			int vmGroupId = 999;
			TimeGen startTime = new TimeGen(0.1*(double)(i%10));
			TimeGen endTime = new TimeGen(0.999);
			Long bw = 0L;
			generateVMGroupComplex(1, 0, 0, startTime, endTime, bw, vmGroupId);
		}
		
		wrtieJSON(jsonFileName);
		
	}
	
	public void generatePriorityTopology(String jsonFileName) {
		final int groupNum = 3;
		final int groupSubNum = 1;
		
		final int TIER = 3;
		Long linkBW = 100000000L;;
		
		for(int vmGroupId = 0;vmGroupId < groupNum; vmGroupId++) {
			for(int vmGroupSubId = 0;vmGroupSubId < groupSubNum; vmGroupSubId++) {
				generateVMGroup(TIER, -1, -1, linkBW, vmGroupId, -1); // Priority VMs
			}
			linkBW /= 15;
		}
		wrtieJSON(jsonFileName);
	}
	public void generate3TierTopology(int num, String jsonFileName) {
		int vmGroupId = 0;
		int vmGroupSubId = -1;
		
		final int TIER = 3;
		Long linkBW = 50000000L;	// 50 MB out of 125MB capacity
		
		for(int i = 0;i < num; i++) {
			generateVMGroup(TIER, -1, -1, linkBW, vmGroupId, vmGroupSubId); // Priority VMs
			vmGroupId++;
		}
		
		// Create non-priority VMs.
//		for(int i = 0;i < num*4; i++) {
//			generateVMGroup(TIER, -1, -1, null);
//		}
		wrtieJSON(jsonFileName);
	}

	int vmNum = 0;
	
	enum VMtype {
		WebServer,
		AppServer,
		DBServer,
		Proxy,
		Firewall
	}
	

	public VMSpec createVM(VMtype vmtype, double startTime, double endTime, int vmGroupId, int vmGroupSubId, long vmBW) {
		String name = "vm";
		int pes = 1;
		long vmSize = 1000;
		long mips=1000;
		int vmRam = 256;
		//long vmBW=12000000;

		switch(vmtype) {
		case WebServer:
			//m1.large
//			mips=mips*2;
			mips=200;//2500;
//			pes=2;
			pes=4;
			name="web";
			break;
		case AppServer:
			//m2.xlarge
			mips=100;//(long) (mips*2.5);
			pes=4;
			name="app";
			break;
		case DBServer:
			//c1.xlarge
			mips=200;//(long) (mips*2.5);
			pes=4;
			name="db";
			break;
		case Proxy:
			mips=400;//(long) (mips*2.5);
			pes=4;
			name="proxy";
			break;
		case Firewall:
			mips=400;//(long) (mips*2.5);
			pes=4;
			name="firewall";
			break;
		}
		name += vmGroupId;
		if(vmGroupSubId != -1) {
			name += "-" + vmGroupSubId;
		}
		vmNum++;

		VMSpec vm = addVM(name, pes, mips, vmRam, vmSize, vmBW, startTime, endTime);
		return vm;
	}
	/*
	public VMSpec createVM(VMtype vmtype, double startTime, double endTime) {
		String name = "vm";
		int pes = 1;
		long vmSize = 1000;
		long mips=10000000;
		int vmRam = 512;
		long vmBW=100000;

		switch(vmtype) {
		case WebServer:
			//m1.large
			mips=mips*2;
			pes=2;
			name="web";
			break;
		case AppServer:
			//m2.xlarge
			mips=(long) (	*1.5);
			pes=8;
			name="app";
			break;
		case DBServer:
			//c1.xlarge
			mips=(long) (mips*2.4);
			pes=8;
			name="db";
			break;
		case Proxy:
			mips=mips*2;
			pes=8;
			vmBW=vmBW*5;
			name="proxy";
			break;
		case Firewall:
			mips=mips*3;
			pes=8;
			vmBW=vmBW*5;
			name="firewall";
			break;
		}
		name += vmGroupId;
		vmNum++;

		VMSpec vm = addVM(name, pes, mips, vmRam, vmSize, vmBW, startTime, endTime);
		return vm;
	}
	*/

	

	public void generateVMGroupComplex(int numWeb, int numApp, int numDB, TimeGen startTime, TimeGen endTime, Long linkBw, int groupId) {
		System.out.printf("Generating VM Group(%d)\n", groupId);
		VMSpec [] webs = new VMSpec[numWeb];
		VMSpec [] apps = new VMSpec[numApp];
		VMSpec [] dbs = new VMSpec[numDB];
		for(int i=0;i<numWeb;i++)
			webs[i] = this.createVM(VMtype.WebServer, startTime.getStartTime(), endTime.getEndTime(), groupId, i, linkBw);
		
		double sTime = startTime.getStartTime();
		for(int i=0;i<numApp;i++)
		{
			apps[i] = this.createVM(VMtype.AppServer, sTime, endTime.getEndTime(), groupId, i, linkBw);
			if(i%2 == 1)
				sTime = startTime.getStartTime(); //pair
		}
		for(int i=0;i<numDB;i++)
			dbs[i] = this.createVM(VMtype.DBServer, startTime.getStartTime(), endTime.getEndTime(), groupId, i, linkBw);
		
		int maxNum = Integer.max(numWeb, numApp);
		maxNum=Integer.max(maxNum, numDB);

		if(linkBw > 0) {
			for(int i=0;i<maxNum;i++)
			{
				addLinkAutoNameBoth(webs[i%numWeb], apps[i%numApp], linkBw);
				addLinkAutoNameBoth(apps[i%numApp], dbs[i%numDB], linkBw);
			}
		}
	}
	
	public void generateVMGroup(int numVMsInGroup, double startTime, double endTime, Long linkBw, int groupId, int subGroupId) {
		System.out.printf("Generating VM Group(%d): %f - %f\n", numVMsInGroup, startTime, endTime);
		
		switch(numVMsInGroup) {
		case 2:
		{
			VMSpec web = this.createVM(VMtype.WebServer, startTime, endTime, groupId, subGroupId, linkBw);
			VMSpec app = this.createVM(VMtype.AppServer, startTime, endTime, groupId, subGroupId, linkBw);
			addLinkAutoNameBoth(web, app, linkBw);
			break;
		}
		case 3:
		{
			VMSpec web = this.createVM(VMtype.WebServer, startTime, endTime, groupId, subGroupId, linkBw);
			VMSpec app = this.createVM(VMtype.AppServer, startTime, endTime, groupId, subGroupId, linkBw);
			VMSpec db = this.createVM(VMtype.DBServer, startTime, endTime, groupId, subGroupId, linkBw);
			addLinkAutoNameBoth(web, app, linkBw);
			addLinkAutoNameBoth(app, db, linkBw);
			break;
		}
		case 4:
		{
			VMSpec web = this.createVM(VMtype.WebServer, startTime, endTime, groupId, subGroupId, linkBw);
			VMSpec app = this.createVM(VMtype.AppServer, startTime, endTime, groupId, subGroupId, linkBw);
			VMSpec db = this.createVM(VMtype.DBServer, startTime, endTime, groupId, subGroupId, linkBw);
			VMSpec proxy = this.createVM(VMtype.Proxy, startTime, endTime, groupId, subGroupId, linkBw);
			addLinkAutoNameBoth(web, app, linkBw);
			addLinkAutoNameBoth(app, db, linkBw);
			addLinkAutoNameBoth(web, proxy, linkBw);
			break;
		}
		case 5:
		{
			VMSpec web = this.createVM(VMtype.WebServer, startTime, endTime, groupId, subGroupId, linkBw);
			VMSpec app = this.createVM(VMtype.AppServer, startTime, endTime, groupId, subGroupId, linkBw);
			VMSpec db = this.createVM(VMtype.DBServer, startTime, endTime, groupId, subGroupId, linkBw);
			VMSpec proxy = this.createVM(VMtype.Proxy, startTime, endTime, groupId, subGroupId, linkBw);
			this.createVM(VMtype.Firewall, startTime, endTime, groupId, subGroupId, linkBw);
			addLinkAutoNameBoth(web, app, linkBw);
			addLinkAutoNameBoth(app, db, linkBw);
			addLinkAutoNameBoth(web, proxy, linkBw);
			break;
		}
		default:
			System.err.println("Unknown group number"+numVMsInGroup);
			break;
		}
	}
	
}
