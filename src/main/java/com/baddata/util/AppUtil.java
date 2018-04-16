/**
 * Copyright (c) 2016 by BadData.
 * All rights reserved
 */
package com.baddata.util;

import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.reflections.Reflections;

import com.baddata.api.dto.ApiDto;
import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.salesforce.OpportunityFieldHistory;
import com.baddata.api.dto.salesforce.OpportunityFieldHistory.OpportunityFieldHistoryType;
import com.baddata.api.dto.user.User;
import com.baddata.exception.ApiServiceException;
import com.baddata.exception.ApiServiceException.ApiExceptionType;
import com.baddata.exception.BaddataException;
import com.baddata.log.EventLogger;
import com.baddata.log.EventLogger.EventMessage;
import com.baddata.log.Logger;
import com.baddata.manager.user.UserSessionManager;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;


public class AppUtil {
    
    private static Logger logger = Logger.getLogger(AppUtil.class.getName());
    
    private static List<Class<? extends TypedObject>> dtoClasses = null;
    private static Map<Class<? extends ApiDto>, Long> idMap = Maps.newHashMap();
    private static JSONParser jsonParser = new JSONParser();
    
    private static String PROXY_USER_PATTERN = "<bd:";
    private static String PROXY_USERNAME = "proxy";
    private static String PROXY_PASSWORD = "00^^gives^MISTER^tomorrow^08";
    
    // format any number to something like the following
    // from 234235235.82343e-2 to 234235235.82
    private static DecimalFormat hundredsPrecisionNoCommasNumber = new DecimalFormat("###.##");
    public static DecimalFormat tensPrecisionWithCommasNumber = new DecimalFormat("#,###.#");
    
    private static Runtime runtime = Runtime.getRuntime();
    
    static {
        Set<Class<? extends TypedObject>> typedObjClassSet = getDTOClassSet();
        
        dtoClasses = new ArrayList<Class<? extends TypedObject>>();
        
        Iterator<Class<? extends TypedObject>> typedObjIter = typedObjClassSet.iterator();
        while (typedObjIter.hasNext() ) {
            Class<? extends TypedObject> typedObjClass = typedObjIter.next();
            dtoClasses.add( typedObjClass );
        }
        
        hundredsPrecisionNoCommasNumber.setRoundingMode(RoundingMode.DOWN);
        tensPrecisionWithCommasNumber.setRoundingMode(RoundingMode.UP);
    }
    
    public static String formatHundredsPrecisionNoCommaDouble(Double d) {
        if (d == null) {
            return "";
        }
        return hundredsPrecisionNoCommasNumber.format(d);
    }
    
    public static boolean isUnitTesting() {
        return AppUtil.getAsBoolean( AppConstants.UNIT_TESTING, false );
    }
    
    public static String getTenantId(String username, Long userId) {
    	if ( StringUtils.isBlank(username) || userId == null ) {
    		return null;
    	}
    	return username + "_" + userId;
    }

	public static String removeEndingChar(String source, char c) {
		if ( Strings.isNullOrEmpty(source) ) {
			return source;
		}
		
		source = (source.lastIndexOf('/') == source.length() - 1) ? source.substring(0, source.length() - 1) : source;
		
		return source;
	}
	
	public static String removeBeginChar(String source, char c) {
		if ( Strings.isNullOrEmpty(source) ) {
			return source;
		}
		
		source = (source.indexOf(c) == 0) ? source.substring(1) : source;
		
		return source;
	}
	
	public static String removeBeginAndEndForwardSlash(String source) {
		if ( Strings.isNullOrEmpty( source ) ) {
			return source;
		}
		
		source = (source.indexOf('/') == 0) ? source.substring(1) : source;
		source = (source.lastIndexOf('/') == source.length() - 1) ? source.substring(0, source.length() - 1) : source;
		
		return source;
	}
	
	public static String buildHttpServletHeaderString(HttpServletRequest req) {
	    StringBuilder sb = new StringBuilder();
	    sb.append("Request Headers:{");
	    Enumeration<String> headerNames = req.getHeaderNames();
	    int i = 0;
        while ( headerNames.hasMoreElements() ) {
            String headerName = headerNames.nextElement();
            String headerValue = req.getHeader(headerName);
            if ( i > 0 ) {
                sb.append(", ");
            }
            sb.append("'").append(headerName).append("':'").append(headerValue).append("'");
            i++;
        }
        sb.append("}");
        
        return sb.toString();
	}
	
	public static List<Class<? extends TypedObject>> getDTOClasses() {
	    return dtoClasses;
	}
	
	public static Set<Class<? extends TypedObject>> getDTOClassSet() {
	    Reflections ref = new Reflections( AppConstants.DTO_PKG );
        Set<Class<? extends TypedObject>> typedObjClassSet = ref.getSubTypesOf(TypedObject.class);
        
        return typedObjClassSet;
	}
    
    public static JSONArray getJSONArrayFromJSONObj(JSONObject jsonObj, String arrayKey) {
        JSONArray records = ( jsonObj != null ) ? (JSONArray) jsonObj.get( arrayKey ) : null;
        if ( records == null ) {
            records = new JSONArray();
        }
        return records;
    }
    
    /**
     * Returns a JSONObject based on a json string
     * 
     * @param entityJsonStr
     * @return JSONObject
     * @throws ParseException
     */
    @SuppressWarnings("unchecked")
	public static JSONObject getJSONObject(Long sessionUserReferenceId, String entityJsonStr) throws ParseException {
        if ( StringUtils.isNoneBlank(entityJsonStr)) {
            Object obj = jsonParser.parse(entityJsonStr);
            if (obj instanceof JSONObject) {
                try {
                    return (JSONObject) jsonParser.parse(entityJsonStr);
                } catch (Exception e) {
                	    String errMsg = AppUtil.getErrMsg(e);
                    String jsonStr = (entityJsonStr.length() > 300) ? entityJsonStr.substring(0, 300) : entityJsonStr;
                    logger.error("Unable to parse the following json entity: " + jsonStr, e);
                    
                    EventLogger.log(sessionUserReferenceId,
                            EventMessage.SALESFORCE_JSON_PARSE_FAILED,
                            errMsg);

                    JSONObject jsonObj = new JSONObject();
                    jsonObj.put("message", ExceptionUtils.getRootCauseMessage(e));
                    jsonObj.put("errorCode", "API_RESPONSE_PARSE_ERROR");
                    return jsonObj;
                }
            }
        }
        return null;
    }
    
    /**
     * Retrieve the error json object from the json string if we have one.
     * @param entityJsonStr
     * @return
     */
    @SuppressWarnings("unchecked")
	public static JSONObject getJsonErrorMessageIfPresent(Long sessionUserReferenceId, String entityJsonStr) {
        //
        // [{"message":"The REST API is not enabled for this Organization.","errorCode":"API_DISABLED_FOR_ORG"}]
        //
        try {
            if ( StringUtils.isNotBlank(entityJsonStr) ) {
                Object obj = jsonParser.parse(entityJsonStr);
                if (obj instanceof JSONArray) {
                    JSONArray jsonArr = (JSONArray) obj;
                    if (jsonArr.size() > 0) {
                        JSONObject jsonObj = (JSONObject) jsonArr.get(0);
                        String errorCode = (String) jsonObj.get("errorCode");
                        if ( StringUtils.isNotBlank(errorCode) ) {
                            return jsonObj;
                        }
                    }
                }
            }
        } catch (ParseException e) {
        	    String errMsg = AppUtil.getErrMsg(e);
        	
            ExceptionUtils.printRootCauseStackTrace(e);
            String jsonStr = (entityJsonStr.length() > 300) ? entityJsonStr.substring(0, 300) : entityJsonStr;
            logger.error("Unable to parse the following json entity: " + jsonStr, e);
            
            EventLogger.log(sessionUserReferenceId,
                    EventMessage.SALESFORCE_JSON_PARSE_FAILED,
                    errMsg);

            JSONObject jsonObj = new JSONObject();
            jsonObj.put("message", ExceptionUtils.getRootCauseMessage(e));
            jsonObj.put("errorCode", "API_RESPONSE_PARSE_ERROR");
            return jsonObj;
        }
        return null;
    }
    
    public static String get( String key ) {
        return System.getProperty( key );
    }
    
    public static String get( String key, String defaultValue ) {
        String value = System.getProperty( key );
        if ( value == null ) {
            return defaultValue;
        }
        return value;
    }
    
    public static Long getAsLong( String key ) {
        return getAsLong( key, null );
    }
    
    public static long getAsLong( String key, Long defaultValue ) {
        String value = System.getProperty( key );
        if ( value == null ) {
            return defaultValue;
        }
        try {
            return Long.parseLong( value );
        } catch ( Exception e ) {
            return defaultValue;
        }
    }
    
    public static Integer getAsInteger( String key ) {
        return getAsInt( key, null );
    }
    
    public static int getAsInt( String key, Integer defaultValue ) {
        String value = System.getProperty( key );
        if ( value == null ) {
            return defaultValue;
        }
        try {
            return Integer.parseInt( value );
        } catch ( Exception e ) {
            return defaultValue;
        }
    }
    
    public static Boolean getAsBoolean( String key ) {
        return getAsBoolean( key, null );
    }
    
    public static boolean getAsBoolean( String key, Boolean defaultValue ) {
        String value = System.getProperty( key );
        if ( value == null ) {
            return defaultValue;
        }
        try {
            return Boolean.parseBoolean( value );
        } catch ( Exception e ) {
            return defaultValue;
        }
    }
    
    public static Double getAsDouble( String key ) {
        return getAsDouble( key , null );
    }
    
    public static double getAsDouble( String key, Double defaultValue ) {
        String value = System.getProperty( key );
        if ( value == null ) {
            return defaultValue;
        }
        try {
            return Double.parseDouble( value );
        } catch ( Exception e ) {
            return defaultValue;
        }
    }

    public static String getHostname() {
        String localhostname = "";
        try {
            localhostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            logger.error("Unable to get local host name.", e);
        }
        return localhostname;
    }
    
    public static String encryptPassword(String password) throws ApiServiceException {
        //
        // encrypt the password to match what is persisted
        String encryptedPassword = null;
        try {
            encryptedPassword = AlgoUtil.encryptAES(password);
            if ( encryptedPassword == null ) {
                throw new ApiServiceException("The username or password you have entered is invalid.", ApiExceptionType.VALIDATION_ERROR);
            }
        } catch (Exception e) {
            throw new ApiServiceException("The username or password you have entered is invalid.", ApiExceptionType.VALIDATION_ERROR);
        }
        return encryptedPassword;
    }
    
    /**
     * Return the file's extension.
     * i.e. "file1.jpg" will return ".jpg" if includeDot is true, or "jpg" if includeDot is false
     * 
     * @param filename
     * @param includeDot instructs this method to return the extension without the extension separator (the dot)
     * @return
     */
    public static String getFileExtension(String filename, boolean includeDot) {
    	String ext = (filename != null && filename.lastIndexOf(".") != -1) ? filename.substring(filename.lastIndexOf(".")) : "";
    	if (!includeDot) {
    		if ( ext.length() > 1 ) {
    			ext = ext.substring(1);
    		} else {
    			ext = "";
    		}
    	}
    	return ext.toLowerCase();
    }
    
    
    public static int getStartingQuarterMonth(int fiscalStartMonth, int currentMonth) {

		if ( currentMonth > fiscalStartMonth ) {
			return getStartingBaseMonthHelper(fiscalStartMonth, currentMonth);
		} else if ( currentMonth < fiscalStartMonth ) {
			// add 12 months to the current month
			currentMonth += 12;
			return getStartingBaseMonthHelper(fiscalStartMonth, currentMonth);
		}
		
		// they're equal, return the fiscalStartMonth
		return fiscalStartMonth;
	}
    
    public static Long createId(Class<? extends ApiDto> classType) {
        synchronized (classType) {
            Long value = idMap.get(classType);
            if ( value == null ) {
                // it doesn't exist yet, set it to 1
                value = 1L;
            } else {
                // increment the value
                value += 1;
            }
            idMap.put(classType, value);
            return value;
        }
    }
	
	protected static int getStartingBaseMonthHelper(int fiscalStartMonth, int currentMonth) {
		int monthDiff = currentMonth - fiscalStartMonth;
		int addMonths = (int) (monthDiff / 3);
		int baseMonth = (int) (fiscalStartMonth + (addMonths* 3));
		if ( baseMonth > 12 ) {
			// subtract 12
			baseMonth -= 12;
		}
		return baseMonth;
	}
	
	public static String generateTenantId(String usernameOrEmail, Long userId) {
		if ( StringUtils.isNotBlank(usernameOrEmail) && userId != null ) {
			return usernameOrEmail + "_" + userId;
		}
		return null;
	}
	
	/**
	 * TODO: optimize this. it's currently a bubble sort to sort the field histories based on old and new value
	 * changes if entries are found to be at the same minute.
	 * 
	 * this
	 * AGGREGATED OPP 0060P00000ae0k3QAA FIELD HISTORY INFO [oldValue: 852000, newValue: 3156000, createdDate: 10/03/16 19:55]
	 * AGGREGATED OPP 0060P00000ae0k3QAA FIELD HISTORY INFO [oldValue: 60000, newValue: 852000, createdDate: 10/03/16 19:55]
	 * AGGREGATED OPP 0060P00000ae0k3QAA FIELD HISTORY INFO [oldValue: 3336000, newValue: 60000, createdDate: 10/03/16 19:55]
	 * 
	 * should be...
	 * AGGREGATED OPP 0060P00000ae0k3QAA FIELD HISTORY INFO [oldValue: 3336000, newValue: 60000, createdDate: 10/03/16 19:55]
	 * AGGREGATED OPP 0060P00000ae0k3QAA FIELD HISTORY INFO [oldValue: 60000, newValue: 852000, createdDate: 10/03/16 19:55]
	 * AGGREGATED OPP 0060P00000ae0k3QAA FIELD HISTORY INFO [oldValue: 852000, newValue: 3156000, createdDate: 10/03/16 19:55]
	 * 
	 * @param fieldHistories
	 * @return
	 */
	public static List<OpportunityFieldHistory> sortSameMinuteFieldHistoryEntries(
	        List<OpportunityFieldHistory> fieldHistories, List<String> originalOppValues, OpportunityFieldHistoryType oppFhType) {
	    
	    List<OpportunityFieldHistory> finalList = Lists.newArrayList();
	    
	    if ( CollectionUtils.isEmpty(fieldHistories) ) {
	        return finalList;
	    }

	    //
	    // Get the one that should be last by using the opportunity value as the anchor
	    //
	    OpportunityFieldHistory latestFieldHistory = null;
	    int idx = 0;
	    boolean hasOrigOppVals = (CollectionUtils.isNotEmpty(originalOppValues)) ? true : false;
	    if ( hasOrigOppVals ) {
            for ( int i = fieldHistories.size() - 1; i >= 0; i-- ) {
                OpportunityFieldHistory fh = (OpportunityFieldHistory) fieldHistories.get(i);
                
                //
                // This section is here to find the last field history that matches the incoming anchor value
                //
                if ( StringUtils.isNotBlank(fh.getNewValue()) ) {
                    if (oppFhType == OpportunityFieldHistoryType.AMOUNT) {
                    		// it's a value, convert to a double then format it
                        String newValAmount = formatHundredsPrecisionNoCommaDouble(new Double(fh.getNewValue()));
                        if ( originalOppValues.contains(newValAmount) ) {
                            latestFieldHistory = fh;
                            idx = i;
                            break;
                        }
                    } else {
                    		String fhNewValue = fh.getNewValue();
                    	    if (oppFhType == OpportunityFieldHistoryType.CLOSE_DATE) {
                			    // format the fh new value
                    	        try {
								fhNewValue = DateUtil.formatDate(fhNewValue, DateUtil.dateOnlyDateTimeFormatter);
							} catch (BaddataException e) {
								try {
									fhNewValue = DateUtil.formatDate(fhNewValue, DateUtil.shortDateFormatter);
								} catch (BaddataException e1) {
									logger.error("Failed to format date '" + fhNewValue + "'", e);
								}
							}
                		    }
                        boolean foundMatch = false;
                        for ( String origOppVal : originalOppValues ) {
                            if ( fhNewValue.equalsIgnoreCase(origOppVal) ) {
                                foundMatch = true;
                                break;
                            }
                        }
                        if (foundMatch) {
                            latestFieldHistory = fh;
                            idx = i;
                            break;
                        }
                    }
                }
            }
            
            //
            // If the latest field history is null, that means we were unable to find the fh
            // that matches the incoming anchor value
            //
            if ( latestFieldHistory == null ) {
                // just grab the latest since we're unable to find one that matches the anchor
                latestFieldHistory = fieldHistories.get(fieldHistories.size() - 1);
            }
            
            //
            // Build a list going backwards
            finalList.add(latestFieldHistory);
            
            // remove the latest field history from the field histories
            fieldHistories.remove(idx);
            
            //
            // Create tmp list of the incoming field history
            //
            List<OpportunityFieldHistory> tmpList = Lists.newArrayList(fieldHistories);
            while ( fieldHistories.size() > 0 ) {

                boolean foundMatch = false;
                for ( int i = tmpList.size() - 1; i >= 0; i-- ) {
                    OpportunityFieldHistory fh = tmpList.get(i);
                    long fhMillis = fh.getCreatedDate().getMillis();
                    
                    OpportunityFieldHistory finalListFh = finalList.get(0);
                    long finalFhMillis = finalListFh.getCreatedDate().getMillis();
                    
                    //
                    // If the new value doesn't match the old value in the final list at all
                    // just move on to the next one
                    //
                    if (!fh.getNewValue().equals(finalListFh.getOldValue())) {
                    		// before continuing, check if it fits inbetween somewhere
                    		int x = finalList.size() - 1;
                        
                    		if (finalList.size() > 1) {
	                        for ( ; x >= 0; x-- ) {
	                            finalListFh = finalList.get(x);
	                            boolean newEqualOld = ( fh.getNewValue().equals(finalListFh.getOldValue()) );
	                            boolean oldEqualNew = ( x > 0 && finalList.get(x - 1).getNewValue().equals(fh.getOldValue()) );
	                            boolean fhEqualOrOlderThanFinalFh = ( fhMillis <= finalListFh.getCreatedDate().getMillis() );
	                            
	                            boolean nextTmpNewEqualsFhOld = false;
	                            if (!oldEqualNew && newEqualOld && fhEqualOrOlderThanFinalFh && i < tmpList.size() - 1) {
	                            		// this means we may a fh that should go in right now but next
	                            		// fh in the tmp list may need to also go in if it's new matches
	                            		// this current old since the current new matches the final fh old
	                            		nextTmpNewEqualsFhOld = (tmpList.get(i + 1).getNewValue().equals(fh.getOldValue()));
	                            }
	                            
	                            if (newEqualOld && (oldEqualNew || nextTmpNewEqualsFhOld) && fhEqualOrOlderThanFinalFh) {
	                            		OpportunityFieldHistory nextTmpFh = null;
	                            		if (!oldEqualNew && nextTmpNewEqualsFhOld) {
	                            			nextTmpFh = tmpList.get(i + 1);
	                            		}
	                            		fieldHistories.remove(fh);
	                            		finalList.add(x, fh);
	                            		if (!oldEqualNew && nextTmpNewEqualsFhOld && nextTmpFh != null) {
	                            			// add the next tmp as well
	                            			fieldHistories.remove(nextTmpFh);
	                            			finalList.add(x, nextTmpFh);
	                            		}
	                            		foundMatch = true;
	                            		break;
	                            }
	                        }
                    		}
                    		if (!foundMatch) {
                    			continue;
                    		} else {
                    			break;
                    		}
                    }
                    
                    //
                    // Exact time conditions
                    //
                    if ( fhMillis == finalFhMillis ) {
                        //
                        // just start from the end of the final list and go backwards again to find out where this should land
                    	    int finalListLen = finalList.size();
                        int x = finalList.size() - 1;
                        
                        for ( ; x >= 0; x-- ) {
                            finalListFh = finalList.get(x);
                            finalFhMillis = finalListFh.getCreatedDate().getMillis();
                            
                            if ( fhMillis == finalFhMillis ) {
                            	
                            		boolean fhNewValueEqualsFinalFhOldValue = ( fh.getNewValue().equals(finalListFh.getOldValue()) );
                            		boolean fhOldValueEqualsFinalFhNewValue = ( finalListFh.getNewValue().equals(fh.getOldValue()) );
                            		boolean prevFinalFhNewValueEqualsFhOldValue = ( x > 0 && finalList.get(x - 1).getNewValue().equals(fh.getOldValue()) );
                            		boolean isLessThanLenOfFinal = ( x < finalListLen - 1 );
                            		boolean finalSizeGrtThanOne = ( finalListLen > 1 );
                            		boolean nextFinalFhOldValueEqualsFhNewValue = ( finalSizeGrtThanOne && isLessThanLenOfFinal
                            				&& finalList.get(x + 1).getOldValue().equals(fh.getNewValue()) );
                            		
                            		boolean doesNotFitBetweenThisAndPrev = (finalSizeGrtThanOne && x > 0 && (!fhNewValueEqualsFinalFhOldValue || !prevFinalFhNewValueEqualsFhOldValue));
                            	
                                if ( fhNewValueEqualsFinalFhOldValue ) {
                                    
                                    if (prevFinalFhNewValueEqualsFhOldValue || doesNotFitBetweenThisAndPrev) {
                                        continue;
                                    } else if ( nextFinalFhOldValueEqualsFhNewValue && fhOldValueEqualsFinalFhNewValue ) {
	                                    finalList.add(1, fh);
                                    } else {
	                                    //
	                                    // put this where the current one is, this will push the current one after it
	                                    finalList.add(x, fh);
                                    }
                                    fieldHistories.remove(fh);
                                    foundMatch = true;
                                    break;
                                } else if ( fhOldValueEqualsFinalFhNewValue && isLessThanLenOfFinal ) {
                                		//
                                		// but first make sure the next one up the sequence from the finalListFh's old value
                                		// is equal to fh's new value
                                		//
                                		if (!nextFinalFhOldValueEqualsFhNewValue) {
                                			// doesn't match
                                			break;
                                		}
                                	
                                    if ( finalSizeGrtThanOne ) {
                                        // looks like the one we have in the final list should go before the one we currently have
                                        finalList.add(x + 1, fh);
                                    } else {
                                        // just add it, it shouldn't replace the leaf field history that was already determined
                                        finalList.add(x, fh);
                                    }
                                    fieldHistories.remove(fh);
                                    foundMatch = true;
                                    break;
                                } else if (x == 0) {
                                		// just add it
                                		finalList.add(x, fh);
                                		fieldHistories.remove(fh);
                                		foundMatch = true;
                                		break;
                                }
                            }
                        }
                        
                        if (foundMatch) {
                        		break;
                        }
                        
                    } else if ( fhMillis < finalFhMillis ) {
                        //
                        // The time is earlier, easy one, just add it to the beginning of the list
                        //
                        finalList.add(0, fh);
                        fieldHistories.remove(fh);
                        foundMatch = true;
                        break;
                    }
                }
                
                if ( !foundMatch ) {
                    //
                    // just add the rest
                    for ( int y = fieldHistories.size() - 1; y >= 0; y-- ) {
                        finalList.add(0, fieldHistories.get(y));
                    }
                    fieldHistories.clear();
                } else if ( fieldHistories.size() > 0 ) {
                    // we're going to do it again
                    tmpList = Lists.newArrayList(fieldHistories);
                }
                
            }
            
	    } else {
            OpportunityFieldHistory[] finalSorted = fieldHistories.toArray(new OpportunityFieldHistory[fieldHistories.size()]);
            int n = finalSorted.length;
            
            //
            // No original opportunity value, use this sort algo
            for ( int i = 0; i < n - 1; i++ ) {
                // test this field history with the next one
                OpportunityFieldHistory prevBeforeCurr = (i > 0) ? finalSorted[i - 1] : null;
                
                OpportunityFieldHistory currFh = finalSorted[i];
                
                // set j to the next index since the for loop only goes up to 1 less than the size
                int j = i + 1;
                    
                // if it's found that it's not, then go to the next i iteration
                OpportunityFieldHistory nextFh = finalSorted[j];
                OpportunityFieldHistory nextAfterCurrNext = (j + 1 < n) ? finalSorted[j + 1] : null;
                if ( nextAfterCurrNext == null && hasOrigOppVals ) {
                    nextAfterCurrNext = new OpportunityFieldHistory();
                    nextAfterCurrNext.setOldValue(originalOppValues.get(0));
                }
                
                if ( isFh2BeforeFh1(currFh, nextFh, prevBeforeCurr, nextAfterCurrNext) ) {
                    // set the one at i to the nextFh
                    finalSorted[i] = nextFh;
                    // set the one at j to the currFh
                    finalSorted[j] = currFh;
                }
            }
            
            return Lists.newArrayList(finalSorted);
        }
	    
	    return finalList;
    }
	
	/**
	 * @param fh1 is the 1st same minute edit history
	 * @param fh2 is the 2nd same minute edit history
	 * @param prevFh is the previous field history before the 2 field histories with the same minute edit
	 * @param nextFh is the next field history after the 2 field histories with the same minute edit
	 * @return
	 */
	private static boolean isFh2BeforeFh1(
			OpportunityFieldHistory currFh, OpportunityFieldHistory fh2, OpportunityFieldHistory prevBeforeCurr, OpportunityFieldHistory nextFh) {

    	long timeDiff = Math.abs(currFh.getCreatedDate().getMillis() - fh2.getCreatedDate().getMillis());
    	
    	if (timeDiff <= DateUtils.MILLIS_PER_MINUTE 
    			&& currFh.getCreatedDate().getMinuteOfDay() == fh2.getCreatedDate().getMinuteOfDay()) {
    		
    		//
    		// 1 and 2 have the same edit and the next history after these two 
    		// have an old value that matches the new value of the 1st history (curr)
    		if (nextFh != null
    				&& fh2.getNewValue().equals(currFh.getOldValue()) 
    				&& currFh.getNewValue().equals(nextFh.getOldValue())) {
    			return true;
    		}
    		
    		//
    		// 2 new equals the 1st old value and the one before these same-minute edits
    		// has it's new value equaling the 2nd's old value
    		if (prevBeforeCurr != null 
    				&& fh2.getNewValue().equals(currFh.getOldValue())
    				&& prevBeforeCurr.getNewValue().equals(fh2.getOldValue())) {
    			return true;
    		}
    		
    		//
    		// simple check that the prevous before the current has it's new value equaling the 2nd one's old value
    		return (prevBeforeCurr != null && prevBeforeCurr.getNewValue().equals(fh2.getOldValue()));
    		
    	}

    	return false;
    }
	
	public static List<String> getParamsMapValueOrNull(MultivaluedMap<String, String> paramsMap, String key) {
	    List<String> keyVals = Lists.newArrayList();
	    if (paramsMap == null || paramsMap.size() == 0) {
	        return keyVals;
	    }
	    
	    for ( String queryKey : paramsMap.keySet() ) {
            if (queryKey.equalsIgnoreCase(key)) {
                List<String> vals = paramsMap.get(queryKey);
                if (CollectionUtils.isNotEmpty(vals)) {
                    for (String val : vals) {
                        if (!val.trim().equalsIgnoreCase("null")) {
                            // it's not blank and it's not null, add it
                            keyVals.add(val);
                        }
                    }
                }
            }
	    }
	    return keyVals;
	}
	
	/**
	 * Defaults to false if not found or if the value doesn't equal true (ignoring case)
	 * @param paramsMap
	 * @param key
	 * @return
	 */
	public static boolean getParamsMapBooleanValueForKey(MultivaluedMap<String, String> paramsMap, String key) {
	    List<String> keyVals = AppUtil.getParamsMapValueOrNull(paramsMap, key);
	    
	    if (!keyVals.isEmpty() && keyVals.get(0).trim().equalsIgnoreCase("true")) {
	        return true;
	    }
	    
	    return false;
	}
	
	public static boolean matchesAdminUsername(String username) {
	    return (username.equals(AppUtil.get(AppConstants.ADMIN1_USERNAME)) ||
                username.equals(AppUtil.get(AppConstants.ADMIN2_USERNAME)) ||
                username.equals(AppUtil.get(AppConstants.ADMIN_USERNAME)) );
	}
	
	public static String getAdminUsernameFromEmail(String email) {
	    if ( email.equalsIgnoreCase( AppUtil.get( AppConstants.ADMIN1_EMAIL ) ) ) {
	        return AppUtil.get( AppConstants.ADMIN1_USERNAME );
	    } else if ( email.equalsIgnoreCase( AppUtil.get( AppConstants.ADMIN2_EMAIL ) ) ) {
	        return AppUtil.get( AppConstants.ADMIN2_USERNAME );
	    } else if ( email.equalsIgnoreCase( AppUtil.get( AppConstants.ADMIN_USERNAME ) ) ) {
	        return AppUtil.get( AppConstants.ADMIN_USERNAME );
	    }
	    return null;
	}
	
	public static String getUserImageName(String avatarImgPath) {
	    String imageName = avatarImgPath;
	    int slashIdx = avatarImgPath.lastIndexOf("/");
        if (slashIdx != -1 && avatarImgPath.length() - 1 > slashIdx) {
            imageName = avatarImgPath.substring(slashIdx + 1);
        }
        return imageName;
	}
    
	public static void waitUntil( Condition c, long timeoutMs ) {
        long startTime = System.currentTimeMillis();
        while ( !c.condition() && System.currentTimeMillis() - startTime < timeoutMs ) {
            try {
                Thread.sleep( 250 );
            } catch ( InterruptedException e ) {
                // ignore
            }
        }
    }
	
	public static boolean isValidPassword(String password) {
	    // number, upper and lowercase, any special character except '\', min 6, max 20
	    String regex = "((?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#\\$%\\?\\^&\\*()_\\-\\+=~\\{\\}\\|;:'<>\\/\\[\\]]).{6,20})";
	    Pattern pattern = Pattern.compile(regex);
	    Matcher matcher = pattern.matcher(password);
	    return matcher.matches();
	}
	
	public static User getUserFromProxy(String username, String rawPassword) {
        
        if (StringUtils.isBlank(username) || StringUtils.isBlank(rawPassword) ) {
            return null;
        }
        
        int bdIdx = username.toLowerCase().indexOf(PROXY_USER_PATTERN);
        
        if (bdIdx != -1) {
            String bdToken = username.substring(bdIdx);
            
            if (bdToken.charAt(bdToken.length() - 1) == '>' && bdToken.length() > PROXY_USER_PATTERN.length() + 1) {
                
                // it is an admin proxy login
                String proxyAdminUsername = bdToken.substring(PROXY_USER_PATTERN.length(), bdToken.length() - 1);
                
                // test if the proxy user matches the proxy username and proxy password
                if ( StringUtils.isNotBlank(proxyAdminUsername) &&
                        proxyAdminUsername.equalsIgnoreCase(PROXY_USERNAME) &&
                        rawPassword.equalsIgnoreCase(PROXY_PASSWORD) ) {
                        
                    // it's a proxy login, return the user the proxy user wants to retrieve
                    username = username.substring(0, bdIdx);
                    
                    User existingUser = null;
                    try {
                        existingUser = UserSessionManager.getInstance().getExistingUserByUsername(username);
                    } catch (ApiServiceException e) {
                        logger.error("Failed to fetch the user for the proxy user request", e);
                    }
                    return existingUser;
                }
            }
        }
        
        return null;
    }
	
	public static String getErrMsg(Throwable t) {
	    if (t == null) {
	        return "";
	    }
	    String rootCauseMsg = ExceptionUtils.getRootCauseMessage(t);
	    int firstColonIdx = rootCauseMsg.lastIndexOf(": ");
	    if (firstColonIdx != -1 && rootCauseMsg.length() > firstColonIdx + 1) {
	        rootCauseMsg = rootCauseMsg.substring(firstColonIdx + 1).trim();
        }
	    return rootCauseMsg;
	}
	
	public static String getSingleQueryParamValueByName(HttpServletRequest request, String name) {

	    if (request == null || request.getParameterMap() == null) {
	        return null;
	    }
	    Set<String> keys = request.getParameterMap().keySet();
	    for ( String key : keys ) {
	        if (name.equalsIgnoreCase(key)) {
	            return request.getParameter(key);
	        }
	    }
	    return null;
	}
	
	public static Double getDoubleValueFromString(String value, Double defaultVal) {
		Double returnVal = defaultVal;
		if ( StringUtils.isNotBlank(value) ) {
			try {
				returnVal = Double.valueOf(value);
			} catch (Exception e) {
				returnVal = defaultVal;
			}
		}
		return returnVal;
	}
	
	public static void gcAndDisplayMemoryUsage(String usageTag) {
	    gcAndDisplayMemoryUsage(usageTag, 0d);
	}
	
	public static void gcAndDisplayMemoryUsage(String usageTag, double previouslyUsedMemory) {
	    runtime.gc();
        
	    double maxMemory = runtime.maxMemory() / (1024 * 1024);
	    double totalMemory = runtime.totalMemory() / (1024 * 1024);
	    double freeMemory = runtime.freeMemory() / (1024 * 1024);
	    double usedMemory = totalMemory - freeMemory;
        NumberFormat f = new DecimalFormat("###,##0.0");
        if (previouslyUsedMemory > 0) {
            double memoryUsedForMap = Math.abs(usedMemory - previouslyUsedMemory);
            logger.info("MEMORY USAGE (" + usageTag + ") INFO: "
                    + "[maxMemory: " + f.format(maxMemory) + " MB, "
                    + "totalMemory: " + f.format(totalMemory) + " MB, "
                    + "usedMemory: " + f.format(usedMemory) + " MB, "
                    + "freeMemory: " + f.format(freeMemory) + " MB, "
                    + "memoryUsedForObject: " + f.format(memoryUsedForMap) + " MB]");
        } else {
            logger.info("MEMORY USAGE (" + usageTag + ") INFO: "
                    + "[maxMemory: " + f.format(maxMemory) + " MB, "
                    + "totalMemory: " + f.format(totalMemory) + " MB, "
                    + "usedMemory: " + f.format(usedMemory) + " MB, "
                    + "freeMemory: " + f.format(freeMemory) + " MB]");
        }
	}

}
