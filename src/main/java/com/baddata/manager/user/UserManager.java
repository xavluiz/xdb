/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.manager.user;

import java.io.File;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.baddata.api.dto.salesforce.SalesforceOauth2Creds;
import com.baddata.api.dto.user.User;
import com.baddata.api.dto.user.User.RoleType;
import com.baddata.api.dto.user.UserSettings;
import com.baddata.api.factory.ApiSessionContext;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.lucene.IndexPathInfo;
import com.baddata.exception.ApiServiceException;
import com.baddata.exception.ApiServiceException.ApiExceptionType;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.manager.salesforce.SalesforceApiHelper;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.baddata.util.FileUtil;

public class UserManager {
	
	protected static Logger logger = Logger.getLogger(UserManager.class.getName());
	
	private static UserManager ref;
	private PersistenceManager persistence = PersistenceManager.getInstance();

    /**
     * Singleton instance
     * @return
     */
    public static UserManager getInstance() {
        if (ref == null) {
            synchronized(UserManager.class) {
                if ( ref == null ) {
                    ref = new UserManager();
                }
            }
        }
        return ref;
    }
    
    // private constructor to ensure singleton usage
    private UserManager() {
    	this.init();
    }
    
    private void init() {
        //
    }
	
	public void deleteUser(Long userId) throws ApiServiceException {
	    
	    // get the user and delete it
        User user = (User) persistence.getById(DbIndexType.USER_TYPE, userId);
	    
	    if ( user == null ) {
	        throw new ApiServiceException("Invalid delete request.  User info not provided.", ApiExceptionType.BAD_REQUEST);
	    }
        
        if ( StringUtils.isNotBlank( user.getRole() ) && RoleType.getRoleTypeFromValue( user.getRole() ) == RoleType.ADMIN ) {
            throw new ApiServiceException("Invalid request, deletion not allowed for this type of user.", ApiExceptionType.BAD_REQUEST);
        }
        
        String username = user.getUsername();
	    
        //
        // Delete the user
        //
        try {
            persistence.delete(user);
            ApiSessionContext.removeUser(user.getAuthToken(), false /*isLogout*/);
            logger.info("Deleted user '" + username + "'");
        } catch (Exception e) {
            logger.error("Failed to delete user.", e );
            throw new ApiServiceException("Unable to delete the user. " + AppUtil.getErrMsg(e), ApiExceptionType.INTERNAL_SERVER_ERROR);
        }
        
        // delete the user images
        if ( username != null ) {
            FileUtil.deleteUserAccountImages(username);
        }
        
        //
        // Delete the user settings for this user
        //
        UserSettings userSettings = this.getUserSettings(user.getId());
        if ( userSettings != null ) {
            try {
                persistence.delete(userSettings);
            } catch (Exception e) {
                logger.error("Failed to delete user settings.", e );
            }
        }
        
        //
        // Delete the salesforce oauth account and all of their downloaded salesforce data
        //
        List<SalesforceOauth2Creds> credsList = SalesforceApiHelper.getInstance().getOauthCredentialList(user.getId());
        if (CollectionUtils.isNotEmpty(credsList)) {
            for ( SalesforceOauth2Creds creds : credsList ) {
                String oauthInfo = creds.toString();
                try {
                    SalesforceApiHelper.getInstance().deleteSalesforceDataForOauth(creds);
                } catch (Exception e) {
                    logger.error("Failed to delete salesforce creds and related data for oauth '" + oauthInfo + "'.", e );
                }
                // now delete their instance directory
                IndexPathInfo indexUserPathInfo = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME, creds.getDynamicInstanceTenantId(), null /*DbIndexType*/);
                File f = FileUtil.getLuceneIndex(indexUserPathInfo, false /*isSearch*/);
                FileUtil.deleteDir(f);
            }
        }
    }
	
	public UserSettings getUserSettings(Long userReferenceId) {
        return (UserSettings) persistence.getByUserRef(DbIndexType.USER_SETTINGS_TYPE, userReferenceId);
    }
	
	public void clearOutgoingPassword(User user) {
        if (user != null) {
            // remove the password
            user.setPassword("");
        }
    }
	
	public void upgradeUserImgVal(User existingUser) {
        boolean updateUserImgPath = false;
        String avatarVal = existingUser.getAvatar();
        String backgroundVal = existingUser.getProfileBackgroundImg();
        
        int slashIdx = avatarVal.lastIndexOf("/");
        if (slashIdx != -1 && avatarVal.length() - 1 > slashIdx) {
            avatarVal = avatarVal.substring(slashIdx + 1);
            existingUser.setAvatar(avatarVal);
            updateUserImgPath = true;
        }

        slashIdx = backgroundVal.lastIndexOf("/");
        if (slashIdx != -1 && backgroundVal.length() - 1 > slashIdx) {
            backgroundVal = backgroundVal.substring(slashIdx + 1);
            existingUser.setProfileBackgroundImg(backgroundVal);
            updateUserImgPath = true;
        }

        if (updateUserImgPath) {
            try {
                persistence.save(existingUser);
            } catch (IndexPersistException e) {
                logger.error("Failed to persist the user avatar and background path for user '" + existingUser.getUsername() + "'.", e);
            }
        }
    }
    

}
