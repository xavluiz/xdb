/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.broker;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import com.baddata.api.dto.user.User;
import com.baddata.api.dto.user.User.RoleType;
import com.baddata.api.factory.ApiSessionContext;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.exception.ApiServiceException;
import com.baddata.exception.ApiServiceException.ApiExceptionType;
import com.baddata.exception.IndexPersistException;
import com.baddata.manager.user.UserManager;
import com.baddata.manager.user.UserSessionManager;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.baddata.util.FBUserUtil;
import com.baddata.util.FBUserUtil.DecodedSignedRequest;
import com.baddata.util.FileUtil;
import com.baddata.util.SecureKeyUtil;

public class SessionBrokerImpl extends BaseBroker {
	
	private UserSessionManager userSessionMgr;
	private UserManager userMgr;
	
	public SessionBrokerImpl(Long userref, String userName) {
        super(userref, userName);
		init();
	}
	
	protected void init() {
		userSessionMgr = UserSessionManager.getInstance();
		userMgr = UserManager.getInstance();
	}

    public User login(User creds, HttpServletRequest request, SearchSpec searchSpec) throws ApiServiceException {
        User user = null;
        RoleType roleType = RoleType.getRoleTypeFromValue(creds.getRole());
        switch ( roleType ) {
            case FACEBOOK:
                user = this.loginFbUser(creds);
                break;
            case GOOGLE:
                user = this.loginGoogleUser(creds);
                break;
            case SALESFORCE:
                user = this.loginSalesforceUser(creds);
                break;
            default:
                // USER, ADMIN, AUDITOR, SUPPORT
                user = this.loginBaddataUser(creds, searchSpec);
        }
        
        if ( user != null ) {
            String authToken = SecureKeyUtil.createTwentyByteRandomHex();
            user.setAuthToken( authToken );
            try {
                persistence.save(user);
            } catch (IndexPersistException e) {
                logger.error("Failed to save auth token '" + authToken + "' for user '" + user.getUsername() + "'.", e);
            }
            ApiSessionContext.updateUserContext(user);
            userMgr.clearOutgoingPassword(user);
            return user;
        } else {
            throw new ApiServiceException("Unable to locate user to establish a session", ApiExceptionType.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Login a baddata type user.
     * @param creds
     * @return
     * @throws ApiServiceException
     */
    protected User loginBaddataUser(User creds, SearchSpec searchSpec) throws ApiServiceException {
        
        if ( creds == null || StringUtils.isBlank(creds.getUsername()) ) {
            throw new ApiServiceException("Please provide a valid username to login.", ApiExceptionType.BAD_REQUEST);
        }
        
        //
        // Get the username and password
        String username = creds.getUsername().trim();
        String incomingPassword = ( StringUtils.isNotBlank(creds.getPassword()) ) ? creds.getPassword().trim() : null;

        User existingUser = null;
        String tokenObj = ( StringUtils.isNotBlank(creds.getTokenObj() ) ) ? creds.getTokenObj().trim() : null;
        
        if ( tokenObj != null ) {
            //
            // Check if there's a user matching this token object, and if so, does it require signup validation?
            //
            existingUser = (User) persistence.get( DbIndexType.USER_TYPE, "tokenobj", tokenObj );
            
            if ( existingUser != null && existingUser.isRequiresSignupValidation() ) {
                
                // July 21, 1983 01:15:00
                // DateTime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour, int secondOfMinute) 
                DateTime automationDateTime = new DateTime(1983, 7, 21, 1, 15, 0);
                // it has to match the automation datetime exactly if i't s an automation email
                DateTime reqDateTime = new DateTime(searchSpec.get_ts());
                if (tokenObj.equals(AppConstants.AUTOMATION_TEST_TOKEN) && !reqDateTime.equals(automationDateTime) ) {
                    // throw an exception, not allowed to login an automation test token user if the automation date doesn't match
                    logger.error("Failed to validate user logon. "
                            + "The token '" + tokenObj + "' matched the following user '" + existingUser.getUsername() + "' "
                            + "but the timestamp '" + reqDateTime.toString() + "' was invalid compared to '" + automationDateTime.toString() + "'.", null);
                    throw new ApiServiceException("Invalid login token information.", ApiExceptionType.BAD_REQUEST);
                }
                
                if ( existingUser.getUsername().equalsIgnoreCase( username ) ) {
                    //
                    // The user has successfully signed in, return the user info
                    // and update requires signup validation to false and tokenobj to null/empty
                    //
                    existingUser.setTokenObj("");
                    existingUser.setRequiresSignupValidation(false);
                    try {
                        persistence.save(existingUser);
                    } catch (IndexPersistException e) {
                        logger.error("Failed to update the user '" + username + "' during signup validation", e);
                    }
                    
                    userMgr.clearOutgoingPassword(existingUser);
                    
                    // create a new token
                    return existingUser;
                } else {
                    logger.error("Failed to validate user logon. "
                            + "The token '" + tokenObj + "' matched the following user '" + existingUser.getUsername() + "' "
                            + "but the username passed in was '" + username + "'.", null);
                }
            }
            
        }
        
        if ( incomingPassword == null ) {
            throw new ApiServiceException("Please provide a password to login.", ApiExceptionType.BAD_REQUEST);
        }
        
        //
        // encrypt the password to match what is persisted
        String encryptedPassword = AppUtil.encryptPassword(incomingPassword);
	    
        //
        // Check to see if it's a proxy login, if so, we'll fetch the user
        // the proxy user is requesting to view the app as.
        //
        existingUser = AppUtil.getUserFromProxy(username, incomingPassword);
        if (existingUser != null) {
            // that means it was a proxy login
            return existingUser;
        }
		
		//
		// Get the user matching this username, but since our normal get existing user by username
        // is case sensitive, we'll fech by the content type
        //
        List<User> users = (List<User>) persistence.getAllForKeywordFromContent(DbIndexType.USER_TYPE, username);
        if ( users != null ) {
            for ( User user : users ) {
                if ( user.getUsername().equalsIgnoreCase(username) ) {
                    existingUser = user;
                    break;
                }
            }
        }
        
        if ( existingUser == null ) {
            existingUser = userSessionMgr.getExistingUserByUsername(username);
        }
		
		if ( existingUser == null ) {
		    throw new ApiServiceException("The username or password you have entered is invalid.", ApiExceptionType.VALIDATION_ERROR);
		}
		
		if ( existingUser.isRequiresSignupValidation() ) {
		    throw new ApiServiceException("Signup validation required. Please confirm your account by clicking on the welcome email.", ApiExceptionType.VALIDATION_ERROR);
		}
        
		if ( existingUser.getPassword().equals(encryptedPassword) ) {
            
            userMgr.clearOutgoingPassword(existingUser);
		    
            // create a new token
		    return existingUser;
		}
		
		//
		// A user exists that matches this username, but the passwords don't match.
		// Throw the generic exception that the username or password doesn't match.
		throw new ApiServiceException("The username or password you have entered is invalid.", ApiExceptionType.VALIDATION_ERROR);
    }
    
    protected User loginGoogleUser(User creds) throws ApiServiceException {
        //
        // Send this token to your server (preferably as an Authorization header)
        // Have your server decode the id_token by using a common JWT library such
        // as jwt-simple or by sending a GET request to
        // https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=YOUR_TOKEN_HERE
        // The returned decoded token should have an hd key equal to the hosted domain you'd like to restrict to.
        //
        String username = creds.getUsername();
        
        //
        // Check if this user already exists
        User existingUser = persistence.getUserByUsername(username);
        if ( existingUser == null ) {
            //
            // create the user for the 1st time
            // no need to encrypt the password, it's already done by google
            return this.createUser(username, creds.getPassword() /*encryptedPassword*/, RoleType.GOOGLE, creds.getTokenObj());
        }
        // update the user in case anything has changed
        this.updateUser(existingUser, creds.getPassword());
        
        return existingUser;
    }
    
    /**
     * Login a salesforce type user.
     * https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/intro_understanding_username_password_oauth_flow.htm
     * @param creds
     * @return
     * @throws ApiServiceException
     */
    protected User loginSalesforceUser(User creds) throws ApiServiceException {
        String username = creds.getUsername();
        String passwd = creds.getPassword();
        
        //
        // Check if we have an existing user based for this username
        User existingUser = persistence.getUserByUsername(username);
        String encryptedPassword = AppUtil.encryptPassword(passwd);
        
        if ( existingUser == null ) {
            // create a baddata user as well
            existingUser = this.createUser(username, encryptedPassword, RoleType.SALESFORCE, null /*tokenObj*/);
        } else {
            // update the user info
            this.updateUser(existingUser, encryptedPassword);
        }
        
        return existingUser;
    }
    
    /**
     * Login a facebook type user.
     * @param creds
     * @return
     * @throws ApiServiceException
     */
    protected User loginFbUser(User creds) throws ApiServiceException {
        DecodedSignedRequest facebookSignedReq = FBUserUtil.isValidFBConnectionAuth( creds );
        if (facebookSignedReq == null) {
            throw new ApiServiceException("Unable to retrieve facebook authentication identity.", ApiExceptionType.VALIDATION_ERROR);
        }
        String username = facebookSignedReq.getUser_id();
        String password = creds.getPassword();
        
        //
        // Get the user matching this username
        User existingUser = persistence.getUserByUsername(username);
        String encryptedPassword = AppUtil.encryptPassword(password);
        
        if ( facebookSignedReq != null ) {
            // it's a valid facebook user signin request
            if ( existingUser == null ) {
                //
                // it's a facebook user login and the user and
                // auth has not yet been created, create it.
                return this.createUser(username, encryptedPassword, RoleType.FACEBOOK, null /*tokenObj*/);
            } else if ( existingUser.getPassword().equals(encryptedPassword) ) {
                return existingUser;
            }
        }
    
        //
        // A user exists that matches this username, but the passwords don't match.
        // Throw the generic exception that the username or password doesn't match.
        throw new ApiServiceException("The username or password you have entered is invalid.", ApiExceptionType.VALIDATION_ERROR);
    }
    
    protected boolean isAdminUser(String username) {
        String admin1Username = AppUtil.get(AppConstants.ADMIN1_USERNAME);
        String admin2Username = AppUtil.get(AppConstants.ADMIN2_USERNAME);
        String adminUsername = AppUtil.get(AppConstants.ADMIN_USERNAME);
        if ( username.equals(admin1Username) 
                || username.equals(admin2Username) 
                || username.equals(adminUsername) ) {
            return true;
        }
        return false;
    }
    
    protected void updateUser(User existingUser, String encryptedPassword) {
        existingUser.setPassword(encryptedPassword);
        try {
            persistence.update(existingUser);
        } catch (IndexPersistException e) {
            //
        }
    }
    
    protected User createUser(String username, String encryptedPassword, RoleType roleType, String tokenObj) 
            throws ApiServiceException {
        
        if ( StringUtils.isBlank(username) || StringUtils.isBlank(encryptedPassword) ) {
            throw new ApiServiceException("Please provide a username and password to create a user", ApiExceptionType.BAD_REQUEST);
        }
        
        User user = new User();
        
        // auth settings
        if ( tokenObj != null ) {
            user.setTokenObj(tokenObj);
        }
        user.setPassword(encryptedPassword);
        user.setUsername(username);
        user.setRole(roleType.name());
        
        // user settings
        user.setFullname(username);
        
        try {
            
            //
            // SET the default images (avatar and profile background)
            //
            String avatar = FileUtil.getImageContextFileName(username, AppConstants.AVATAR_FILE_TOKEN);
            String profileBackgroundImg = FileUtil.getImageContextFileName(username, AppConstants.PROFILE_BACKGROUND_FILE_TOKEN);
            user.setAvatar(AppUtil.getUserImageName(avatar));
            user.setProfileBackgroundImg(AppUtil.getUserImageName(profileBackgroundImg));
            
            // persist the user + auth object
            Long newUserId = persistence.create(user);
            user = (User) persistence.getById(DbIndexType.USER_TYPE, newUserId);
            if ( user == null ) {
                logger.error("Failed to fetch newly created " + roleType.name()  + " user '" + username + "'", null);
                return null;
            }
        } catch (IndexPersistException e) {
            logger.error("Failed to create new " + roleType.name()  + " user '" + username + "'.", e );
            return null;
        }
        return user;
    }

	public void logout(HttpServletRequest request) throws ApiServiceException {
	    String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
	    ApiSessionContext.removeUser( authorizationHeader, true /*isLogout*/ );
	}
	
	public String generateCsrfToken() {
	    String csrftoken = SecureKeyUtil.createTwentyByteRandomHex();
        ApiSessionContext.updateCsrfToken(userReferenceId, csrftoken);
        return csrftoken;
	}

}
