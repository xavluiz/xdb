/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.manager.message;

public class MessageManager {

    public static final String MISSING_START_OR_END_DATETIME_ERROR = "Start and end date should be specified to "
            + "view summary results.";
    public static final String START_DATETIME_IS_AFTER_END_DATETIME_ERROR = "Start date should be before end date.";
    public static final String OPPORTUNITY_FIELD_FETCH_FAILED = "Unable to retrieve opportunity field information.";
    public static final String OPPORTUNITY_OAUTH_CREDS_SAVE_FAILED = "Failed to save salesforce oauth2 credentials. "
            + "Contact support for more information.";
    public static final String OPPORTUNITY_FIELD_PROFILE_SETTINGS_SAVE_FAILED = "Failed to save Salesforce opportunity "
            + "field profile infomation. Contact support for more information.";
    public static final String OPPORTUNITY_DOWNLOAD_IN_PROGRESS = "Unable to initialize Salesforce opportunity download. "
            + "There's already a running task for this Salesforce account.";
    public static final String OPPORTUNITY_ONBOARDING_FAILED = "Failed to onboard the Salesforce opportunity data for this account.";
    public static final String DATA_QUALITY_FETCH_FAILED = "Unable to fetch data quality results at this time.";
    public static final String USER_PERFORMANCE_FETCH_FAILED = "Unable to fetch user performance results at this time.";
    public static final String TREND_FETCH_FAILED = "Unable to fetch trend results at this time.";
}
