/**
 * Copyright (c) 2017 by Baddata
 * All rights reserved.
 */
package com.baddata.manager.event;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DateUtils;

import com.baddata.api.dto.system.BaddataLogEvent;
import com.baddata.api.dto.user.User;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.log.EventLogger.SeverityType;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.manager.email.EmailManager;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;

public class EventLogManager {
    
    protected static Logger logger = Logger.getLogger(EventLogManager.class.getName());

    private static EventLogManager ref;
    private PersistenceManager persistence = PersistenceManager.getInstance();
    
    // timer for the PurgeAlertTask
    private Timer purgeAlertTimer = null;

    /**
     * Singleton instance
     * @return
     */
    public static EventLogManager getInstance() {
        if (ref == null) {
            synchronized(EventLogManager.class) {
                if ( ref == null ) {
                    ref = new EventLogManager();
                }
            }
        }
        return ref;
    }
    
    // private constructor to ensure singleton usage
    private EventLogManager() {
        //
    }
    
    public void startTasks() {
        
        //
        // look for notifications that haven't been sent
        //
        @SuppressWarnings("unchecked")
        List<BaddataLogEvent> logEvents = 
                (List<BaddataLogEvent>) persistence.getAllForObjectByFieldAndKeyword(
                        DbIndexType.BADDATA_LOG_EVENT_TYPE, "notified", "false");
        
        if ( CollectionUtils.isNotEmpty(logEvents) ) {
            for ( BaddataLogEvent logEvent : logEvents ) {
                User user = null;
                boolean sendEmail = false;
                if (logEvent.getSeverity().equals(SeverityType.SUPPORT_NOTICE.name())) {
                    //
                    // get the admin user
                    user = PersistenceManager.getInstance().getUserByUsername( AppUtil.get( AppConstants.ADMIN1_USERNAME ) );
                    sendEmail = true;
                }
                
                if ( sendEmail ) {
                    //
                    // Found an event that needs to be sent
                    try {
                        EmailManager.getInstance().sendEmail( user, null /*ccUser*/, logEvent.getSubject(), logEvent.getFormattedMessage() /* message */ );
                        
                        logEvent.setNotified(true);
                        persistence.save(logEvent);
                    } catch (Exception e) {
                        logger.error( "Failed to send notification email.", e);
                    }
                }
            }
        }
        
        // start the purge alerts task, but make sure it's not running first
        stopTasks();
        
        //
        // Don't run as a daemon thread. The JVM will wait for this to complete if asked to shutdown
        purgeAlertTimer = new Timer( "PurgeAlertsTask", false /*isDaemon*/ );
        // start in a minute then run once a day
        purgeAlertTimer.schedule( new PurgeAlertsTask(), DateUtils.MILLIS_PER_MINUTE /*delay*/, DateUtils.MILLIS_PER_DAY /*period*/ );

        logger.debug("PurgeAlertsTask: start(): scheduled successfully");
    }
    
    public void stopTasks() {
        if (purgeAlertTimer != null) {
            purgeAlertTimer.cancel();
        }
    }
    
    protected class PurgeAlertsTask extends TimerTask {

        @Override
        public void run() {
            
            //
            // Fetch alerts older than a month ago and acknowledge them
        }
    }
}
