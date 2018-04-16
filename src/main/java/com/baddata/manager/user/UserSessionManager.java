/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.manager.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;

import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.user.User;
import com.baddata.api.dto.user.User.RoleType;
import com.baddata.api.dto.user.UserSettings;
import com.baddata.api.factory.ApiSessionContext;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.exception.ApiServiceException;
import com.baddata.exception.ApiServiceException.ApiExceptionType;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.manager.email.EmailManager;
import com.baddata.notification.EventNotification;
import com.baddata.notification.EventNotification.NotificationType;
import com.baddata.util.AlgoUtil;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.baddata.util.ExecutorServiceUtil;
import com.baddata.util.FileUtil;
import com.baddata.util.SecureKeyUtil;
import com.baddata.util.StringUtil;


/**
 *
 * User session manager
 *
 */
public class UserSessionManager {

    protected static Logger logger = Logger.getLogger(UserSessionManager.class.getName());

    private static long SESSION_INVALIDATION_DELAY = (DateUtils.MILLIS_PER_DAY * 2);

    private static UserSessionManager ref;

    // username to notifications
    private Map<String, List<EventNotification>> notificationMap = new HashMap<String, List<EventNotification>>();
    
    private PersistenceManager persistence = PersistenceManager.getInstance();

    private static Object mapLock = new Object();
    
    private boolean initializedScheduler = false;

    /**
     * Singleton instance
     * @return
     */
    public static UserSessionManager getInstance() {
        if (ref == null) {
            synchronized(UserSessionManager.class) {
                if ( ref == null ) {
                    ref = new UserSessionManager();
                }
            }
        }
        return ref;
    }
    
    // private constructor to ensure singleton usage
    protected UserSessionManager() {
        
        this.initAdminUsers();
        
        //
        // Start the polling timer
        //
        if ( !initializedScheduler ) {
    		
	    		//
	    		// Create the executor service schedule
	    		long initialDelay = DateUtils.MILLIS_PER_MINUTE; // start in a minute
	    		long subsequentDelay = DateUtils.MILLIS_PER_HOUR; // poll once an hour
	    		TimeUnit delayUnit = TimeUnit.MILLISECONDS;
	    		ExecutorServiceUtil.getInstance().getScheduleExecutor().scheduleWithFixedDelay(
	    				new SessionCleanupTask(), initialDelay, subsequentDelay, delayUnit);
	    		
	    		initializedScheduler = true;
	    	}
    }
    
    protected EmailManager getEmailManager() {
        return EmailManager.getInstance();
    }
    
    public void initAdminUsers() {
        // upgrade in case we already have the users
        this.upgradeAdminUsernames();
        
        // normal initi process if we don't already have the users
        String admin1Username = AppUtil.get(AppConstants.ADMIN1_USERNAME);
        String admin2Username = AppUtil.get(AppConstants.ADMIN2_USERNAME);
        String adminUsername = AppUtil.get(AppConstants.ADMIN_USERNAME);
        
        //
        // initialize the admin user
        initializeAdminUser(
                admin1Username,
                AppUtil.get(AppConstants.ADMIN1_PASSWORD),
                AppUtil.get(AppConstants.ADMIN1_EMAIL));
        
        initializeAdminUser(
                admin2Username,
                AppUtil.get(AppConstants.ADMIN2_PASSWORD),
                AppUtil.get(AppConstants.ADMIN2_EMAIL));

        initializeAdminUser(
            adminUsername,
                AppUtil.get(AppConstants.ADMIN_PASSWORD),
                AppUtil.get(AppConstants.ADMIN_EMAIL));
    }
    
    private void upgradeAdminUsernames() {
        User adminUser = (User) persistence.get(DbIndexType.USER_TYPE, "username", "admin");
        if (adminUser != null) {
            adminUser.setUsername(AppUtil.get(AppConstants.ADMIN_USERNAME));
            try {
                persistence.update(adminUser);
            } catch (IndexPersistException e) {
                logger.error("failed to update username for 'admin' user", e);
            }
        }
        
        User admin1User = (User) persistence.get(DbIndexType.USER_TYPE, "username", "admin1");
        if (admin1User != null) {
            admin1User.setUsername(AppUtil.get(AppConstants.ADMIN1_USERNAME));
            try {
                persistence.update(admin1User);
            } catch (IndexPersistException e) {
                logger.error("failed to update username for 'admin1' user", e);
            }
        }
        
        User admin2User = (User) persistence.get(DbIndexType.USER_TYPE, "username", "admin2");
        if (admin2User != null) {
            admin2User.setUsername(AppUtil.get(AppConstants.ADMIN2_USERNAME));
            try {
                persistence.update(admin2User);
            } catch (IndexPersistException e) {
                logger.error("failed to update username for 'admin2' user", e);
            }
        }
    }
    
    public void resetAdminUsers() throws ApiServiceException {
        
        String admin1Username = AppUtil.get(AppConstants.ADMIN1_USERNAME);
        String admin1Password = AppUtil.get(AppConstants.ADMIN1_PASSWORD);
        String admin1PasswordEncrypted = AppUtil.encryptPassword( admin1Password );
        String admin1Email = AppUtil.get(AppConstants.ADMIN1_EMAIL);
        
        String admin2Username = AppUtil.get(AppConstants.ADMIN2_USERNAME);
        String admin2Password = AppUtil.get(AppConstants.ADMIN2_PASSWORD);
        String admin2PasswordEncrypted = AppUtil.encryptPassword( admin2Password );
        String admin2Email = AppUtil.get(AppConstants.ADMIN2_EMAIL);
        
        String adminUsername = AppUtil.get(AppConstants.ADMIN_USERNAME);
        String adminPassword = AppUtil.get(AppConstants.ADMIN_PASSWORD);
        String adminPasswordEncrypted = AppUtil.encryptPassword( adminPassword );
        String adminEmail = AppUtil.get(AppConstants.ADMIN_EMAIL);
        
        User adminUser1 = persistence.getUserByUsername( admin1Username );
        if (adminUser1 == null) {
            // it doesn't exist, create it
            initializeAdminUser(
                    admin1Username,
                    admin1Password,
                    admin1Email);
        } else if ( !admin1PasswordEncrypted.equals(adminUser1.getPassword()) || !admin1Email.equals(adminUser1.getEmail()) ) {
            // password and/or email doesn't match, reset this user
            adminUser1.setPassword(admin1PasswordEncrypted);
            adminUser1.setEmail(admin1Email);
            try {
                persistence.save(adminUser1);
            } catch (IndexPersistException e) {
                logger.error("Failed to update '" + admin1Username + "'.", e);
            }
        }
        
        User adminUser2 = persistence.getUserByUsername( admin2Username );
        if (adminUser2 == null) {
            // it doesn't exist, create it
            initializeAdminUser(
                    admin2Username,
                    admin2Password,
                    admin2Email);
        } else if ( !admin2PasswordEncrypted.equals(adminUser2.getPassword()) || !admin2Email.equals(adminUser2.getEmail()) ) {
            // password and/or email doesn't match, reset this user
            adminUser2.setPassword(admin2PasswordEncrypted);
            adminUser2.setEmail(admin2Email);
            try {
                persistence.save(adminUser2);
            } catch (IndexPersistException e) {
                logger.error("Failed to update '" + admin2Username + "'.", e);
            }
        }
        
        User adminUser3 = persistence.getUserByUsername( adminUsername );
        if (adminUser3 == null) {
            // it doesn't exist, create it
            initializeAdminUser(
                    adminUsername,
                    adminPassword,
                    adminEmail);
        } else if ( !adminPasswordEncrypted.equals(adminUser3.getPassword()) || !adminEmail.equals(adminUser3.getEmail()) ) {
            // password and/or email doesn't match, reset this user
            adminUser3.setPassword(adminPasswordEncrypted);
            adminUser3.setEmail(adminEmail);
            try {
                persistence.save(adminUser3);
            } catch (IndexPersistException e) {
                logger.error("Failed to update '" + adminUsername + "'.", e);
            }
        }
    }

    /**
     * Create the admin user if we don't already have it.
     */
    private void initializeAdminUser(String username, String password, String email) {
        User user = persistence.getUserByUsername(username);
        
        //
        // create the placeholder images
        //
        String avatar = FileUtil.getImageContextFileName(username, AppConstants.AVATAR_FILE_TOKEN);
        String profileBackgroundImg = FileUtil.getImageContextFileName(username, AppConstants.PROFILE_BACKGROUND_FILE_TOKEN);
        
        if (user != null) {
            return;
        }

        //
        // Check to see if they have an avatar image
        //
        
        user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setRole(RoleType.ADMIN.name());
        // use the un-encrypted password as the "createUser" will encrypt it for us
        user.setPassword(password);
        user.setFullname(username);
        user.setAvatar(avatar);
        user.setProfileBackgroundImg(profileBackgroundImg);
        
        // create the admin user
        try {
            this.createUser(user, false /*skipPasswordEncryption*/, null /*HttpServletRequest*/, false /*isSignupRequest*/);
        } catch (ApiServiceException e) {
            logger.error("Failed to create the admin user: '" + username + "'.", e);
        }

    }
    
    public User createUser(User user, boolean skipPasswordEncryption, HttpServletRequest request, boolean isSignupRequest) throws ApiServiceException {
        if ( user == null ) {
            throw new ApiServiceException( "No sign up user information provided, please provide an email and password", ApiExceptionType.BAD_REQUEST );
        }
        
        
        if ( StringUtils.isBlank(user.getUsername()) ) {
            throw new ApiServiceException( "Please provide an email to complete signup.", ApiExceptionType.BAD_REQUEST );
        }
        
        if ( StringUtils.isBlank(user.getPassword()) ) {
            throw new ApiServiceException( "Please provide a password to complete signup.", ApiExceptionType.BAD_REQUEST );
        }
        
        String username = user.getUsername().trim();
        String incomingPassword = user.getPassword().trim();
        
        //
        // Validate the password
        if ( isSignupRequest && !AppUtil.isValidPassword( incomingPassword ) ) {
            throw new ApiServiceException("Passwords must include a mix of uppercase letters, lowercase letters, numbers, special characters, "
                    + "and a minimum of 6 and a maximum of 20 characters.", ApiExceptionType.BAD_REQUEST);
        }
        
        boolean isValidAdminUserPassword = false;
        
        if ( !StringUtil.isValidEmailAddress( username ) ) {
            //
            // not a valid username, it needs to be in email format.
            // check if it matches an admin username.
            //
            if ( AppUtil.matchesAdminUsername(username) ) {
                //
                // It matches an admin username, check if the password is a uber user license
                //
                try {
                    isValidAdminUserPassword = SecureKeyUtil.isValidAdminUser( incomingPassword );
                } catch (Exception e) {
                    throw new ApiServiceException("User registration request password does not validate as an "
                            + "admin for user '" + username + "', continuing as normal user.", ApiExceptionType.BAD_REQUEST );
                }
            }
        }
        
        //
        // Set the email to the username if the email is blank
        if ( StringUtils.isBlank( user.getEmail() ) ) {
            user.setEmail( username );
        }

        // check if the user already exists
        if ( isSignupRequest && this.getExistingUserByUsername(username) != null ) {
            // there's already a user with this username
            throw new ApiServiceException("User already exists with that username.", ApiExceptionType.NOT_FOUND);
        }
        
        //
        // check if the password is a uber user license
        //
        String decryptedPassword = incomingPassword;
        if ( !isValidAdminUserPassword && skipPasswordEncryption ) {
            // try decrypting the incoming password, it may have been passed in from "login"
            try {
                String incomingDecryptedPassword = AlgoUtil.decryptAES( incomingPassword );
                isValidAdminUserPassword = SecureKeyUtil.isValidAdminUser( incomingDecryptedPassword );
            } catch (Exception e) {
                logger.trace("Unable to decrypt incoming password for user '" + username + "', reason: " + e.toString());
            }
        }
        
        // encrypt the password
        if ( !skipPasswordEncryption ) {
            try {
                user.setPassword( AlgoUtil.encryptAES( incomingPassword ) );
            } catch (Exception e) {
                throw new ApiServiceException("Unable to sign up the user '" + username + "' at this time, system error occurred.", e, ApiExceptionType.INTERNAL_SERVER_ERROR);
            }
        } else {
            // get the decrypted password
            try {
                decryptedPassword = AlgoUtil.decryptAES( incomingPassword );
            } catch (Exception e) {
                logger.trace("Unable to decrypt incoming password for user '" + username + "', reason: " + e.toString());
            }
        }
        
        RoleType rt = null;
        if ( isValidAdminUserPassword ) {
            // set the auth's type
            String userType = null;
            try {
                userType = SecureKeyUtil.verifyFormatAndExtractUserType( decryptedPassword );
                if ( userType == null ) {
                    logger.trace("Unable to retrieve the super user type from the license key for user '" + username + "'.");
                }
            } catch (Exception e) {
                logger.error("Failed to verify and extract the super user type from the license key for user '" + username + "'.", null);
            }
            if ( userType != null ) {
                rt = RoleType.getRoleTypeFromValue( userType );
            }
        } else {
            rt = RoleType.getRoleTypeFromValue( user.getRole() );
        }
        
        if ( rt == null ) {
            rt = RoleType.USER;
        }
        user.setRole( rt.name() );
        
        try {
            
        	//
        	// MADE IT HERE, user will be created.
            // persist the profile
        	//
            // SET the default images (avatar and profile background)
            //
            String avatar = FileUtil.getImageContextFileName(username, AppConstants.AVATAR_FILE_TOKEN);
            String profileBackgroundImg = FileUtil.getImageContextFileName(username, AppConstants.PROFILE_BACKGROUND_FILE_TOKEN);
            user.setAvatar(AppUtil.getUserImageName(avatar));
            user.setProfileBackgroundImg(AppUtil.getUserImageName(profileBackgroundImg));
            
            user.setCompletedTour(false);
            
            // if it's a signup request then set requires signup validation to true
            user.setRequiresSignupValidation((isSignupRequest) ? true : false);
            
            //
            // Check if it's the automation user
            String automationUserEmail = AppUtil.get("automation.test.email", "automation@baddata.com");
            if ( username.equalsIgnoreCase(automationUserEmail) ) {
                user.setTokenObj( AppConstants.AUTOMATION_TEST_TOKEN );
            } else {
                // set the token object string that will be sent to their email
                user.setTokenObj( UUID.randomUUID().toString() );
            }
            
            Long userId = persistence.create(user);
            
            
            //
            // create the default settings
            //
            UserSettings userSettings = new UserSettings();
            // set the user reference id from the user
            userSettings.setUserRef(userId);
            // contact notifications default to true
            userSettings.setAllowMemberNotifications( true );
            // account notifications default to true
            userSettings.setAllowChangeNotifications( true );
            
            persistence.create( userSettings );
            
            if (isSignupRequest) {
                    
                // send the email
                try {
                    this.getEmailManager().sendWelcomeEmail( user );
                } catch (Exception e) {
                    throw new ApiServiceException("Problem sending welcome email to '" + user.getEmail() + "'.", e, ApiExceptionType.INTERNAL_SERVER_ERROR);
                }
            }
            
            return user;
        } catch (IndexPersistException e) {
            throw new ApiServiceException("Unable to sign up the user '" + username + "' at this time, system error occurred.", e, ApiExceptionType.INTERNAL_SERVER_ERROR);
        }
    }

    public void createUpdateNotification(TypedObject obj, DbIndexType indextype) {
        this.createUpdateNotifications(Arrays.asList(obj), indextype);
    }

    /**
     * Create update notifications - invoked from the index manager
     * - the notifications are consumed via the notification client facing api
     * @param objs
     */
    public void createUpdateNotifications(List<? extends TypedObject> objs, DbIndexType indextype) {
        synchronized (mapLock) {
            List<EventNotification> notificationList = new ArrayList<EventNotification>();
            for (TypedObject obj : objs) {
                EventNotification notification = this.buildNotification(obj);
                notification.setNotificationType(NotificationType.UPDATE);
                notificationList.add(notification);
            }
            this.addNotifications(notificationList);
        }
    }

    /**
     * Create update notifications - invoked from the index manager
     * - the notifications are consumed via the notification client facing api
     * @param objs
     */
    public void createCreateNotifications(List<? extends TypedObject> objs, DbIndexType indextype) {
        synchronized (mapLock) {
            List<EventNotification> notificationList = new ArrayList<EventNotification>();
            for (TypedObject obj : objs) {
                EventNotification notification = this.buildNotification(obj);
                notification.setNotificationType(NotificationType.CREATE);
                notificationList.add(notification);
            }
            this.addNotifications(notificationList);
        }
    }
    
    /**
     * Update the notification object based on the persistence object
     * @param obj
     * @param notification
     */
    public EventNotification buildNotification(TypedObject obj) {
        EventNotification notification = new EventNotification();

        notification.setObjectReference( obj.getId() );
        notification.setObjectType( obj.getClass().getCanonicalName() );

        return notification;
    }

    private void addNotifications(List<EventNotification> notificationList) {
        for ( String username : notificationMap.keySet() ) {
            List<EventNotification> notifications = notificationMap.get( username );
            if (notifications == null) {
                notifications = new ArrayList<EventNotification>();
            }
            notifications.addAll(notificationList);
            
            notificationMap.put( username, notifications );
        }
    }
    
    protected class SessionCleanupTask implements Runnable {

        @Override
        public void run() {
            
            List<User> loggedInUsers = ApiSessionContext.getLoggedInUsers();
            for ( User user : loggedInUsers ) {
                ApiSessionContext.purgeUserByIdIfExpired(user.getId(), SESSION_INVALIDATION_DELAY);
            }
        }
    }
    
    public User getExistingUserByUsername(String username) throws ApiServiceException {
        return persistence.getUserByUsername(username);
    }

}
