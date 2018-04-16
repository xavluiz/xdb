/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.manager.job;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.baddata.api.dto.job.Progress;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.SearchQuery;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.google.common.collect.Lists;

public class JobManager {

	private static Logger logger = Logger.getLogger(JobManager.class.getName());
	
	private static JobManager singleton;
	
	private PersistenceManager persistence;
	
	public static JobManager getInstance() {
		if ( singleton == null ) {
			synchronized (JobManager.class) {
				if ( singleton == null ) {
					singleton = new JobManager();
				}
			}
		}
		return singleton;
	}
	
	private JobManager() {
		persistence = PersistenceManager.getInstance();
		//
		// Delete all jobs to ensure clean start-up
		try {
			persistence.deleteObjectsByIndexType(DbIndexType.PROGRESS_TYPE, null /*tenantId*/);
		} catch (Exception e) {
			logger.error("Failed to delete existing jobs on webapp startup", e);
		}
	}
	
	public enum JobType {
		SALESFORCE_DATA("Salesforce Data Load", "Analyzing Salesforce Opportunities", "salesforce_data"),
		LINEARITY_DATA("Linearity", "Analyzing Sales Linearity", "linearity_data");
		
		private String title;
		private String descrpition;
		private String alias;
		
		private JobType(String title, String description, String alias) {
			this.title = title;
			this.descrpition = description;
			this.alias = alias;
		}

		public String getTitle() {
			return title;
		}

		public String getDescrpition() {
			return descrpition;
		}
		
		public String getAlias() {
			return alias;
		}
		
		public static JobType getJobType(String type) {
			type = (type == null) ? "" : type.toUpperCase();
			JobType jobType = JobType.valueOf(type);
			if ( type == null ) {
				for ( JobType jtype : JobType.values() ) {
					if ( jtype.getAlias().equalsIgnoreCase(type) ) {
						return jtype;
					}
				}
			}
			return jobType;
		}
	}
	
	public Progress initializeProgress(JobType progressType, long userReferenceId, String tenantId, long jobTaskCount, long additionalBufferInMillis) {

		Progress progress = new Progress(progressType, userReferenceId, tenantId);
		progress.setInitializing(true);
		progress.setPercent(0.01f);
		progress.setJobTaskCount(jobTaskCount);
		progress.setAdditionalBuffer(additionalBufferInMillis);
		try {
			persistence.create(progress);
		} catch (IndexPersistException e) {
			logger.error("Failed to create a new job progress.", e);
		}
		
		return progress;
	}
	
	public Progress getJob(JobType progressType, String tenantId) {
		SearchQuery sq2 = new SearchQuery();
		sq2.setField("type");
		sq2.setPattern(progressType.name());
		
		SearchQuery sq3 = new SearchQuery();
        sq3.setField("referenceTenant");
        sq3.setPattern(tenantId);
		
		SearchSpec searchSpec = new SearchSpec();
		searchSpec.setQueries(Lists.newArrayList(sq2, sq3));
		
		return (Progress) persistence.getFirstOne(DbIndexType.PROGRESS_TYPE, searchSpec, null);
	}
	
	public Progress getJobInProgress(JobType progressType, String tenantId) {
		
		List<Progress> progressList = this.getJobsOfType(progressType, tenantId);
		if ( CollectionUtils.isNotEmpty(progressList) ) {
		    for ( Progress progress : progressList ) {
		        if ( progress.getPercent() < 1.0 ) {
		            return progress;
		        }
		    }
		}
		
		return null;
	}
	
	public List<Progress> getJobsOfType(JobType progressType, String tenantId) {
		
		SearchQuery sq2 = new SearchQuery();
		sq2.setField("type");
		sq2.setPattern(progressType.name());
		
		SearchQuery sq3 = new SearchQuery();
        sq3.setField("referenceTenant");
        sq3.setPattern(tenantId);
		
		SearchSpec searchSpec = new SearchSpec();
		searchSpec.setQueries(Lists.newArrayList(sq2, sq3));
		
		List<Progress> progressList = (List<Progress>) persistence.getAllForObjectBySearchSpec(DbIndexType.PROGRESS_TYPE, searchSpec, null);
		
		return progressList;
	}

	public void deleteJob(Progress job) {
		try {
			persistence.delete(job);
		} catch (Exception e) {
			logger.error("Failed to delete an existing completed job for '" + job.getReferenceTenant() + "'", e);
		}
	}
	
	public void deleteJobsForTenant(List<JobType> jobTypes, String tenantId) {
		for (JobType jobType : jobTypes) {
			List<Progress> progressList = this.getJobsOfType(jobType, tenantId);
			if (CollectionUtils.isNotEmpty(progressList)) {
	            for (Progress progress : progressList) {
	                try {
	                    persistence.delete(progress);
	                } catch (Exception e) {
	                    logger.error("Failed to delete progress object for " + tenantId + " of type " + jobType.name(), e);
	                }
	            }
	        }
		}
	}
	
	@SuppressWarnings("unchecked")
	public Progress getJobWithLeastProgress(String tenantId) {
		Progress leastProgress = null;
		List<Progress> progressList = (List<Progress>) persistence.getAllForIndex(DbIndexType.PROGRESS_TYPE, null);
		if ( CollectionUtils.isNotEmpty(progressList) ) {
			
			for ( Progress progress : progressList ) {
			    
			    if (StringUtils.isNoneBlank(tenantId)) {
			        if (!progress.getReferenceTenant().equals(tenantId)) {
			            continue;
			        }
			    }
			    
				if ( progress.getPercent() < 1.0 && (leastProgress == null || progress.getPercent() < leastProgress.getPercent()) ) {
					leastProgress = progress;
				}
			}
		}
		return leastProgress;
	}
	
	public boolean isJobRunning(JobType progressType, String tenantId) {
		Progress progress = this.getJobInProgress(progressType, tenantId);
		return ( progress != null && progress.getPercent() < 1.0 ) ? true : false;
	}
}
