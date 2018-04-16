/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.manager.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.joda.time.DateTime;

import com.baddata.api.config.BigDecimalJsonDeserializer;
import com.baddata.api.config.DateTimeJsonDeserializer;
import com.baddata.api.config.JsonTypedBeanDeserializer;
import com.baddata.api.config.ZonedDateTimeJsonDeserializer;
import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.system.AuditLogApiInfo;
import com.baddata.api.dto.system.AuditLogErrorInfo;
import com.baddata.api.dto.system.AuditLogPropertyStat;
import com.baddata.api.dto.system.AuditLogStat;
import com.baddata.api.dto.system.LogMonitor;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.exception.BaddataException;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.util.AppConstants;
import com.baddata.util.DateUtil;
import com.baddata.util.FileUtil;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public class BaddataLogManager {

    private static Logger logger = Logger.getLogger(BaddataLogManager.class.getName());
    
    private static BaddataLogManager singleton;
    
    private Timer monitorTimer = null;
    private Timer logStatBuilderTimer = null;
    
    // Checks the log every 9 seconds
    private final long NINE_SEC_INTERVAL = 1000 * 9;
    // Build stats every hour
    private final long ONE_HOUR_INTERVAL = DateUtils.MILLIS_PER_HOUR;
    
    private AtomicBoolean monitorTimeScheduled = new AtomicBoolean( false );
    private AtomicBoolean statBuilderTimerScheduled = new AtomicBoolean( false );
    
    private PersistenceManager persistence;
    protected static Gson gson;
    
    // log monitor tunables
    public static long logMonitorDelay = DateUtils.MILLIS_PER_MINUTE;
    public static AtomicBoolean runningLogMonitor = new AtomicBoolean( false );
    
    // stat builder tunables
    public static long statBuilderDelay = DateUtils.MILLIS_PER_MINUTE * 2;
    public static AtomicBoolean runningStatBuilder = new AtomicBoolean( false );
    
    public static BaddataLogManager getInstance() {
        if ( singleton == null ) {
            synchronized (BaddataLogManager.class) {
                if ( singleton == null ) {
                    singleton = new BaddataLogManager();
                }
            }
        }
        return singleton;
    }
    
    private BaddataLogManager() {
        persistence = PersistenceManager.getInstance();
    }
    
    public synchronized void startTasks() {
        //
        // *** Commented out to disable tasks from executing for now ***
        //
        
        if ( !monitorTimeScheduled.get() ) {
        
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter( DateTime.class, new DateTimeJsonDeserializer() );
            gsonBuilder.registerTypeAdapter( BigDecimal.class, new BigDecimalJsonDeserializer() );
            gsonBuilder.registerTypeAdapter( TypedObject.class, new JsonTypedBeanDeserializer<TypedObject>() );
            gsonBuilder.registerTypeAdapter( ZonedDateTime.class, new ZonedDateTimeJsonDeserializer() );
            gson = gsonBuilder.create();
    
            // monitorTimer = new Timer( "monitor-log", false /*isDaemon*/ );
            // monitorTimer.schedule( new MonitorLogTask(), logMonitorDelay /*delay*/, NINE_SEC_INTERVAL /*period*/ );
    
            // monitorTimeScheduled.set( true );
            // logger.debug("MonitorLog: start(): scheduled successfully");
        }
        
        if ( !statBuilderTimerScheduled.get() ) {
            
            // logStatBuilderTimer = new Timer( "log-stat-builder", false /*isDaemon*/ );
            // logStatBuilderTimer.schedule( new LogStatBuilderTask(), statBuilderDelay /*delay*/, ONE_HOUR_INTERVAL /*period*/ );
            
            // statBuilderTimerScheduled.set( true );
            // logger.debug("LogStatBuilder: start(): scheduled successfully");
        }
    }
    
    public synchronized void stopTasks() {
        if ( monitorTimer != null ) {
            monitorTimer.cancel();
            monitorTimeScheduled.set( false );
        }
        
        if ( logStatBuilderTimer != null ) {
            logStatBuilderTimer.cancel();
            statBuilderTimerScheduled.set( false );
        }
    }
    
    protected class LogStatBuilderTask extends TimerTask {

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            runningStatBuilder.set(true);
            
            try {
            
                SearchSpec searchSpec = new SearchSpec(true);
                searchSpec.setLimit(1000);
                searchSpec.setPage(1);
                Page p = persistence.get(DbIndexType.AUDIT_LOG_API_INFO_TYPE, searchSpec);
                
                Set<String> sessionSet = Sets.newHashSet();
                Set<Long> userRefSet = Sets.newHashSet();
                DateTime statStart = null;
                
                if ( p != null ) {
                    AuditLogStat logStat = new AuditLogStat();
                    while ( p.getItemCount() > 0 ) {
                        //
                        // Add the typed items found in the page
                        List<AuditLogApiInfo> apiInfoList = (List<AuditLogApiInfo>) p.getItems();

                        boolean persistedStats = false;
                        long avgCount = 0;
                        for ( AuditLogApiInfo apiInfo : apiInfoList ) {
                            
                            long createTime = apiInfo.getCreateTime().getMillis();
                            if ( statStart == null ) {
                                statStart = new DateTime(createTime);
                            }
                            
                            String sessionId = apiInfo.getSessionId();
                            Long userRef = apiInfo.getUserRef();
                            String url = apiInfo.getUrl();
                            
                            //
                            // Add to the session set
                            if ( !sessionSet.contains(sessionId) ) {
                                sessionSet.add(sessionId);
                            }
                            
                            //
                            // Add to the user ref set
                            if ( userRef != null && !userRefSet.contains(userRef) ) {
                                userRefSet.add(userRef);
                            }

                            long timeSinceStart = createTime - statStart.getMillis();
                            
                            // increment the average counter
                            avgCount += 1;
                            
                            // update the average duration
                            logStat.setAvgDuration( logStat.getAvgDuration() + apiInfo.getElapsed() );
                            
                            //
                            // Set the API Count per api
                            //
                            AuditLogPropertyStat apiStat = logStat.getApiStatsMap().get( url );
                            if (apiStat == null) {
                                // it's not there, add it
                                apiStat = new AuditLogPropertyStat();
                                apiStat.setName("API Count");
                                apiStat.setDescription( "Total count per API" );
                                logStat.getApiStatsMap().put( url, apiStat );
                            }
                            // update the apiStat by reference now
                            apiStat.setCount( apiStat.getCount() + 1 );
                            
                            if ( timeSinceStart > DateUtils.MILLIS_PER_HOUR ) {

                                //
                                // Falls into the hourly bucket,
                                // save the current stat info and reset the set
                                //
                                logStat.setUniqueSessions(sessionSet.size());
                                logStat.setUniqueUsers(userRefSet.size());
                                
                                // complete the average duration
                                logStat.setAvgDuration(logStat.getAvgDuration() / avgCount);
                                
                                // persist
                                try {
                                    persistence.create(logStat);
                                    persistedStats = true;
                                } catch (IndexPersistException e) {
                                    logger.error("Failed to create audit log stat.", e);
                                }
                                
                                break;
                            }
                        } // end for loop
                        
                        if (!persistedStats) {
                            //
                            // Falls into the hourly bucket,
                            // save the current stat info and reset the set
                            //
                            logStat.setUniqueSessions(sessionSet.size());
                            logStat.setUniqueUsers(userRefSet.size());
                            
                            // complete the avg duration
                            logStat.setAvgDuration(logStat.getAvgDuration() / avgCount);
                            
                            // persist
                            try {
                                persistence.create(logStat);
                            } catch (IndexPersistException e) {
                                logger.error("Failed to create audit log stat.", e);
                            }
                        }
                        
                        if ( p.getPage() < p.getPages() ) {
                            
                            //
                            // Reset the sets
                            //
                            logStat = new AuditLogStat();
                            sessionSet = Sets.newHashSet();
                            avgCount = 0;
                            statStart = null;
                            
                            // fetch again
                            searchSpec.setPage(searchSpec.getPage() + 1);
                            p = persistence.get(DbIndexType.AUDIT_LOG_API_INFO_TYPE, searchSpec);
                        } else {
                            
                            // done with paging through the results
                            break;
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Failure running log stat builder.", e);
            } finally {
                runningStatBuilder.set(false);
            }
        }
        
    }
    
    /**
     * 
     * Monitors the log entries
     *
     */
    protected class MonitorLogTask extends TimerTask {
        
        private long charactersAlreadyRead = 0; // number of characters to skip
        private long lastMessageTimestamp = 0;

        @Override
        public void run() {
            runningLogMonitor.set(true);
            try {
                String useTestLogVal = System.getProperty(AppConstants.USE_TEST_LOG, "false");
                boolean useTestLog = Boolean.parseBoolean(useTestLogVal);
                if (!useTestLog) {
                    this.processLog( FileUtil.getLogFile() );
                } else {
                    this.processLog( FileUtil.getLocalTestLogFile() );
                }
            } finally {
                runningLogMonitor.set(false);
            }
        }
        
        protected void processLog(File logFile) {
            
            LogMonitor logMonitor = (LogMonitor) persistence.getFirstObject( DbIndexType.LOG_MONITOR_TYPE, null /* tenantId */ );
            if ( logMonitor == null ) {
                logMonitor = new LogMonitor();
                try {
                    persistence.create( logMonitor );
                    logMonitor = (LogMonitor) persistence.getFirstObject( DbIndexType.LOG_MONITOR_TYPE, null /* tenantId */ );
                } catch ( IndexPersistException e ) {
                    logger.trace( "Failed to create the LogMonitor index, error: " + ExceptionUtils.getStackTrace( e ) );
                    return;
                }
            }
            lastMessageTimestamp = logMonitor.getLastMessageTimestamp();
            charactersAlreadyRead = logMonitor.getCharactersAlreadyRead();
            
            BufferedReader br = null;

            try {

                long logFileLength = logFile.length();
                if ( logFileLength < charactersAlreadyRead ) {
                    // logs has been rotated, let's read from the beginning of the file again
                    charactersAlreadyRead = 0;
                }

                FileReader fileReader = new FileReader( logFile );
                br = new BufferedReader( fileReader );

                // skip over logs already processed
                if ( charactersAlreadyRead > 0 ) {
                    br.skip(charactersAlreadyRead - 1); // removing trailing new line
                    br.readLine(); // drop partial line
                }

                long newMessageTimestamp = lastMessageTimestamp;
                String line = br.readLine();
                boolean fetchingErrorTrace = false;
                AuditLogErrorInfo errorInfo = null;
                StringBuffer errorSb = null;
                while ( line != null ) {
                    //
                    // Process the log line. We'll persist or send an alert for log entries that
                    // match specific cases, such as a run time exception which we'll send
                    // an alert to support to look into.  Or basic audit logging entries, we'll
                    // persist info to show a dashboard of the API usage.
                    //
                    if (StringUtils.isNotBlank(line)) {
                        long ts = this.getTimestamp(line);
                        
                        
                        int auditLogIndex = line.indexOf(AppConstants.AUDIT_API_TAG);
                        int errorIdx = line.indexOf(AppConstants.AUDIT_ERROR_TAG);
                        
                        if ( auditLogIndex != -1 ) {
                            this.processAuditApiLogLine(line, auditLogIndex);
                            if (fetchingErrorTrace) {
                                // persist what we have
                                this.completeErrorInfoLog(errorInfo, errorSb.toString());
                                
                                fetchingErrorTrace = false;
                            }
                        } else if ( errorIdx != -1 ) {
                            
                            if (fetchingErrorTrace) {
                                // persist what we have
                                this.completeErrorInfoLog(errorInfo, errorSb.toString());
                            }
                            
                            fetchingErrorTrace = true;
                            errorInfo = new AuditLogErrorInfo();
                            errorSb = new StringBuffer();
                            String errorMsg = line.substring(errorIdx + AppConstants.AUDIT_ERROR_TAG.length()).trim();
                            errorInfo.setMessage(errorMsg);
                        } else if (ts == -1 && fetchingErrorTrace) {
                            // check if this is a timestamp line. if so, we're done
                            // with gathering lines for the stack trace
                            if (errorSb.length() > 0) {
                                errorSb.append("\n");
                            }
                            errorSb.append(line.trim());
                        } else if (ts != -1 && fetchingErrorTrace) {
                            // persist what we have
                            this.completeErrorInfoLog(errorInfo, errorSb.toString());
                            fetchingErrorTrace = false;
                        }
                        
                        newMessageTimestamp = (ts != -1) ? ts : lastMessageTimestamp;
                    }

                    charactersAlreadyRead += line.length() + 1; // add trailing new line
                    line = br.readLine();
                }
                
                if ( lastMessageTimestamp <= newMessageTimestamp ) {
                    
                    logMonitor.setLastMessageTimestamp( newMessageTimestamp );
                    logMonitor.setCharactersAlreadyRead( charactersAlreadyRead );
                    try {
                        persistence.save( logMonitor );
                    } catch ( IndexPersistException e ) {
                        logger.trace( "Failed to save the new message timestamp of "
                                + "'" + new Date(newMessageTimestamp).toString() + "', "
                                        + "error: " + ExceptionUtils.getStackTrace( e ) );
                    }
                }
            
            } catch (Exception e) {
                logger.trace( "Failed processing log file '" + logFile.getAbsolutePath() + "', error: " + ExceptionUtils.getStackTrace( e ) );
            } finally {
                // close the buffered reader if anything goes wrong
                try {
                    if (br != null) {
                        br.close();
                    }
                } catch (IOException e) {
                    logger.trace( "Failed closing buffered file reader for log file '" + logFile.getAbsolutePath() + "', error: " + ExceptionUtils.getStackTrace( e ) );
                }
            }
        }
        
        protected void completeErrorInfoLog(AuditLogErrorInfo errorInfo, String errorStr) {
            // persist what we have
            if (errorInfo != null) {
                errorInfo.setStackTrace(errorStr.trim());
                try {
                    persistence.create(errorInfo);
                    errorInfo = null;
                } catch (Exception e) {
                    logger.trace("Failed to create new error log error info db entry: " + e.toString());
                }
            }
        }
        
        protected long getTimestamp(String line) {
            // 2015-03-15 11:00:48.744, [qtp1036899981-18], SearchService.java, DEBUG - the message blah blah
            String timestring = null;
            long timestampTime = -1;
            if (line.indexOf(",") != -1) {
                timestring = line.substring(0, line.indexOf(","));
            } else if (line.indexOf(" :") != -1) {
                timestring = line.substring(0, line.indexOf(" :"));
            }
            
            //
            // turn the timestring into a date
            DateTime dt = null;
            if (timestring != null) {
                try {
                    dt = DateUtil.buildDateTimeForISO8601NoTz(timestring);
                    timestampTime = dt.getMillis();
                } catch (BaddataException e) {
                    logger.trace("Failed to parse timestamp from log: " + e.toString());
                }
            }

            return timestampTime;
        }
        
        protected void processAuditApiLogLine(String line, int auditLogIndex) {

            //
            // get the json located after this tag
            //
            String jsonStr = line.substring(auditLogIndex + AppConstants.AUDIT_API_TAG.length());
            
            //
            // Unmarshall from JSON and pick out the fields from the JSON message.
            //
            try {
                AuditLogApiInfo auditLogInfo = gson.fromJson( jsonStr, AuditLogApiInfo.class );
                try {
                    persistence.create(auditLogInfo);
                } catch (Exception e) {
                    logger.trace("Failed to create new audit log api info db entry: " + e.toString());
                }
            } catch (Exception e1) {
                logger.trace("Failed to parse audit log api info line, error: " + e1.toString());
            }
        }

    }
}
