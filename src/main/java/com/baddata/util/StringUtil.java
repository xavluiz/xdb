/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class StringUtil {
    

    /**
     * normalize a given phone number to one of the following
     * +15551231212
     * +<anynumbergreaterthan10>
     * @param phoneNumber
     * @return
     */
    public static String standardizePhoneNumber(String phoneNumber) {
        if ( StringUtils.isNotBlank(phoneNumber) ) {
            String[] phoneParts = phoneNumber.split("[^0-9]");
            phoneNumber = StringUtils.join(phoneParts, "");
            
            int len = phoneNumber.length();
            if ( len <= 10 ) {
                phoneNumber = "+1" + phoneNumber;
            } else {
                phoneNumber = "+" + phoneNumber;
            }
            
            return phoneNumber;
        }
        // it's null or empty, return empty string
        return "";
    }
    
    public static String buildUserRoleTypeDisplay(String roleType) {
        if ( StringUtils.isBlank(roleType) ) {
            return "";
        }
        
        String[] roleTypes = roleType.split("_");
        for ( int i = 0; i < roleTypes.length; i++ ) {
            roleTypes[i] = String.valueOf( roleTypes[i].charAt(0) ).toUpperCase() + "" + roleTypes[i].substring( 1 );
        }
        
        return StringUtils.join(roleTypes, " ");
    }
    
    public static String getDurationBreakdown(long millis) {
        if ( millis < 0 ) {
            return "Milliseconds is less than zero, unable to build duration breakdown.";
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder(64);
        sb.append(days).append(" Days ");
        sb.append(hours).append(" Hours ");
        sb.append(minutes).append(" Minutes ");
        sb.append(seconds).append(" Seconds");

        return(sb.toString());
    }
    
    public static boolean isValidEmailAddress(String email) {
        String emailPattern = "^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}";
        Pattern p = Pattern.compile(emailPattern);
        Matcher m = p.matcher(email);
        return m.matches();
    }
    
    public static String buildExactMatchKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        keyword = keyword.trim();
        
        if (keyword.indexOf('"') != 0) {
            keyword = "\"" + keyword + "\"";
        }
        return keyword;
    }
    
}
