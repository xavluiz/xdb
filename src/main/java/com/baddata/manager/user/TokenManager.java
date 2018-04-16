/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.manager.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DateUtils;

import com.baddata.api.dto.ApiDto;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.user.AuthenticationToken;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.RangeQuery;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.util.ExecutorServiceUtil;


public class TokenManager {
    
    protected static Logger logger = Logger.getLogger(TokenManager.class.getName());
    
    private static TokenManager ref;
    private boolean initializedScheduler = false;
    
    /**
     * Singleton instance
     * @return
     */
    public static TokenManager getInstance() {
        if (ref == null) {
            synchronized(TokenManager.class) {
                if ( ref == null ) {
                    ref = new TokenManager();
                }
            }
        }
        return ref;
    }
    
    // private constructor to ensure singleton usage
    private TokenManager() {
    	if ( !initializedScheduler ) {
    		
    		//
    		// Create the executor service
//    		long initialDelay = DateUtils.MILLIS_PER_MINUTE;
//    		long subsequentDelay = DateUtils.MILLIS_PER_DAY; // poll once a day
//    		TimeUnit delayUnit = TimeUnit.MILLISECONDS;
//    		ExecutorServiceUtil.getInstance().getScheduleExecutor().scheduleWithFixedDelay(
//    				new TokenCleanupTask(), initialDelay, subsequentDelay, delayUnit);
    		initializedScheduler = true;
    	}
    }

    public class TokenCleanupTask implements Runnable {
    
        @Override
        public void run() {
    
            // wrap a try catch so that death of sync data won't kill the scheduler
            try {
                //
                // fetch the forgot password tokens and delete any that are older than a day
                //
                SearchSpec searchRequest = new SearchSpec(null /*userReferenceId*/);
                RangeQuery rq = new RangeQuery("expirationdate", System.currentTimeMillis() /*max*/);
                searchRequest.setRangeQueries(new ArrayList<RangeQuery>(Arrays.asList(rq)));
                
                PersistenceManager persitenceMgr = PersistenceManager.getInstance();
        
                Page response = persitenceMgr.get(DbIndexType.AUTHENTICATION_TOKEN_TYPE, searchRequest);
        
                if (response != null && response.getItemCount() > 0) {
                    for (ApiDto tokenDTO : response.getItems()) {
                        
                        String token = ((AuthenticationToken) tokenDTO).getToken();
                        String tokenEmail = ((AuthenticationToken) tokenDTO).getEmail();
                        try {
                            persitenceMgr.delete( ((AuthenticationToken)tokenDTO) );
                        } catch (IndexPersistException e) {
                            logger.error("Failed to delete token " + token + " for user " + tokenEmail + ".", e);
                        }
                    }
                }
            } catch ( Throwable e ) {
                logger.error("Error running TokenCleanupTimer.", e);
            }
        }
    
    }
    
}
