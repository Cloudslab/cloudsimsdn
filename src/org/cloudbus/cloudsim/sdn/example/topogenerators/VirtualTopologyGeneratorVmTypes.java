/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.example.topogenerators;

/**
 * This class specifies different type of VMs that will be generated from VirtualTopoGenerator.
 * Please change the configurations of VMs (MIPs, bandwidth, etc) here.
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class VirtualTopologyGeneratorVmTypes extends VirtualTopologyGenerator{
	
	public static void main(String [] argv) {
//		int numVms = 10;
		String jsonFileName = "wiki.virtual.constant.small.json";

		VirtualTopologyGeneratorVmTypes vmGenerator = new VirtualTopologyGeneratorVmTypes();
		//vmGenerator.generate3TierTopology(numVms, jsonFileName);
		vmGenerator.generateWikiTopology(jsonFileName);
	}
	
	public void generateWikiTopology(String jsonFileName) {
		final int groupNum = 5;
		final int groupSubNum = 21;
		
		final int TIER = 3;
		final Long linkBW = (long) (125000000/3);
		
		for(int vmGroupId = 0;vmGroupId < groupNum; vmGroupId++) {
			for(int vmGroupSubId = 0;vmGroupSubId < groupSubNum; vmGroupSubId++) {
				generateVMGroup(TIER, -1, -1, linkBW, vmGroupId, vmGroupSubId); // Priority VMs
			}
		}
		
		// Create non-priority VMs.
//		for(int i = 0;i < num*4; i++) {
//			generateVMGroup(TIER, -1, -1, null);
//		}
		wrtieJSON(jsonFileName);
	}
	
	public void generate3TierTopology(int num, String jsonFileName) {
		int vmGroupId = 0;
		int vmGroupSubId = -1;
		
		final int TIER = 3;
		final Long linkBW = (long) (125000000/3);
		
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
	

	public VMSpec createVM(VMtype vmtype, double startTime, double endTime, int vmGroupId, int vmGroupSubId) {
		String name = "vm";
		int pes = 1;
		long vmSize = 1000;
		long mips=1000;
		int vmRam = 256;
		long vmBW=100000000;

		switch(vmtype) {
		case WebServer:
			//m1.large
//			mips=mips*2;
			mips=100;//2500;
//			pes=2;
			pes=1;
			name="web";
			break;
		case AppServer:
			//m2.xlarge
			mips=900;//(long) (mips*2.5);
			mips=200;//(long) (mips*2.5);
			pes=2;
			name="app";
			break;
		case DBServer:
			//c1.xlarge
			mips=500;//(long) (mips*2.5);
			mips=150;//(long) (mips*2.5);
			pes=2;
			name="db";
			break;
		case Proxy:
			mips=mips*2;
			pes=8;
			vmBW=500000000;
			name="proxy";
			break;
		case Firewall:
			mips=mips*3;
			pes=8;
			vmBW=500000000;
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
	private void addLinkAutoName(VMSpec src, VMSpec dest, Long bw) {
		String linkName = "default";
		addLink(linkName, src, dest, bw);
		
		if(bw != null) {
			linkName = src.name + dest.name;
			addLink(linkName, src, dest, bw);
		}
	}
	private void addLinkAutoNameBoth(VMSpec vm1, VMSpec vm2, Long linkBw) {
		addLinkAutoName(vm1, vm2, linkBw);
		addLinkAutoName(vm2, vm1, linkBw);
	}
	
	
	public void generateVMGroup(int numVMsInGroup, double startTime, double endTime, Long linkBw, int groupId, int subGroupId) {
		System.out.printf("Generating VM Group(%d): %f - %f\n", numVMsInGroup, startTime, endTime);
		
		switch(numVMsInGroup) {
		case 2:
		{
			VMSpec web = this.createVM(VMtype.WebServer, startTime, endTime, groupId, subGroupId);
			VMSpec app = this.createVM(VMtype.AppServer, startTime, endTime, groupId, subGroupId);
			addLinkAutoNameBoth(web, app, linkBw);
			break;
		}
		case 3:
		{
			VMSpec web = this.createVM(VMtype.WebServer, startTime, endTime, groupId, subGroupId);
			VMSpec app = this.createVM(VMtype.AppServer, startTime, endTime, groupId, subGroupId);
			VMSpec db = this.createVM(VMtype.DBServer, startTime, endTime, groupId, subGroupId);
			addLinkAutoNameBoth(web, app, linkBw);
			addLinkAutoNameBoth(app, db, linkBw);
			break;
		}
		case 4:
		{
			VMSpec web = this.createVM(VMtype.WebServer, startTime, endTime, groupId, subGroupId);
			VMSpec app = this.createVM(VMtype.AppServer, startTime, endTime, groupId, subGroupId);
			VMSpec db = this.createVM(VMtype.DBServer, startTime, endTime, groupId, subGroupId);
			VMSpec proxy = this.createVM(VMtype.Proxy, startTime, endTime, groupId, subGroupId);
			addLinkAutoNameBoth(web, app, linkBw);
			addLinkAutoNameBoth(app, db, linkBw);
			addLinkAutoNameBoth(web, proxy, linkBw);
			break;
		}
		case 5:
		{
			VMSpec web = this.createVM(VMtype.WebServer, startTime, endTime, groupId, subGroupId);
			VMSpec app = this.createVM(VMtype.AppServer, startTime, endTime, groupId, subGroupId);
			VMSpec db = this.createVM(VMtype.DBServer, startTime, endTime, groupId, subGroupId);
			VMSpec proxy = this.createVM(VMtype.Proxy, startTime, endTime, groupId, subGroupId);
			this.createVM(VMtype.Firewall, startTime, endTime, groupId, subGroupId);
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
