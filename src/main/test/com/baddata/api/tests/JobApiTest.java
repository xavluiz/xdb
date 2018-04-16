/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.tests;

import org.junit.Test;

import com.baddata.RestApiBase;
import com.baddata.api.dto.job.Progress;
import com.baddata.api.dto.page.Page;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.SearchQuery;
import com.baddata.manager.job.JobManager.JobType;
import com.google.common.collect.Lists;

import junit.framework.Assert;

public class JobApiTest extends RestApiBase {

	@Test
	public void validateJobProgressTests() throws Exception {
		
		//
		// Create a job and perisist it
		long jobTaskCount = 200;
		String referenceTenant = this.getTentantId();
		Progress progress = jobMgr.initializeProgress(JobType.SALESFORCE_DATA, this.getUserId(), referenceTenant, jobTaskCount, 0);
		
		//
		// make sure we can get this job progress by the tenant id and job id
		Progress existingProgress = (Progress) persistence.get(DbIndexType.PROGRESS_TYPE, "referenceTenant", referenceTenant);
		Assert.assertNotNull(existingProgress);
		
		long progressStart = System.currentTimeMillis();
		for (int i = 0; i < jobTaskCount; i++) {
			progressStart = System.currentTimeMillis();
        	progress.updateProgress(progressStart);
			
			Thread.sleep(10);
		}
		
		//
		// fetch the job using the "done" field and assert it's not done
		Progress notDoneProgress = (Progress) persistence.get(DbIndexType.PROGRESS_TYPE, "done", "false", null /*tenantId*/);
		Assert.assertNotNull(notDoneProgress);
		
		// look for a done job
		Progress doneProgress = (Progress) persistence.get(DbIndexType.PROGRESS_TYPE, "done", "true", null /*tenantId*/);
		Assert.assertNull(doneProgress);
		
		// look for a job not yet complete
		SearchQuery sq = new SearchQuery();
		sq.setField("done");
		sq.setPattern("false");
		SearchQuery sq2 = new SearchQuery();
		sq2.setField("type");
		sq2.setPattern(JobType.SALESFORCE_DATA.name());
		SearchSpec searchSpec = new SearchSpec(this.getUserId());
		searchSpec.setQueries(Lists.newArrayList(sq, sq2));
		Page p = (Page) persistence.get(DbIndexType.PROGRESS_TYPE, searchSpec, null /*tenantId*/);
		Progress pageProgress = (Progress) p.getItems().iterator().next();
		Assert.assertNotNull(pageProgress);
		Assert.assertEquals(false, pageProgress.isDone());
		
		progressStart = System.currentTimeMillis();
		
		System.out.println("estimate: " + progress.getTimeEstimate() + ", elapsed: " + progress.getTotalElapsed());
		// i.e. estimate: 4400, elapsed: 400
		// set the progress time back far enough to complete the job
		if ( progress.getTotalElapsed() < progress.getTimeEstimate() ) {
		    progressStart -= progress.getTimeEstimate() - progress.getTotalElapsed();
		}
		
		progress.updateProgress(progressStart);
		
		Assert.assertEquals(true, progress.getPercent() >= 0.95 && progress.getPercent() <= 0.99);
	}
}
