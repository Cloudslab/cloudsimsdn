package org.cloudbus.cloudsim.sdn.workload;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class WorkloadResultWriterThread implements Runnable {
	private BlockingQueue<Workload> toWrite = new LinkedBlockingQueue<Workload>();
	private WorkloadResultWriter wrw;
	
	public WorkloadResultWriterThread(WorkloadResultWriter wrw) {
		this.wrw = wrw;
	}
	
	public void setExit() {
		enqueue(new Workload(-999, null));
	}
	
	public void enqueue(Workload wl) {
		toWrite.add(wl);
	}
	
	@Override
	public void run() {
		Workload wl;
		
		while(true)
		{
			try {
				wl = toWrite.take();
				if(wl.workloadId == -999)
					return;
				
				wrw.printWorkload(wl);
				
			} catch (InterruptedException e) {
				System.err.println("Intruptted");
				e.printStackTrace();
			} 
		}
		
		
	}
}
