/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved
 */
package com.baddata.log;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.system.BaddataLogEvent;
import com.baddata.api.dto.user.User;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.RangeQuery;
import com.baddata.db.SearchQuery;
import com.baddata.exception.IndexPersistException;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.manager.email.EmailManager;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.google.common.collect.Lists;

public class EventLogger {
    
    private static Logger logger = Logger.getLogger(EventLogger.class.getName());
    
    private static PersistenceManager persistenceMgr = PersistenceManager.getInstance();
    
    public enum ThrottlerKey {
        
        // event service keys
        EVENT_SERVICE_EVENTQ_IS_FULL,
        EVENT_SERVICE_IS_BUSY,
        EVENT_SERVICE_QUEUE_ERRORS,
        
        // JsonTypedBeanDeserializer keys
        TYPED_BEAN_DESERIALIZER_FAILED;
        
    }

    public enum SeverityType {

        USER_AUDIT("USER", "AUDIT", "User actions useful for correlating changes to error conditions"),

        USER_NOTICE("USER", "NOTICE", "FYI to know, but does not require immediate action"),

        USER_ALERT("USER", "ALERT", "Requires immediate action by the end user via UI and email"),

        USER_UI_ALERT("USER", "UI_ALERT", "Requires immediate action by the end user, but only visible on the UI"),

        EVENT_AUDIT("EVENT", "AUDIT", "Event steps for client workflow or command requests"),

        SUPPORT_NOTICE("SUPPORT", "NOTICE", "Important trace messages for support"),

        SUPPORT_ALERT("SUPPORT", "ALERT", "Requires immediate action by support"),

        DEBUG("DEBUG", "AUDIT", "developers");

        public String target;
        public String level;
        public String description;

        private SeverityType(String target, String level, String description) {
            this.level = level;
            this.target = target;
            this.description = description;
        }
    }
    
    public static String info(Long sessionUserReferenceId, Logger logger, String msg, EventMessage m, Object... eventTemplateVals ) {
        logger.info(msg);
        
        return log( sessionUserReferenceId, m, eventTemplateVals );
    }
    
    public static String debug( Long sessionUserReferenceId, Logger logger, String msg, EventMessage m, Object... eventTemplateVals ) {
        logger.debug(msg);
        
        return log( sessionUserReferenceId, m, eventTemplateVals );
    }
    
    public static String warn(Long sessionUserReferenceId, Logger logger, String msg, EventMessage m, Object... eventTemplateVals ) {
        logger.warn(msg);
        
        return log( sessionUserReferenceId, m, eventTemplateVals );
    }
    
    public static String error(Long sessionUserReferenceId, Logger logger, String msg, EventMessage m, Object... eventTemplateVals ) {
        logger.error(msg, null);
        
        return log( sessionUserReferenceId, m, eventTemplateVals );
    }
    
    public static String fatal(Long sessionUserReferenceId, Logger logger, String msg, EventMessage m, Object... eventTemplateVals ) {
        logger.fatal(msg);
        
        return log( sessionUserReferenceId, m, eventTemplateVals );
    }
    
    public static synchronized String log( Long sessionUserReferenceId, EventMessage m, Object... eventTemplateVals ) {
        
        String formattedMessage = "";
        try {
            formattedMessage = String.format( m.messageTemplate, eventTemplateVals );
        } catch (Exception e) {
            String givenVals = (eventTemplateVals != null) ?  eventTemplateVals.toString() : "";
            // failed to format the message
            logger.error("Failed to format message template '" + m.messageTemplate + "' for the given vals "
                    + "'" + givenVals + "'"
                    + "This requires a patch to fix the template values against the given template.", e.getCause());
            formattedMessage = m.messageTemplate;
        }
        
        if (m.severityType == SeverityType.EVENT_AUDIT || m.severityType == SeverityType.USER_AUDIT) {
            // just log it
            logger.info(formattedMessage);
        }
        
        //
        // it's a notice or alert
        //
        try {
            
            //
            // Get the session user
            //
            User user = (User) persistenceMgr.getById(DbIndexType.USER_TYPE, sessionUserReferenceId);
            if (user == null) {
                logger.error("Failed to get the session user when logging, will return current formatted message", null);
                return formattedMessage;
            }

            String subject = (StringUtils.isNotBlank(m.subject)) ? m.subject : "BadData alert";

            //
            // Get the support user
            //
            User supportUser = persistenceMgr.getUserByUsername( AppUtil.get( AppConstants.ADMIN1_USERNAME ) );
            
            SeverityType sType = m.severityType;

            if (sType == SeverityType.SUPPORT_ALERT || sType == SeverityType.SUPPORT_NOTICE) {
                //
                // set the user to the support user
                user = persistenceMgr.getUserByUsername( AppUtil.get( AppConstants.ADMIN1_USERNAME ) );
                // null out the support user
                supportUser = null;
            }

            //
            // determine if we should persist it so the event is shown on the UI in the alerts view
            //
            boolean persistEvent =
                (sType == SeverityType.USER_ALERT || sType == SeverityType.USER_UI_ALERT) ? true : false;
            
            //
            // Determine if we should send an email
            //
            boolean sendEmail =
                (sType == SeverityType.SUPPORT_NOTICE ||
                    sType == SeverityType.USER_NOTICE || sType == SeverityType.USER_ALERT) ?
                        true : false;
            
            Long userRef = user.getId();
            
            //
            // Make sure we haven't already sent the message, look for this exact message in the db
            //
            SearchSpec searchSpec = new SearchSpec(userRef);
            
            List<SearchQuery> queries = Lists.newArrayList();
            // match the event code
            SearchQuery sq = new SearchQuery("eventcode", m.eventCode);
            queries.add(sq);

            // as long as it was created less than a week ago
            List<RangeQuery> rangeQueries = Lists.newArrayList();
            long aWeekAgoTimestamp = DateTime.now().minusDays(7).getMillis();
            RangeQuery rangeQuery = new RangeQuery("createtime", aWeekAgoTimestamp /*minimum*/, null /*max*/);
            rangeQueries.add(rangeQuery);
            
            searchSpec.setRangeQueries(rangeQueries);
            searchSpec.setQueries(queries);
            
            boolean alreadySentIt = false;
            Page pageOfLogEvents = persistenceMgr.get(DbIndexType.BADDATA_LOG_EVENT_TYPE, searchSpec);
            if (pageOfLogEvents != null && pageOfLogEvents.getTotalHits() > 0) {
                // looks like we have
                
                List<BaddataLogEvent> existingEvents = (List<BaddataLogEvent>) pageOfLogEvents.getItems();
                for (BaddataLogEvent existingEvent : existingEvents) {
                    if (!existingEvent.isAcknowledged()) {
                        //
                        // Hasn't been acknowledged yet, don't send it again
                        //
                        alreadySentIt = true;
                        break;
                    }
                }
            }
            
            if (!alreadySentIt) {
                // send the message and persist it
                BaddataLogEvent logEvent = new BaddataLogEvent(m.severityType, m.eventCode, formattedMessage, m.subject, userRef);
                
                if (sendEmail) {
                    try {
                        EmailManager.getInstance().sendEmail( user, supportUser, subject, formattedMessage /* message */ );
                        
                        //
                        // Set "notified" to true
                        logEvent.setNotified(true);
                    } catch (Exception e) {
                        logger.error( "Failed to send notification email.", e );
                    }
                }
                
                if (persistEvent) {
                    persistenceMgr.create(logEvent);
                }
            }

        } catch (IndexPersistException e) {
            logger.error( "Failed to send save log event", e );
        }
        
        return formattedMessage;
    }
    
    /**
     * These are used by the resource classes to set the response error code so the client
     * can handle things in a more systematic way if it needs to.
     */
    public enum ApiErrorCode {
        
        // ----------------------------------------------
        // SESSION RESOURCE ERRORS
        // ERR-API-1xxx
        // ----------------------------------------------
        INVALID_SESSION_ERROR("ERR-API-1001"),
        UNAUTHORIZED_ACCESS_ERROR("ERR-API-1002"),
        FORBIDDEN_ACCESS_ERROR("ERR-API-1003"),
        SERVICE_UNAVAILABLE_ERROR("ERR-API-1004"),
        
        
        // ----------------------------------------------
        // LOGIN ERRORS
        // ERR-API-11xx
        // ----------------------------------------------
        LOGIN_FAILED_ERROR("ERR-API-1101"),
        
        
        // ----------------------------------------------
        // LOGOUT ERRORS
        // ERR-API-12xx
        // ----------------------------------------------
        LOGOUT_FAILED_ERROR("ERR-API-1201"),
        
        // ----------------------------------------------
        // SIGNUP ERRORS
        // ERR-API-13xx
        // ----------------------------------------------
        SIGNUP_FAILED_ERROR("ERR-API-1301"),
        
        // ----------------------------------------------
        // FILE_UPLOAD ERRORS
        // ERR-API-14xx
        // ----------------------------------------------
        FILE_UPLOAD_NON_MULTIPART_ERROR("ERR-API-1401"),
        FILE_UPLOAD_ERROR("ERR-API-1402"),
        
        // ----------------------------------------------
        // USER RESOURCE ERRORS
        // ERR-API-2xxx
        // ----------------------------------------------
        USER_CREATE_ERROR("ERR-API-2001"),
        USER_UPDATE_ERROR("ERR-API-2002"),
        USER_GET_ERROR("ERR-API-2003"),
        USER_DELETE_ERROR("ERR-API-2004"),
        USER_CHANGE_PASSWORD_ERROR("ERR-API-2005"),
        USER_RESET_PASSWORD_ERROR("ERR-API-2006"),
        USER_REQUEST_RESET_PASSWORD_ERROR("ERR-API-2007"),
        USER_SETTINGS_GET_ERROR("ERR-API-2008"),
        USER_SETTINGS_UPDATE_ERROR("ERR-API-2009"),
        USER_INFO_UPDATE_ERROR("ERR-API-2010"),
        USER_ACCOUNT_DELETE_ERROR("ERR-API-2011"),
        USER_WELCOME_ERROR("ERR-API-2012"),
        
        
        // ----------------------------------------------
        // RATE ERRORS
        // ERR-API-31xx
        // ---------------------------------------------
        UPDATE_BASIC_RATE_FAILED("ERR-API-3101"),
        UPDATE_RATE_DISCOUNT_FAILED("ERR-API-3102"),
        
        // ----------------------------------------------
        // EVENT/PROMOTE ERRORS
        // ERR-API-41xx
        // ---------------------------------------------
        UPDATE_EVENT_FAILED("ERR-API-4101"),
        
        // ----------------------------------------------
        // SALESFORCE DATA PROCESSING ERRORS
        // ERR-API-51xx
        // ---------------------------------------------
        LOAD_SALESFORCE_OPPORTUNITIES_FAILED("ERR-API-5101"),
        LOAD_SALESFORCE_OPPORTUNITY_FIELD_HISTORY_FAILED("ERR-API-5102"),
        UPDATE_SALESFORCE_OAUTH_CREDS_FAILED("ERR-API-5103"),
        GET_SALESFORCE_SUMMARY_INFO_FAILED("ERR-API-5104"),
        DELETE_SALESFORCE_OAUTH_CREDS_FAILED("ERR-API-5105"),
        LOAD_SALESFORCE_OPPORTUNITY_FIELDS_FAILED("ERR-API-5106"),
        UPDATE_SALESFORCE_FIELD_PROFILE_SETTINGS_FAILED("ERR-API-5107"),
        UPDATE_SALESFORCE_OBJECTIVE_FAILED("ERR-API-5108"),
        GET_SALESFORCE_DATA_QUALITY_DOWNLOAD_REPORT_FAILED("ERR-API-5109"),
        GET_SALESFORCE_OPPORTUNITIES_DOWNLOAD_FAILED("ERR-API-5110"),
        GET_SALESFORCE_LATEST_STAGE_OPPORTUNITIES_DOWNLOAD_FAILED("ERR-API-5111"),
        
        // ----------------------------------------------
        // DATABASE RESOURCE ERRORS
        // ERR-API-61xx
        // ---------------------------------------------
        RETRIEVE_DATABASE_BY_TYPE_FAILED("ERR-API-6101"),
        UPDATE_ROW_FAILED("ERR-API-6102"),
        DELETE_DB_FAILED("ERR-API-6103"),
        
        // ----------------------------------------------
        // ASSET RESOURCE ERRORS
        // ERR-API-71xx
        // ---------------------------------------------
        RETRIEVE_ASSET_FAILED("ERR-API-7101"),
        
        // ----------------------------------------------
        // EVENT RESOURCE ERRORS
        // ERR-API-81xx
        // ---------------------------------------------
        ACKNOWLEDGE_ALERT_FAILED("ERR-API-8101"),
        
        // ----------------------------------------------
        // GENERIC API ERRORS
        // ERR-API-100xx
        // ---------------------------------------------
        API_NOT_FOUND_ERROR("ERR-API-10000"),
        BAD_API_QUERY_PARAM_FORMAT_ERROR("ERR-API-10001");
        
        // ----------------------------------------------
        //
        // local variables
        //
        // ---------------------------------------------

        private String code;
        private SeverityType severityType;

        /**
         * ErrorCodes constructor that builds the error message information for client-api/logging purposes
         * 
         * @param c
         */
        private ApiErrorCode( String code ) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
    
    /**
     * This is used internally to generate an email for either support or the user or both.
     */
    public enum EventMessage {
        
        SALESFORCE_OAUTH_CREDS_DELETED(SeverityType.SUPPORT_NOTICE, "LOG-SF-10001",
                "User deleted oauth credentials for Salesforce account '%s'.",
                "User deleted oauth credentials" /* subject */),
        
        SALESFORCE_OAUTH_CREDS_ADDED(SeverityType.SUPPORT_NOTICE, "LOG-SF-10002",
                "User added oauth credentials for Salesforce account '%s'.",
                "User added oauth credentials" /* subject */),
        
        SALESFORCE_DATA_ONBOARDED(SeverityType.SUPPORT_NOTICE, "LOG-SF-10003",
                "User onboarded data for Salesforce account '%s'.",
                "User completed Salesforce onboarding" /* subject */),

        SALESFORCE_OAUTH_CONNECT_FAILED(SeverityType.USER_UI_ALERT, "LOG-SF-10004",
                "For security purposes, Salesforce periodically resets your %s account password. "
                + "Plese reauthorize BadData using the new Salesforce credentials. If you have "
                + "questions please feel free to email support@baddata.com.",
                "Please update your Salesforce credentials in BadData" /* subject */),
        
        // i.e. [{"message":"Your query request was running for too long.","errorCode":"QUERY_TIMEOUT"}]
        SALESFORCE_DATA_DOWNLOAD_FAILED(SeverityType.USER_ALERT, "LOG-SF-10005",
                "We are currently experiencing Salesforce limitations for downloading data for account '%s' at %s. "
                + "We have already notified support@baddata.com, and they will follow up with you shortly.",
                "Important information about your Salesforce data sync" /* subject */),

        SALESFORCE_OPPORTUNITY_DATA_DOWNLOAD_INTERRUPTED_FAILED(SeverityType.USER_UI_ALERT, "LOG-SF-10006",
                "Due to server upgrades your Salesforce data sync has been interrupted at %s, please try again.",
                "" /* subject not required for UI Alert */),
        
        SALESFORCE_JSON_PARSE_FAILED(SeverityType.SUPPORT_NOTICE, "LOG-SF-10007",
                "Unable to parse Salesforce JSON response entity. Error: %s",
                "" /* subject not required for UI Alert */),
        
        SALESFORCE_OPPORTUNITY_MISSING_CLOSE_DATE_FH_NOTICE(SeverityType.USER_UI_ALERT, "LOG-SF-10008",
                "You're Salesforce account does not have any records indicating close date updates. "
                + "You can set a default sales cycle duration within the Salesforce Oauth settings to enable forecasting.",
                "" /* subject not required for UI Alert */);
        
        public String eventCode;
        public String messageTemplate;
        public SeverityType severityType;
        public String subject;
        
        private EventMessage(SeverityType severityType, String eventCode, String messageTemplate, String subject) {
            this.eventCode = eventCode;
            this.severityType = severityType;
            this.messageTemplate = messageTemplate;
            this.subject = subject;
        }
    }
    
}
