/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */
 
 package org.cloudbus.cloudsim.sdn;

public class Configuration {
	public static String workingDirectory = "./";
	public static String experimentName="";
	
	//public static double minTimeBetweenEvents = 0.01;//0.01;	// in sec
	//public static int resolutionPlaces = 1;
	//public static int timeUnit = 1;	// 1: sec, 1000: msec\

	// Monitoring setup
	
	/*
	/////////////////////////////////FOR TEST ONLY
	public static final double monitoringTimeInterval = 1; // every 60 seconds, polling utilization.
	
	public static final double overbookingTimeWindowInterval = monitoringTimeInterval;	// Time interval between points 
	public static final double overbookingTimeWindowNumPoints = 5;	// How many points to track
	
	public static final double migrationTimeInterval = overbookingTimeWindowInterval*overbookingTimeWindowNumPoints; // every 1 seconds, polling utilization.

	public static final double OVERBOOKING_RATIO_MAX = 1.0; 
	public static final double OVERBOOKING_RATIO_MIN = 0.1;	// Guarantee 10%
	public static final double OVERBOOKING_RATIO_INIT = 0.5;
	
	public static final double OVERLOAD_THRESHOLD = 0.70;
	public static final double OVERLOAD_THRESHOLD_ERROR = 1.0 - OVERLOAD_THRESHOLD;
	public static final double OVERLOAD_THRESHOLD_BW_UTIL = 0.9;

	public static final double UNDERLOAD_THRESHOLD_HOST = 0.5;
	public static final double UNDERLOAD_THRESHOLD_HOST_BW = 0.5;
	public static final double UNDERLOAD_THRESHOLD_VM = 0.1;
	
	public static final double DECIDE_SLA_VIOLATION_GRACE_ERROR = 1.03; // Expected time + 5% is accepted as SLA provided
	
	public static final double OVERBOOKING_RATIO_UTIL_PORTION = 0.5;	//Give 10% more than historical utilization
	public static final double OVERLOAD_HOST_PERCENTILE_THRESHOLD = 0.3;	// If 5% of time is overloaded, host is overloaded
	
	public static final double CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT = 0.20;	// 20% of CPU resource is required to process 1 workload
	
	///*
	
	//////////////////////////// FOR Overbooking EXPERIMENT
	public static final double monitoringTimeInterval = 180; // every 60 seconds, polling utilization.
	
	public static final double overbookingTimeWindowInterval = monitoringTimeInterval;	// Time interval between points 
	public static final double overbookingTimeWindowNumPoints = 10;	// How many points to track
	
	public static final double migrationTimeInterval = overbookingTimeWindowInterval*overbookingTimeWindowNumPoints; // every 1 seconds, polling utilization.

	public static final double OVERBOOKING_RATIO_MAX = 1.0; 
	public static final double OVERBOOKING_RATIO_MIN = 0.9;
	//public static final double OVERBOOKING_RATIO_MIN = 0.4;
	public static double OVERBOOKING_RATIO_INIT = 0.7;
	
	public static final double OVERLOAD_THRESHOLD = 0.70;
	public static final double OVERLOAD_THRESHOLD_ERROR = 1.0 - OVERLOAD_THRESHOLD;
	public static final double OVERLOAD_THRESHOLD_BW_UTIL = 0.7;

	public static final double UNDERLOAD_THRESHOLD_HOST = 0.5;
	public static final double UNDERLOAD_THRESHOLD_HOST_BW = 0.5;
	public static final double UNDERLOAD_THRESHOLD_VM = 0.1;
	
	public static final double DECIDE_SLA_VIOLATION_GRACE_ERROR = 1.03; // Expected time + 5% is accepted as SLA provided
	
	public static final double OVERBOOKING_RATIO_UTIL_PORTION = (OVERBOOKING_RATIO_MAX - OVERBOOKING_RATIO_MIN)*0.2;	
	public static final double OVERLOAD_HOST_PERCENTILE_THRESHOLD = 0.3;	// If 5% of time is overloaded, host is overloaded
	
	public static final double CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT = 0.2;//0.05;	// 20% of CPU resource is required to process 1 workload
	
	public static final double HOST_ACTIVE_AVERAGE_UTIL_THRESHOLD = 0.1;

	//*/
	//////////////////////////// Default value
	public static final double CPU_SIZE_MULTIPLY = 1;	// Multiply all the CPU size for scale. Default =1 (No amplify) 
	public static final double NETWORK_PACKET_SIZE_MULTIPLY = 1;	// Multiply all the network packet size. Default =1 (No amplify) 
	
	public static final double monitoringTimeInterval = 180000; // every 1800 seconds, polling utilization.
	
	public static final double overbookingTimeWindowInterval = monitoringTimeInterval;	// Time interval between points 
	public static final double overbookingTimeWindowNumPoints = Double.POSITIVE_INFINITY;	// No migration. How many points to track
//	public static final double overbookingTimeWindowNumPoints = 10;	// How many points to track
	
	public static final double migrationTimeInterval = overbookingTimeWindowInterval*overbookingTimeWindowNumPoints; // every 1 seconds, polling utilization.
	
	public static final double OVERBOOKING_RATIO_MAX = 1.0; 
	public static final double OVERBOOKING_RATIO_MIN = 1.0;	// No overbooking
	public static double OVERBOOKING_RATIO_INIT = 1.0;	// No overbooking
	
	public static final double OVERLOAD_THRESHOLD = 1.0;
	public static final double OVERLOAD_THRESHOLD_ERROR = 1.0 - OVERLOAD_THRESHOLD;
	public static final double OVERLOAD_THRESHOLD_BW_UTIL = 1.0;
	
	public static final double UNDERLOAD_THRESHOLD_HOST = 0;
	public static final double UNDERLOAD_THRESHOLD_HOST_BW = 0;
	public static final double UNDERLOAD_THRESHOLD_VM = 0;
	
	public static final double DECIDE_SLA_VIOLATION_GRACE_ERROR = 1.0; // Expected time + 5% is accepted as SLA provided
	
	public static final double OVERBOOKING_RATIO_UTIL_PORTION = (OVERBOOKING_RATIO_MAX - OVERBOOKING_RATIO_MIN)*0.2;	
	public static final double OVERLOAD_HOST_PERCENTILE_THRESHOLD = 0.0;	// If 5% of time is overloaded, host is overloaded
	
	public static final double CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT = 1.0;//0.05;	// 20% of CPU resource is required to process 1 workload
	
	public static final double HOST_ACTIVE_AVERAGE_UTIL_THRESHOLD = 0;
	
	//*/	
	

}
