/**
 * Copyright (c) 2017 by Baddata
 * All rights reserved.
 */
package com.baddata.manager.system;

import java.util.List;

import com.baddata.api.dto.salesforce.OpportunityFieldPreferences;
import com.baddata.api.dto.salesforce.SalesforceOauth2Creds;
import com.baddata.api.dto.system.ConfigInfo;
import com.baddata.api.dto.user.User;
import com.baddata.api.dto.user.User.RoleType;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.exception.ApiServiceException;
import com.baddata.exception.ApiServiceException.ApiExceptionType;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.manager.salesforce.SalesforceApiHelper;
import com.baddata.manager.salesforce.SalesforceDataLoadManager;
import com.baddata.manager.user.UserSessionManager;
import com.baddata.util.AppConstants;

/**
 * 
 * Manages the global configurations such as
 * initializing devMode. 
 *
 */
public class SystemManager {

    protected static Logger logger = Logger.getLogger(SystemManager.class.getName());
    
    private static SystemManager ref;
    private PersistenceManager persistence = PersistenceManager.getInstance();
    private boolean inMaintenanceMode = false;

    /**
     * Singleton instance
     * @return
     */
    public static SystemManager getInstance() {
        if (ref == null) {
            synchronized(SystemManager.class) {
                if ( ref == null ) {
                    ref = new SystemManager();
                }
            }
        }
        return ref;
    }
    
    // private constructor to ensure singleton usage
    private SystemManager() {
        init();
    }
    
    private void init() {
        this.getOrSaveInitializeConfig();
    }
    
    public boolean isMaintenanceMode() {
        return this.inMaintenanceMode;
    }
    
    public void unsetMaintenanceMode() {
        ConfigInfo existingConfigInfo = this.getOrSaveInitializeConfig();
        existingConfigInfo.setMaintenanceMode(false);
        existingConfigInfo.setMaintenanceMessage(AppConstants.UNSET_STRING_VALUE);
        try {
            persistence.update(existingConfigInfo);
            this.inMaintenanceMode = false;
        } catch (IndexPersistException e) {
            logger.error("Failed to save the system config info.", e);
        }
    }
    
    public void setToMaintenanceMode(String maintenanceMessage) {
        ConfigInfo existingConfigInfo = this.getOrSaveInitializeConfig();
        existingConfigInfo.setMaintenanceMode(true);
        existingConfigInfo.setMaintenanceMessage(maintenanceMessage);
        try {
            persistence.update(existingConfigInfo);
            this.inMaintenanceMode = true;
        } catch (IndexPersistException e) {
            logger.error("Failed to save the system config info.", e);
        }
    }
    
    public boolean isDevModeForAdminUsers(long userReferenceId) {
        //
        // first check if this is an admin user
        User user = (User) persistence.getById(DbIndexType.USER_TYPE, userReferenceId);
        if ( user == null || !user.getRole().equals( RoleType.ADMIN.name() ) ) {
            return false;
        }
        
        // there should always be only one
        ConfigInfo existingConfigInfo = this.getOrSaveInitializeConfig();
        return existingConfigInfo.isDevMode();
    }
    
    public ConfigInfo getOrSaveInitializeConfig() {
        ConfigInfo configInfo = (ConfigInfo)
                persistence.getFirstObject(DbIndexType.CONFIG_INFO_TYPE, null /* tenantId */);
        
        //
        // If it doesn't exist, create an initial config info
        //
        if (configInfo == null) {
            configInfo = new ConfigInfo();
            configInfo.setDevMode(true);
            configInfo.setMaintenanceMode(false);
            try {
                persistence.save(configInfo);
            } catch (IndexPersistException e) {
                logger.error("Failed to save the system config info.", e);
            }
            
            configInfo = (ConfigInfo) persistence.getFirstObject(DbIndexType.CONFIG_INFO_TYPE, null /* tenantId */);
        }
        
        // update the maintenance mode flag
        this.inMaintenanceMode = configInfo.isMaintenanceMode();
        
        //
        // Set the version, it won't be set when running on localhost
        //
        String version = System.getProperty(AppConstants.APP_VERSION, "1.0");
        configInfo.setVersion(version);
        
        //
        // Return what was created
        return configInfo;
    }
    
    public ConfigInfo getConfigInfo() {
        return this.getOrSaveInitializeConfig();
    }
    
    public void updateConfigInfo( ConfigInfo incomingConfigInfo, long userReferenceId ) throws ApiServiceException {
        
        User user = (User) persistence.getById(DbIndexType.USER_TYPE, userReferenceId);
        if ( user == null || !user.getRole().equals( RoleType.ADMIN.name() ) ) {
            throw new ApiServiceException("Unable to update system config information as a non-admin user", ApiExceptionType.BAD_REQUEST );
        }
        
        //
        // make sure the user exists
        ConfigInfo existingConfigInfo = (ConfigInfo) persistence.getFirstObject(DbIndexType.CONFIG_INFO_TYPE, null /*tenantId*/);
        
        if ( existingConfigInfo == null ) {
            existingConfigInfo = new ConfigInfo();
            existingConfigInfo.setDevMode(true);
            try {
                persistence.save(existingConfigInfo);
            } catch (IndexPersistException e) {
                logger.error("Failed to save the system config info.", e);
            }
            
            existingConfigInfo = (ConfigInfo) persistence.getFirstObject(DbIndexType.CONFIG_INFO_TYPE, null /* tenantId */);
        }
        
        boolean deleteData = (incomingConfigInfo.isDevMode() != existingConfigInfo.isDevMode());
        existingConfigInfo.setDevMode(incomingConfigInfo.isDevMode());

        try {
            persistence.save(existingConfigInfo);
            
            if (deleteData) {
                
                //
                // Get the admin users
                //
                @SuppressWarnings("unchecked")
                List<User> users = (List<User>) persistence.getAllForObjectByFieldAndKeyword(DbIndexType.USER_TYPE, "role", RoleType.ADMIN.name());
                
                SalesforceApiHelper sfApiHelper = SalesforceApiHelper.getInstance();
                
                for ( User u : users ) {
                    
                    if (!u.getRole().equals(RoleType.ADMIN.name())) {
                        // only delete/update non-admin users data
                        continue;
                    }
                    
                    //
                    // Get the oauth creds for this user
                    SalesforceOauth2Creds creds = (SalesforceOauth2Creds) persistence.getByUserRef(DbIndexType.SALESFORCE_OAUTH2_CREDS_TYPE, u.getId());
                    
                    if ( creds != null ) {
                        
                        sfApiHelper.clearSalesforceConnectionInfoCache(creds);
                        
                        // found an oauth for this admin user
                        
                        Long fieldPrefsId = (creds.getFieldPreferences() != null) ? creds.getFieldPreferences().getId() : null;
                        String tenantId = creds.getDynamicInstanceTenantId();
                        
                        //
                        // Delete the field preferences
                        // check if it wasn't deleted by it's parent
                        OpportunityFieldPreferences fieldPrefs = (OpportunityFieldPreferences)
                                persistence.getById(DbIndexType.SALESFORCE_OPPORTUNITY_FIELD_SETTINGS_TYPE, fieldPrefsId);
                        if ( fieldPrefs != null ) {
                            //
                            // Delete the field settings for the opportunity
                            //
                            try {
                                sfApiHelper.deleteOpportunityFieldSettings(fieldPrefsId);
                            } catch (Exception e) {
                                logger.error("Failed to delete opportunity field settings.", e);
                            }
                        }
                        
                        // successfully deleted oauth credentials. delete the salesforce data associated with it
                        // sf user, account, opportunity, opportunity field history
                        SalesforceDataLoadManager.getInstance().deleteData(tenantId, creds.getUserRef());
                    }
                }
            }
            
        } catch ( IndexPersistException e ) {
            throw new ApiServiceException( "Update user info failed", e, ApiExceptionType.BAD_REQUEST );
        }
    }
    
    public void resetAdmin(String token) throws ApiServiceException {
        if (token.equals("datameetreality!")) {
            UserSessionManager.getInstance().resetAdminUsers();
        } else {
            throw new ApiServiceException("Invalid token to update system preferences", ApiExceptionType.UNAUTHORIZED);
        }
    }
}
