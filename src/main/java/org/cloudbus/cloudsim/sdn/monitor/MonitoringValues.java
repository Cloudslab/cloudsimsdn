/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.monitor;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.sdn.Configuration;

public class MonitoringValues {
	public enum ValueType {
		Utilization_Percentage,
		DataRate_BytesPerSecond,
		General_Float,
		Time_Second,
	}
	
	private ValueType valueType;

	/**
	 * The values of the monitoring metric
	 */
	private ArrayList<Double> values;
	/**
	 * The timestamps of the monitoring metric
	 */
	private ArrayList<Double> timestamps;
	
	private double maxDurationToKeep;

	/**
	 * The constuctor of the class.
	 * 
	 * @param value
	 *            the monitoring value
	 * @param timestamp
	 *            the timestamps
	 */
	public MonitoringValues(ValueType type, double maxDurationToKeep) {
		values = new ArrayList<Double>();
		timestamps = new ArrayList<Double>();
		this.valueType = type;
		this.maxDurationToKeep = maxDurationToKeep;
	}
	
	public MonitoringValues(ValueType type) {
		this(type, Configuration.migrationTimeInterval*2);
	}
	
	private void removeOutdatedPoints(double currentTime) {
		double timeToRemove = currentTime - this.maxDurationToKeep;
		
		while(timestamps.size() > 1) {
			double nextTime = timestamps.get(1);
			if(nextTime < timeToRemove) {
				timestamps.remove(0);
				values.remove(0);
			}
			else {
				break;
			}
		}
	}
	
	public int getNumberOfPoints() {
		return values.size();
	}

	/**
	 * Add new value and timestamp to the variables.
	 * 
	 * @param value
	 *            the monitoring value
	 * @param timestamp
	 *            the timestamps
	 */
	public void add(double value, double timestamp) {
		removeOutdatedPoints(timestamp);
		
		if(values.size() >= 1 && values.get(values.size()-1) == value)
		{
			// Remove the last one (= duplicate)
			values.remove(values.size()-1);
			timestamps.remove(timestamps.size()-1);
		}
		
//		if(value > 1.5) {
//			System.err.println("Too high value!");
//		}
		values.add(value);
		timestamps.add(timestamp);
	}
	

	/**
	 * Get the values.
	 * 
	 * @return the value arrayList.
	 */
	public List<Double> getValues() {
		return values;
	}

	public double [] getValuePoints(double startTime, double endTime, double interval) {
		startTime = startTime > 0 ? startTime : 0;
		
		int numPoints = (int) Math.ceil((endTime-startTime)/interval);
		if(numPoints == 0) return null;
		
		double [] points = new double[numPoints];
		double startInterval = startTime;
		double endInterval = startTime + interval;
		int i=0, j=0;

		do {
			// Calculate the average values between start and end time
			endInterval =  endInterval > endTime ? endTime : endInterval;
			double sum = 0;
			double totalDuration = 0;
			double t_prev = startInterval;
			double average = 0; 
			
			while(i < timestamps.size()) {
				double t = timestamps.get(i);
				if(t > startInterval) {
					if(t > endInterval) {
						t = endInterval;
					}
					double v = values.get(i);
					sum += v * (t-t_prev);
					totalDuration += (t-t_prev);
					t_prev = t;
					if(t >= endInterval) {
						break;
					}
				}
				i++;
			}
			if(totalDuration != 0) {
				average = sum / totalDuration;
			}
			
			points[j++] = average;
			startInterval = endInterval;
			endInterval += interval;
		}
		while(endInterval <= endTime);
		
		return points;
	}
	

	/**
	 * Get the timestamps
	 * 
	 * @return the timestamps arrayList.
	 */
	public List<Double> getTimestamps() {
		return timestamps;
	}

	/**
	 * Set the values.
	 * 
	 * @param values
	 *            the value arrayList.
	 */
	public void setValues(ArrayList<Double> values) {
		this.values = values;
	}

	/**
	 * Set the timestamps
	 * 
	 * @param timestamps
	 *            the timestamps arrayList.
	 */
	public void setTimestamps(ArrayList<Double> timestamps) {
		this.timestamps = timestamps;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<timestamps.size(); i++) {
			if(valueType == ValueType.Utilization_Percentage)
				sb.append(String.format("%.0f:%.2f%%\n", timestamps.get(i), values.get(i)*100));
			else if(valueType == ValueType.DataRate_BytesPerSecond) {
				sb.append(String.format("%.0f:%.2f KBytesPerSeconds\n", timestamps.get(i), values.get(i)/1000));
			}
			else {
				sb.append(String.format("%.0f:%.2f\n", timestamps.get(i), values.get(i)));
			}
			
		}
		return sb.toString();
	}
	
	public double getAverageValue(double startTime, double endTime) {
		// Calculate the average values between start and end time
		double sum = 0;
		double totalDuration = 0;

		for(int i=timestamps.size()-1; i>=0; i--)
		{
			double t = timestamps.get(i);
			double t_prev =  i > 0 ? timestamps.get(i-1) : 0;

			if(t_prev < endTime)
			{
				if(t > endTime) {
					t = endTime;
				}
				if(t_prev < startTime) {
					t = startTime;
				}
				double v = values.get(i);
				sum += v * (t-t_prev);
				totalDuration += (t-t_prev);

				if(t_prev <= startTime)
				{
					break;
				}
			}
		}
		
		double average = 0; 
		if(totalDuration != 0) {
			average = sum / totalDuration;
		}
		
		return average;
	}
	
	/**
	 * Calculate the percentile of the overutilized time (Percentile of the time that utilization level was above the threshold)
	 * 
	 * @param value
	 *            the monitoring value
	 * @param timestamp
	 */
	public double getOverUtilizedPercentile(double startTime, double endTime, double overutilizedThreshold) {
		double overutilizedDuration = 0;
		double totalDuration = 0;

		for(int i=0; i<timestamps.size(); i++) {
			double t = timestamps.get(i);
			double t_prev =  i-1 >= 0 ? timestamps.get(i-1) : 0;
			if(t > startTime) {
				if(t_prev < startTime) {
					t_prev = startTime;
				}
				if(t > endTime) {
					t = endTime;
				}
				double v = values.get(i);
				if(v > overutilizedThreshold) {
					overutilizedDuration += (t-t_prev);
				}
				totalDuration += (t-t_prev);
				
				if(t >= endTime) {
					break;
				}
			}
		}
		
		double percentile = 0; 
		if(totalDuration != 0) {
			percentile = overutilizedDuration / totalDuration;
		}
		
		return percentile;
	}
}