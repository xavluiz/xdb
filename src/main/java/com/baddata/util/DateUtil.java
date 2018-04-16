/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved
 */
package com.baddata.util;



import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.baddata.exception.BaddataException;
import com.baddata.log.Logger;
import com.baddata.manager.salesforce.SalesforceDataLoadManager;

public class DateUtil {
	
	private static Logger logger = Logger.getLogger(SalesforceDataLoadManager.class.getName());

    public static final long SECOND_IN_MILLIS = 1000;
    public static final long MINUTE_IN_MILLIS = SECOND_IN_MILLIS * 60;
    
    public static String shortDatePattern = "MM/dd/yy".intern();
    public static String shortestDatePattern = "M/d/yy".intern();
    public static String shortDateTimePattern = "MM/dd/yy HH:mm".intern();
    public static String dateOnlyFormatterPattern = "yyyy-MM-dd".intern();
    public static String dateAndTimeFormatterPattern = "yyyy-MM-dd HH:mm".intern();
    public static String iso8601DateFormatPatternNoMillis = "yyyy-MM-dd'T'HH:mm:ss".intern();
    public static String iso8601DateFormatNoTz = "yyyy-MM-dd HH:mm:ss.SSS".intern();
    
    // i.e. 10/08/16
    public static DateTimeFormatter shortDateFormatter = DateTimeFormat.forPattern(shortDatePattern);
    // i.e. 6/07/16 8:55
    public static DateTimeFormatter shortDateTimeFormatter = DateTimeFormat.forPattern(shortDateTimePattern);
    // i.e. 2014-07-15
    public static DateTimeFormatter dateOnlyDateTimeFormatter = DateTimeFormat.forPattern( dateOnlyFormatterPattern );
    // i.e. 2017-07-15 04:15
    public static DateTimeFormatter dateAndTimeFormatter = DateTimeFormat.forPattern(dateAndTimeFormatterPattern);
    // i.e. 2017-01-17T18:45:04-08:00
    public static DateTimeFormatter iso8601DateFormatterNoMillis = DateTimeFormat.forPattern(iso8601DateFormatPatternNoMillis);
    // i.e. 2015-03-15 11:00:48.744
    public static DateTimeFormatter iso8601DateFormatterNoTz = DateTimeFormat.forPattern( iso8601DateFormatNoTz );
    // i.e. 2017-02-04T00:00:00-08:00
    public static DateTimeFormatter momentJsFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    // i.e. 2018-02-03T00:58:54.000+0000
    public static DateTimeFormatter isoFormatter = ISODateTimeFormat.dateTime();
    
    private static DateTime staticDt = new DateTime();
    
    static {
    		isoFormatter = isoFormatter.withZoneUTC();
    		shortDateFormatter = shortDateFormatter.withZoneUTC();
    		shortDateTimeFormatter = shortDateTimeFormatter.withZoneUTC();
    		dateOnlyDateTimeFormatter = dateOnlyDateTimeFormatter.withZoneUTC();
    		dateAndTimeFormatter = dateAndTimeFormatter.withZoneUTC();
    		iso8601DateFormatterNoMillis = iso8601DateFormatterNoMillis.withZoneUTC();
    		iso8601DateFormatterNoTz = iso8601DateFormatterNoTz.withZoneUTC();
    		momentJsFormat = momentJsFormat.withZoneUTC();
    }
    
    public static DateTime buildUtcDateTime(String value) throws BaddataException {
    		DateTime dt = null;
    		try {
    			dt = isoFormatter.parseDateTime(value);
    		} catch (Exception e) {
    			try {
    				dt = dateOnlyDateTimeFormatter.parseDateTime(value);
    			} catch (Exception e1) {
    				try {
    					dt = momentJsFormat.parseDateTime(value);
    				} catch (Exception e2) {
    					// unable to parse date
    					throw new BaddataException("Failed formatting date value '" + value + "'. " + AppUtil.getErrMsg(e2));
    				}
    			}
    		}
    		return dt;
    }
    
    public static DateTime buildDateTime(String value, DateTimeFormatter formatter) throws BaddataException {
        try {
            return formatter.parseDateTime(value);
        } catch (Exception e) {
            throw new BaddataException("Failed formatting date value '" + value + "'. " + AppUtil.getErrMsg(e));
        }
    }
    
    public static DateTime buildDateTimeForISO8601NoTz(String value) throws BaddataException {
        try {
            return iso8601DateFormatterNoTz.parseDateTime(value);
        } catch (Exception e) {
            throw new BaddataException("Failed formatting date value '" + value + "'. " + AppUtil.getErrMsg(e));
        }
    }
    
    public static String formatDate(DateTime d, DateTimeFormatter formatter) {
    		return d.toString(formatter);
    }
    
    public static String formatDate(String value, DateTimeFormatter formatter) throws BaddataException {
    		return formatDate(buildDateTime(value, formatter), formatter);
    }
    
    public static DateTime convertDateTimeToUtc(DateTime dt) {
    		DateTime convertedDt = new DateTime(dt);
    		return convertedDt.toDateTime(DateTimeZone.UTC);
    }
    
    public static DateTime getDateValueFromString(String value, DateTime defaultValue) {
		if ( StringUtils.isNotBlank( value ) ) {
			try {
				return DateUtil.buildUtcDateTime(value);
			} catch (BaddataException e) {
				logger.error("Failed to build datetime using value '" + value + "'.", e);
			}
		}
		return defaultValue;
	}
	
	public static DateTime getUtcDateUsingSpecifiedTimezone(String value, DateTime defaultValue, DateTimeZone specifiedTz) {
		DateTime dt = null;
		
		if (value != null) {
			try {
				dt = DateUtil.buildUtcDateTime(value);
			} catch (BaddataException e) {
				logger.error("Failed to build datetime using value '" + value + "'.", e);
				dt = new DateTime(defaultValue);
			}
			
			if (dt != null) {
				DateTime dtWithSpecifiedTz = new DateTime(
						dt.getYear(), dt.getMonthOfYear(), dt.getDayOfMonth(),
						dt.getHourOfDay(), dt.getMinuteOfHour(), dt.getSecondOfMinute(),
						dt.getMillisOfSecond(), specifiedTz);
				
				dtWithSpecifiedTz = dtWithSpecifiedTz.toDateTime(DateTimeZone.UTC);
				
				return dtWithSpecifiedTz;
			}
		} else if (defaultValue != null) {
			dt = new DateTime(defaultValue);
		}
		
		return dt;
	}
}
