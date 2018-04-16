/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.broker;

import com.baddata.api.dto.job.Progress;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.manager.job.JobManager;

public class JobBrokerImpl extends BaseBroker {

	private JobManager jobMgr = null;
	private PersistenceManager persistence = null;

	public JobBrokerImpl(Long userref, String userName) {
		super(userref, userName);
		init();
	}

	protected void init() {
		if ( jobMgr == null ) {
			jobMgr = JobManager.getInstance();
		}
		if ( persistence == null ) {
			persistence = PersistenceManager.getInstance();
		}
	}

	public Progress getProgressById(String jobId) {
		return (Progress) persistence.getById(DbIndexType.PROGRESS_TYPE, Long.getLong(jobId));
	}

	public Progress getProgress(SearchSpec searchSpec) {

		String instanceTenantId = searchSpec.getTenantId();

		Progress progress = jobMgr.getJobInProgress(searchSpec.getJobType(), instanceTenantId);
		if ( progress == null ) {
			progress = jobMgr.getJob(searchSpec.getJobType(), instanceTenantId);
		}

		if ( progress != null && progress.isDone() ) {
			// it's done, delete it and return out
			jobMgr.deleteJob(progress);
		}

		return progress;
	}

}
