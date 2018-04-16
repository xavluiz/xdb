/**
 * Copyright (c) 2018 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.util;

import org.joda.time.DateTime;

import com.baddata.manager.job.JobManager.JobType;

public class LongPollInfo {

	private DateTime lastAccessDateTime;
	private DateTime objectUpdateDateTime;
	private JobType jobType;
	private Long userReferenceId;
	private Long timeoutInSeconds;
	
	public LongPollInfo(JobType jobType, Long userReferenceId, Long timeoutInSeconds) {
		this.jobType = jobType;
		this.userReferenceId = userReferenceId;
		this.timeoutInSeconds = timeoutInSeconds;
	}
	
	/**
	 * This is used mainly before creating this object so this object can be fetched
	 * from cache or saved there.
	 * 
	 * @param jobType
	 * @param userReferenceId
	 * @return String
	 */
	public static String buildLongPollKey(JobType jobType, Long userReferenceId, Object... args) {
		String longPollKey = userReferenceId + "_" + jobType.name();
		if ( args != null && args.length > 0)  {
			for ( int i = 0; i < args.length; i++ ) {
				longPollKey += "_" + args[i].toString();
			}
		}
		return longPollKey;
	}

	public DateTime getLastAccessDateTime() {
		return lastAccessDateTime;
	}

	public void setLastAccessDateTime(DateTime lastAccessDateTime) {
		this.lastAccessDateTime = lastAccessDateTime;
	}

	public JobType getJobType() {
		return jobType;
	}

	public void setJobType(JobType jobType) {
		this.jobType = jobType;
	}

	public Long getUserReferenceId() {
		return userReferenceId;
	}

	public void setUserReferenceId(Long userReferenceId) {
		this.userReferenceId = userReferenceId;
	}

	public Long getTimeoutInSeconds() {
		return timeoutInSeconds;
	}

	public void setTimeoutInSeconds(Long timeoutInSeconds) {
		this.timeoutInSeconds = timeoutInSeconds;
	}

	public DateTime getObjectUpdateDateTime() {
		return objectUpdateDateTime;
	}

	public void setObjectUpdateDateTime(DateTime objectUpdateDateTime) {
		this.objectUpdateDateTime = objectUpdateDateTime;
	}
	
	
}
