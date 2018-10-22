/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import org.cloudbus.cloudsim.sdn.Configuration;

public class OverbookingPercentileUtils {
	public static String PERCENTILE_FILE_NAME = "percentiles.csv";
	
	// This function returns calculated percentage from the percentile input.
	public static double translateToPercentage(String vmName, double percentile) {
		loadVmPercentileMaps();
		VmPercentileMap pMap = vmPercentileMaps.get(vmName);
		return pMap.calculateNearestPercentage(percentile);
	}
	
	static class VmPercentileMap {
		private static final int PERCENTILE_INTERVAL  = 10;
		String vmName;
		HashMap<Integer, Double> percentileMap;	//Map from percentile (from 0 to 100, integer) to percentage (0.0 ~ 1.0)
		
		VmPercentileMap(String vmName, HashMap<Integer, Double> percentileMap) {
			this.vmName = vmName;
			this.percentileMap = percentileMap;
		}

		double calculateNearestPercentage(double percentile) {
			int lb_int = getNearestLowerBound(percentile);	// lower bound
			
			if(lb_int < 0) {
				// lower bound is less than 0 percentile. error
				System.err.println("DEBUG:: calculateNearestPercentage() VM="+this.vmName	+", percentile = "+percentile+", lb = "+lb_int);
				return 0.0;				
			} else if(lb_int >= 100) {
				// upper bound is already 100. Give the 100th percentage.
				return percentileMap.get(lb_int);				
			}
			
			int ub_int = lb_int + PERCENTILE_INTERVAL;	// upper bound
			
			double lbp = percentileMap.get(lb_int);
			double ubp = percentileMap.get(ub_int);
			double lb = (double)lb_int * 0.01;
			double ub = (double)ub_int * 0.01;
			
			double calculatedPercentage = lbp + (ubp-lbp)*(percentile-lb)/(ub-lb);
			
			
			return calculatedPercentage;
		}

		private int getNearestLowerBound(double percentile) {
			int nearestLowerBound = ((int)(percentile*10))*10;
			//System.err.println("DEBUG:: getNearestLowerBound() percentile = "+percentile+", lowerbound = "+nearestLowerBound);
			return nearestLowerBound;
		}
	}
	
	private static HashMap<String, VmPercentileMap> vmPercentileMaps = null;
	
	private static void loadVmPercentileMaps() {
		// Load csv file to build VmPercentileMap data
		if(vmPercentileMaps != null) {
			return;	//already loaded
		}

		vmPercentileMaps = new HashMap<String, VmPercentileMap>();

		// 1. File open
		BufferedReader bufReader = null;
		try {
			bufReader = new BufferedReader(new FileReader(
					Configuration.workingDirectory+PERCENTILE_FILE_NAME));
			
			String line=bufReader.readLine();	// Skip the title line

			while ((line = bufReader.readLine()) != null ) {
				//System.out.println("parsing:"+line);
				
				String[] splitLine = line.replace("\"", "").split(",");
				int i = 0;
				
				String vmName = splitLine[0];
				HashMap<Integer, Double> percentileMap = new HashMap<Integer, Double>();	
				
				for(i=1; i < splitLine.length; i++) {
					int percentile = (i-1)*10;
					double percentage = Double.parseDouble(splitLine[i]);
					percentileMap.put(percentile, percentage);
				}
				
				VmPercentileMap vmMap = new VmPercentileMap(vmName, percentileMap);
				vmPercentileMaps.put(vmName, vmMap);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
}
