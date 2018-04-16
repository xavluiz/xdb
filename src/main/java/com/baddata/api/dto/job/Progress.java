/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.job;

import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.joda.time.DateTime;

import com.baddata.api.dto.TypedObject;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.manager.job.JobManager.JobType;

@XmlRootElement
public class Progress extends TypedObject {
	
	public static final int MIN_TASK_COUNT_FOR_PROGRESS = 100;
	
	@XmlTransient
	private Logger logger = Logger.getLogger(Progress.class);
	
	// TypedObject "createTime" and "parent"
	// can represent the job start time and the object ID
	// this job is related to.

	// 0 to 1
	private float percent = 0f;
	private boolean done = false;
	private long timeEstimate = -1l;
	private long jobTaskCount = 0l;
	private long totalElapsed = 0l;
	private long timeRemaining = 0l;
	private long initialElapsed = -1l;
	private long initialElapsedBuffer = 0l;
	private long additionalBuffer = 0l;
	private boolean initializing = true;
	private boolean updatedEstimate = false;
	private String title = "";
	private String description = "";
	private String referenceTenant = "";
	private JobType type;
	private DateTime completeTime = null;
	
	private PersistenceManager persistenceManager;
	
	public Progress() {
		// this is required to allow the search service to rebuild this object from it's index
	}
	
	public Progress(JobType type, long userRef, String referenceTenant) {
		this.type = type;
		this.title = type.getTitle();
		this.description = type.getDescrpition();
		this.referenceTenant = referenceTenant;
		this.percent = 0f;
		this.setUserRef(userRef);
		persistenceManager = PersistenceManager.getInstance();
	}
	
	public float getPercent() {
		return percent;
	}
	public void setPercent(float percent) {
		this.percent = percent;
	}
	public long getTimeEstimate() {
		return timeEstimate;
	}
	public void setTimeEstimate(long timeEstimate) {
		this.timeEstimate = timeEstimate;
	}
	public long getTimeRemaining() {
        return timeRemaining;
    }
    public void setTimeRemaining(long timeRemaining) {
        this.timeRemaining = timeRemaining;
    }
    public long getTotalElapsed() {
		return totalElapsed;
	}
	public void setTotalElapsed(long totalElapsed) {
		this.totalElapsed = totalElapsed;
	}
	public long getInitialElapsed() {
		return initialElapsed;
	}
	public void setInitialElapsed(long initialElapsed) {
		this.initialElapsed = initialElapsed;
	}
	public long getJobTaskCount() {
		return jobTaskCount;
	}
	public void setJobTaskCount(long jobTaskCount) {
		this.jobTaskCount = jobTaskCount;
	}
	
	public long getAdditionalBuffer() {
        return additionalBuffer;
    }

    public void setAdditionalBuffer(long additionalBuffer) {
        this.additionalBuffer = additionalBuffer;
    }

    public boolean isInitializing() {
		return initializing;
	}
	public void setInitializing(boolean initializing) {
		this.initializing = initializing;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getReferenceTenant() {
        return referenceTenant;
    }
    public void setReferenceTenant(String referenceTenant) {
        this.referenceTenant = referenceTenant;
    }
    @XmlElement
    public String getType() {
		return (type != null) ? type.name() : JobType.SALESFORCE_DATA.name();
	}
	public void setType(JobType type) {
		this.type = type;
	}
	
	@XmlJavaTypeAdapter(value=com.baddata.api.config.DateTimeXmlAdapter.class)
	public DateTime getCompleteTime() {
		return completeTime;
	}
	public void setCompleteTime(DateTime completeTime) {
		this.completeTime = completeTime;
	}
	
	public void setDone(boolean isdone) {
		this.done = isdone;
	}
	
	public boolean isDone() {
		done = ( !this.initializing && (this.done || this.percent == 1.0));
		return done;
	}
	
	public boolean isUpdatedEstimate() {
        return updatedEstimate;
    }

    public void setUpdatedEstimate(boolean updatedEstimate) {
        this.updatedEstimate = updatedEstimate;
    }

    public void updateEstimate(long currentElapsed) {
		// estimate time hasn't been initialized
        boolean isFirstEstimation = (initialElapsed == -1) ? true : false;
		initialElapsed = currentElapsed;
		timeEstimate = jobTaskCount * initialElapsed + additionalBuffer;
		if (isFirstEstimation) {
		    long minutes = TimeUnit.MILLISECONDS.toMinutes(timeEstimate);
    		logger.info("INITIAL PROGRESS ESTIMATE: "
    				+ "[job: " + title + ", "
    				+ "taskCount: " + jobTaskCount + ", "
    				+ "estimateInMin: " + minutes + ", "
    				+ "elapsed: " + initialElapsed + ", "
    				+ "referenceTenant: " + referenceTenant + ", "
    				+ "addtionalBufferTime: " + initialElapsedBuffer + "]");
		}
	}
	
	/**
	 * Update the progress based on the latest current progress start time.
	 * i.e. running a job that iterates 10 times. each iteration would have it's own
	 *      currentProgressStartTime
	 * 
	 * @param currentProgressStartTime
	 * @param initialBuffer
	 */
	
	public void updateProgress(long currentProgressStartTime) {

	    //
	    // Get how long this current progress took.
		long currentElapsed = System.currentTimeMillis() - currentProgressStartTime;
		
		if (currentElapsed < 100) {
			currentElapsed = 100;
		}

		if ( initialElapsed == -1 || (currentElapsed * 2) < initialElapsed ) {
		    //
		    // Time Estimate is -1, initialize the time estimate based
		    // on ellapsed time, including the initial buffer
		    //
			this.updateEstimate(currentElapsed);
			//
			// save the initial progress
			initializing = false;
		}
		
		totalElapsed += currentElapsed;
		float tmpPercent = 0f;
		if (timeEstimate > totalElapsed) {
		    //
		    // total elapsed time is still under the time estimate, update the time remaining and percent
		    //
		    tmpPercent = (float) ((double) totalElapsed / (double) timeEstimate);
		} else {
		    //
		    // the total elapsed time is greater than the current time estimate
		    // the time estimate should be bumped 10% higher, it was estimated incorrectly.
		    // OR the estimate and elapsed are equal, so 95% is fine
		    //
		    timeEstimate = totalElapsed + (long) Math.ceil((timeEstimate * .05));
		    tmpPercent = (float) ((double) totalElapsed / (double) timeEstimate);
		}
		
		// make sure we don't go over 99%. the progress object is done
		// when the boolean flag is set to true
		if ( tmpPercent > 0.99 ) {
		    tmpPercent = 0.99f;
		}
		
		if ( tmpPercent > percent ) {
		    percent = tmpPercent;
		}
		
		//
		// set the timeRemaining to the percent of the timeEstimate
		timeRemaining = timeEstimate - ((long) Math.ceil( (timeEstimate * percent) ));
		
		try {
		    persistenceManager.save(this);
		} catch (IndexPersistException e) {
			logger.error("Failed to update the progress.", e);
		}
	}
	
	public void setToCompletedProgress() {
		this.percent = 1.0f;
		this.completeTime = DateTime.now();
		this.timeRemaining = 0;
		this.totalElapsed = this.timeEstimate;
		this.done = true;
		try {
		    persistenceManager.save(this);
		} catch (IndexPersistException e) {
			logger.error("Failed to update the progress to complete.", e);
		}
	}

    @Override
    public String toString() {
        return "Progress [percent=" + percent + ", done=" + done + ", timeEstimate=" + timeEstimate + ", jobTaskCount="
                + jobTaskCount + ", totalElapsed=" + totalElapsed + ", timeRemaining=" + timeRemaining
                + ", initialElapsed=" + initialElapsed + ", initialElapsedBuffer=" + initialElapsedBuffer
                + ", initializing=" + initializing + ", updatedEstimate=" + updatedEstimate + ", title=" + title
                + ", description=" + description + ", referenceTenant=" + referenceTenant + ", type=" + type
                + ", completeTime=" + completeTime + "]";
    }
	
	

}
