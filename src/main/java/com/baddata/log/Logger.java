/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.log;


import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;

import com.baddata.util.AppConstants;
import com.baddata.util.ExecutorServiceUtil;
import com.baddata.util.StringUtil;
import com.google.common.collect.Maps;

/**
 *
 * Log4j Logging utility
 *
 */
public class Logger {
    // Fully Qualified Class Name
    static private final String LOGGER_CLASS_NAME = Logger.class.getName();
    
    static Map<String, Long> errorMsgMap = Maps.newHashMap();
    static Map<String, LogMessageWrapper> logMessageMap = Maps.newHashMap();
    
    public enum LogLevel {
        WARN,
        DEBUG,
        ERROR,
        INFO
    }
    
    public static class LogMessageWrapper {
        private long time;
        private String message;
        public long getTime() {
            return time;
        }
        public void setTime(long time) {
            this.time = time;
        }
        public String getMessage() {
            return message;
        }
        public void setMessage(String message) {
            this.message = message;
        }
    }
    
    public static class LogMessageMonitor implements Runnable {

        @Override
        public void run() {
            // go through the map and remove the one's greater than 2 hours
            for (String key : logMessageMap.keySet() ) {
                LogMessageWrapper wrapper = logMessageMap.get(key);
                if (wrapper.getTime() >= (DateUtils.MILLIS_PER_HOUR * 2)) {
                    logMessageMap.remove(key);
                }
            }
        }
    }
    
    private static void init() {
        ExecutorServiceUtil.getInstance().getScheduleExecutor().scheduleAtFixedRate(
                new LogMessageMonitor(), DateUtils.MILLIS_PER_HOUR, DateUtils.MILLIS_PER_HOUR, TimeUnit.MILLISECONDS);
    }

    private final org.apache.log4j.Logger logger4j;

    private Logger(org.apache.log4j.Logger logger4j) {
        this.logger4j = logger4j;
    }

    public static Logger getLogger(String name) {
        // remove the class name
        org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(name);
        init();
        return new Logger(logger);
    }

    public static Logger getLogger(Class<?> clazz) {
        init();
        return new Logger(org.apache.log4j.Logger.getLogger(clazz));
    }

    /**
     * Log the event in the specified logger files and persist the events that are required.
     * @param level
     * @param message
     * @param t
     */
    private synchronized void forcedLog(Level level, String message, Throwable t) {
        // The class name (FCQN) lets it know that the caller of this class is the one that should be printed in
        // the log message.  Otherwise the log messages would all say "com.baddata.server.<blah>.logger.Logger..."
        String key = level.toString() + "_" + message;
        LogMessageWrapper msgWrapper = logMessageMap.get(key);
        if (msgWrapper == null || msgWrapper.getTime() > (DateUtils.MILLIS_PER_HOUR * 2)) {
            msgWrapper = new LogMessageWrapper();
            msgWrapper.setTime(System.currentTimeMillis());
            msgWrapper.setMessage(message);
            logMessageMap.put(key, msgWrapper);
            logger4j.callAppenders( new LoggingEvent(LOGGER_CLASS_NAME, logger4j, level, message, t) );
        }
    }
    
    public void apiAudit(String msg) {
        if (logger4j.isInfoEnabled()) {
            msg = AppConstants.AUDIT_API_TAG + "" + msg;
            forcedLog(Level.INFO, msg, null);
        }
    }

    public void debug(String msg) {
        if (logger4j.isDebugEnabled()) {
            forcedLog(Level.DEBUG, msg, null);
        }
    }
    
    public void error(String msg, Throwable t) {
        if (logger4j.isEnabledFor(Level.ERROR)) {
            
            Long msgDateTime = errorMsgMap.get(msg);
            long now = System.currentTimeMillis();
            if ( msgDateTime != null && now - msgDateTime.longValue() < DateUtils.MILLIS_PER_HOUR ) {
                // already have the error message in cache, skip logging this one. If
                // it's past an hour, we'll refresh the cache timestamp and log it
                return;
            }
            
            // add the error msg to the map so we don't flood the log with the same error
            errorMsgMap.put(msg, new Long(now));
            
            StringBuffer errorMsg = new StringBuffer();
            errorMsg.append(AppConstants.AUDIT_ERROR_TAG + "" + msg);
            
            String tMsg = "";
            String tCause = "";
            String tTrace = "";
            
            if (t != null) {
                tMsg = (StringUtils.isNotBlank(t.getMessage())) ? t.getMessage() : t.toString();
                tMsg = (tMsg != null) ? tMsg.trim() : "";
                if (t.getCause() != null) {
                    if (StringUtils.isNotBlank(t.getCause().getMessage())) {
                        tCause = t.getCause().getMessage();
                    } else {
                        tCause = t.getCause().toString();
                    }
                }
                tCause = (tCause != null) ? tCause.trim() : "";
                
                StringBuffer sb = new StringBuffer();
                if (t.getStackTrace() != null && t.getStackTrace().length > 0) {
                    for (StackTraceElement el : t.getStackTrace()) {
                        String traceStr = el.toString();
                        if (traceStr.indexOf("com.baddata") != -1) {
                            if (sb.length() > 0) {
                                sb.append("\n\t");
                            }
                            sb.append(traceStr);
                        }
                    }
                }
                tTrace = sb.toString().trim();
            }
            
            if (tMsg.length() > 0 || tCause.length() > 0 || tTrace.length() > 0) {
                errorMsg.append("\n");
            }

            if (tMsg.length() > 0) {
                errorMsg.append("\tREASON: " + tMsg);
                if (tCause.length() > 0 || tTrace.length() > 0) {
                    errorMsg.append("\n");
                }
            }
            if (tCause.length() > 0) {
                errorMsg.append("\tCAUSE: " + tCause);
                if (tTrace.length() > 0) {
                    errorMsg.append("\n");
                }
            }
            if (tTrace.length() > 0) {
                errorMsg.append("\tTRACE: " + tTrace);
            }

            // no need to send t since we've built an error trace into the 'msg'
            forcedLog(Level.ERROR, errorMsg.toString(), null /*t*/);
        }
    }

    public void fatal(String msg) {
        if (logger4j.isEnabledFor(Level.FATAL)) {
            forcedLog(Level.FATAL, msg, null);
        }
    }

    public void fatal(String msg, Throwable t) {
        if (logger4j.isEnabledFor(Level.FATAL)) {
            forcedLog(Level.FATAL, msg, t);
        }
    }

    public void info(String msg) {
        if (logger4j.isInfoEnabled()) {
            forcedLog(Level.INFO, msg, null);
        }
    }

    public void trace(String msg) {
        if (logger4j.isTraceEnabled()) {
            forcedLog(Level.TRACE, msg, null);
        }
    }

    public boolean isDebugEnabled() {
        return logger4j.isDebugEnabled();
    }

    public void warn(String msg) {
        if (logger4j.isEnabledFor(Level.WARN)) {
            forcedLog(Level.WARN, msg, null);
        }
    }

    public void warn(String msg, Throwable t) {
        if (logger4j.isEnabledFor(Level.WARN)) {
            forcedLog(Level.WARN, msg, t);
        }
    }
    
    public void logTimerThreadInfo(String threadName, long delay, long interval, boolean repeat) {
        String intervalBreakdown = StringUtil.getDurationBreakdown( interval );
        this.info("TIMER THREAD START: Starting {'name': \"" + threadName + "\", 'delay': \"" + delay + " (ms)\", 'interval': \"" + intervalBreakdown + "\", 'repeat': " + Boolean.toString(repeat) + "}");
    }
}
