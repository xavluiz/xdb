/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.util;

import org.joda.time.DateTimeZone;

public class AppConstants {
    
    //--------------------------------------------------
    //
    // VERSION CONSTANTS
    //
    //--------------------------------------------------

    final public static String SESSION_USERNAME_KEY = "BaddataSessionUsernameValue".intern();
    final public static String SESSION_USERREFERENCE_KEY = "BaddataSessionUserReferenceValue".intern();
    final public static String SESSION_IS_UBER_USER_KEY = "IsUberUser".intern();
    
    final public static String PATH_ROOT = "/api".intern();
    final public static String PATH_ROOT_NO_SLASH = "api".intern();
    
    final public static String RESOURCE_PKG = "com.baddata.api".intern();
    final public static String DTO_PKG = "com.baddata.api.dto".intern();
    
    //--------------------------------------------------
    //
    // TYPED OBJECT ATTRIBUTE CONSTANTS
    //
    //--------------------------------------------------
    final public static String ID_KEY = "id".intern();
    final public static String SORT_ID_KEY = "sortid".intern();
    final public static String TYPE_ID_KEY = "typeId".intern();
    final public static String CREATE_DATE_KEY = "createTime".intern();
    final public static String UPDATE_DATE_KEY = "updateTime".intern();
    final public static String PARENT_ID_KEY = "parent".intern();
    final public static String TENANT_ID_KEY = "tenantId".intern();
    final public static String USER_REFERENCE_KEY = "userRef".intern();
    final public static String UNSET_STRING_VALUE = "UNSET_STRING_VALUE".intern();
    
    final public static String ORDERBY_SUB_OBJ_REF_KEY = "@ref@".intern();
    final public static String ORDERBY_SUB_OBJ_PARENT_REF_KEY = "@parentref@".intern();
    final public static String ORDERBY_OBJ_ID_REF_KEY = "@id@".intern();
    
    //--------------------------------------------------
    //
    // TOKEN PARAM NAME CONSTANTS
    //
    //--------------------------------------------------
    // session authorization token
    public static String AUTH_TOKEN = "authToken";
    // antiforgery token
    public static String AF_TOKEN = "afToken";
    public static String AUTOMATION_TEST_TOKEN = "AUTOMATION-TEST-TOKEN";

    //--------------------------------------------------
    //
    // Directory or Filename CONSTANTS
    //
    //--------------------------------------------------

    public static String LUCENE_STORE_NAME = "luceneStore";
    public static String IMAGE_STORE_NAME = "imageStore";
    public static String CURRENCY_DIR_NAME = "currency";
    public static String CURRENCY_FILE_NAME = "CurrencyRateExchange.json";
    public static String OPPORTUNITY_LOG_INFO = "OpportunityDataInfo.log";
    public static String OPPORTUNITY_FIELD_HISTORY_LOG_INFO = "OpportunityFieldHistoryDataInfo.log";
    public static String OPPORTUNITY_CURRENCY_CONVERSION_LOG_INFO = "OpportunityCurrencyConversionDataInfo.log";
    public static String CONTENTS_FIELD = "contents";
    public static String FOR_NAME_FIELD = "forName";

    //--------------------------------------------------
    //
    // SEARCH CONSTANTS
    //
    //--------------------------------------------------

    /**
     * Max batch size of documents to index: 1000.
     */
    public static int MAX_INDEX_LIMIT = 5000;
    /**
     * Max number of documents the db can return in one page: 10000.
     */
    public static int MAX_SEARCH_LIMIT = 10000;
    
    //--------------------------------------------------
    //
    // ENVIRONMENT CONSTANTS
    //
    //--------------------------------------------------
    
    public static String BADDATA_LOGS_HOME_ENV_VAR = "BADDATA_LOGS";
    public static String FACEBOOK_SECRET_ENV_VAR = "fb.app.secret";
    
    //
    // Server constants
    public static String SYNTHETIC_DATASET = "synthetic.data.dir";
    public static String DEV_SYNTHETIC_DATASET = "dev.synthetic.data.dir";
    public static String TOMCAT_HOME_VAR = "tomcat.home";
    public static String APP_VERSION = "app.version";
    public static String TIME_ZONE_ID = "timezone.id";
    
    //
    // Test constants
    public static String UNIT_TESTING = "unit.testing";
    // used for BaddataLogManagerTest
    public static String USE_TEST_LOG = "use.test.log";
    public static String TEST_LOG_PATH = "test.log.path";
    
    //
    // Log file constants
    public static String LOG_FILE_NAME = "log.file.name";
    
    //--------------------------------------------------
    //
    // CURRANCY LAYER CONSTANTS
    // https://currencylayer.com/documentation
    //
    //--------------------------------------------------
    
    public static String CURRENCY_LAYER_KEY = "currency.layer.key";
    public static String CURRENCY_LAYER_ENDPOINT = "currency.layer.endpoint";
    public static String USD_ISO_CODE = "USD";
    
    //--------------------------------------------------
    //
    // SALESFORCE INSTANCE CONSTANTS
    //
    //--------------------------------------------------
    
    public static String SALESFORCE_CLIENT_ID = "salesforce.client.id";
    public static String SALESFORCE_CLIENT_SECRET = "salesforce.client.secret";
    public static String SALESFORCE_OAUTH_TOKEN_API = "salesforce.token.api";
    
    //--------------------------------------------------
    //
    // MISC CONSTANTS
    //
    //--------------------------------------------------
    
    //
    // ADMIN CONSTANTS
    public static String ADMIN1_USERNAME = "admin1.username";
    public static String ADMIN1_PASSWORD = "admin1.password";
    public static String ADMIN1_EMAIL = "admin1.email";
    
    public static String ADMIN2_USERNAME = "admin2.username";
    public static String ADMIN2_PASSWORD = "admin2.password";
    public static String ADMIN2_EMAIL = "admin2.email";
    
    public static String ADMIN_USERNAME = "admin.username";
    public static String ADMIN_PASSWORD = "admin.password";
    public static String ADMIN_EMAIL = "admin.email";
    
    //
    // Avatar file part
    public static String AVATAR_FILE_TOKEN = "-avatar";
    //
    // Profile background file part
    public static String PROFILE_BACKGROUND_FILE_TOKEN = "-profile-background";


    //--------------------------------------------------
    //
    // LOG CONSTANTS
    //
    //--------------------------------------------------
    
    public static final String AUDIT_API_TAG = "AUDIT:LOG:";
    public static final String AUDIT_ERROR_TAG = "AUDIT:ERROR:";
    
    //--------------------------------------------------
    //
    // EMAIL TEMPLATE NAME CONSTANTS
    //
    //--------------------------------------------------
    public static final String FORGOT_PASSWORD_EMAIL_TEMPLATE = "forgotpass.html";
    public static final String WELCOME_NEW_USER_EMAIL_TEMPLATE = "welcome.html";
    
    //--------------------------------------------------
    //
    // IDS TO VALIDATE
    //
    //--------------------------------------------------
    public static final String SALESFORCE_OPPORTUNITY_VALIDATION_IDS = "sf.opp.validation.ids";
    
}
