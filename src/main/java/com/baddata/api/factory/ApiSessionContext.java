/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.factory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.baddata.api.dto.user.User;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;

/**
 * 
 * A thread local copy of the user session info
 *
 */
public class ApiSessionContext {
    
    private static final Logger logger = Logger.getLogger(ApiSessionContext.class.getName());

    private static Map<String, Long> loggedInUserMap = new HashMap<String, Long>();
    private static Map<Long, Date> loggedInUserStartDateMap = new HashMap<Long, Date>();
    private static Map<Long, String> loggedInUserIdToCsrfTokenMap = new HashMap<Long, String>();
    
    private static PersistenceManager persistence = PersistenceManager.getInstance();
    
    // this will be set by the CacheControlFilter to "https://app.baddata.com/"
    // if it's used by the test server, it'll be set to "https://test.baddata.com/" or "http://localhost:8080/"
    public static String sessionUrl = null;
    
    public static void updateUserContext(User user) {
        loggedInUserMap.put(user.getAuthToken(), user.getId());
        loggedInUserStartDateMap.put(user.getId(), new Date());
    }
    
    public static List<User> getLoggedInUsers() {
        List<User> loggedInUsers = new ArrayList<User>();
        for ( Long userId : loggedInUserMap.values() ) {
            User user = (User) persistence.getById(DbIndexType.USER_TYPE, userId);
            if (user != null) {
                loggedInUsers.add(user);
            }
        }
        
        return loggedInUsers;
    }
    
    public static void purgeUserByIdIfExpired(Long userId, long expireDuration) {
        Iterator<String> iter = loggedInUserMap.keySet().iterator();
        long now = System.currentTimeMillis();
        
        while ( iter.hasNext() ) {
            Long currentUserId = loggedInUserMap.get( iter.next() );
            if (currentUserId == null) {
                continue;
            }

            Date loggedInDate = loggedInUserStartDateMap.get( currentUserId );
            if ( loggedInDate == null ) {
                // uh-oh, no matching logged in user date, create one
                loggedInDate = new Date();
                loggedInUserStartDateMap.put( userId, loggedInDate );
            }
            
            if ( currentUserId.longValue() == userId.longValue() ) {
                boolean shouldExpire = (now - loggedInDate.getTime() > expireDuration);
                if (shouldExpire) {
                    // it should expire, remove it from both maps
                    iter.remove();
                    loggedInUserStartDateMap.remove( userId );
                    loggedInUserIdToCsrfTokenMap.remove( userId );
                }
                break;
            }
        }
    }
    
    public static User getUser(String token) {
        Long userId = loggedInUserMap.get(token);
        if (userId != null) {
            return (User) persistence.getById(DbIndexType.USER_TYPE, userId);
        }
        return null;
    }
    
    public static void removeUser( String token, boolean isLogout ) {
        Long userId = loggedInUserMap.remove( token );
        if ( userId != null ) {
            if (isLogout) {
                try {
                    User user = (User) persistence.getById(DbIndexType.USER_TYPE, userId);
                    if (user != null) {
                        user.setAuthToken("");
                        PersistenceManager.getInstance().update(user);
                    }
                } catch (IndexPersistException e) {
                    logger.error("Failed to invalidate authentication token during logout for user '" + userId + "'.", e);
                }
            }
            loggedInUserStartDateMap.remove( userId );
            loggedInUserIdToCsrfTokenMap.remove( userId );
        }
    }
    
    public static void updateCsrfToken( Long userId, String csrfToken ) {
        loggedInUserIdToCsrfTokenMap.put(userId, csrfToken);
    }
    
    public static String getCsrfToken( Long userId ) {
        return loggedInUserIdToCsrfTokenMap.get( userId );
    }
}
