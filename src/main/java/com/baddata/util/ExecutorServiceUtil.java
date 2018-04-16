/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.salesforce.Opportunity;
import com.baddata.api.dto.salesforce.OpportunityFieldHistory;
import com.baddata.log.Logger;

public class ExecutorServiceUtil {
    
    private static Logger logger = Logger.getLogger(ExecutorServiceUtil.class.getName());
    
    private static ExecutorServiceUtil ref;
    
    private static ExecutorService executorService = null;
    
    private Set<Future<?>> existingFutures = new HashSet<Future<?>>();
    
    // thread schedulur
    private ScheduledExecutorService scheduleExecutor;

    /**
     * Singleton instance
     * @return
     */
    public static ExecutorServiceUtil getInstance() {
        if (ref == null) {
            synchronized(ExecutorServiceUtil.class) {
                if ( ref == null ) {
                    ref = new ExecutorServiceUtil();
                }
            }
        }
        return ref;
    }
    
    private ExecutorServiceUtil() {
        //
    }
    
    public void init() {
        // initialize the executor service member
        if ( executorService == null ) {
            // thread pool of 5 (for now)
            // FIXME: look at if we need to increase or decrease the thread pool count
            executorService = Executors.newFixedThreadPool(5, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    // create as a non-daemon threads
                    // * Where the JVM will wait for these threads to finish their tasks if asked to shutdown
                    Thread thread = Executors.defaultThreadFactory().newThread(r);
                    thread.setDaemon(false);
                    return thread;
                }
            });
        }
        
        if ( scheduleExecutor == null ) {
        	scheduleExecutor = Executors.newScheduledThreadPool(5 /*poolSize*/);
        }
    }
    
    public ScheduledExecutorService getScheduleExecutor() {
        if (this.scheduleExecutor == null) {
            this.init();
        }
    	return this.scheduleExecutor;
    }
    
    public void stop() {
    	if ( this.scheduleExecutor != null ) {
    		this.scheduleExecutor.shutdown();
    		this.scheduleExecutor = null;
    	}
    	this.shutdownThreads();
    }
    
    private void shutdownThreads() {
        if ( executorService != null ) {
            logger.info(" Stopping executor service pool threads." );
            executorService.shutdownNow();
            executorService = null;
        }
    }
    
    public Future<Void> submitVoidCallable(Callable<Void> task) {
        init();
        logger.debug("ExecutorServiceUtil executing void return callable task: '" + task.getClass().getName() + "'.");
        Future<Void> future = executorService.submit(task);
        existingFutures.add(future);
        return future;
    }
    
    public Future<TypedObject> submitObjectCallable(Callable<TypedObject> task) {
    	init();
        Future<TypedObject> future = executorService.submit(task);
        existingFutures.add(future);
        return future;
    }
    
    public Future<List<TypedObject>> submitCollectionCallable(Callable<List<TypedObject>> task) {
        init();
        Future<List<TypedObject>> future = executorService.submit(task);
        existingFutures.add(future);
        return future;
    }
    
    public Future<List<Opportunity>> submitOpportunityCallable(Callable<List<Opportunity>> task) {
        init();
        Future<List<Opportunity>> future = executorService.submit(task);
        existingFutures.add(future);
        return future;
    }
    
    public Future<List<OpportunityFieldHistory>> submitOpportunityFieldHistoryCallable(Callable<List<OpportunityFieldHistory>> task) {
        init();
        Future<List<OpportunityFieldHistory>> future = executorService.submit(task);
        existingFutures.add(future);
        return future;
    }
    
    /**
     * Shutdown after waiting for the futures to complete, ONLY if there
     * are no more futures left in the existing futures set.
     * @param futures
     */
    public void waitForFuturesAndShutdown(List<Future<?>> futures) {
        for ( Future<?> future : futures ) {
            try {
                future.get();
                existingFutures.remove(future);
            } catch (Exception e) {
                String futureClassName = (future != null)  ? future.getClass().getName() : "";
                logger.error("Failed wating for a executor service Future '" + futureClassName + "' to complete.", e);
            }
        }
        if ( existingFutures.size() == 0 ) {
            this.shutdownThreads();
        }
    }

}
