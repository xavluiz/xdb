/**
 * Copyright (c) 2016 by Baddata
 * All rights reserved.
 */
package com.baddata.util;

import com.baddata.api.dto.user.User;
import com.baddata.log.Logger;
import com.google.common.base.Strings;

/**
 * 
 * Validates whether an Auth object is a valid connected FB user or not.
 *
 */
public class FBUserUtil {
    
    private static Logger logger = Logger.getLogger(FBUserUtil.class.getName());
    
    public static class DecodedSignedRequest {
        private String auth_token;
        private String algorithm;
        private long expires;
        private long issued_at;
        private String user_id;
        private String code;
        
        public String getAuth_token() {
            return auth_token;
        }
        public void setAuth_token(String auth_token) {
            this.auth_token = auth_token;
        }
        public String getAlgorithm() {
            return algorithm;
        }
        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }
        public long getExpires() {
            return expires;
        }
        public void setExpires(long expires) {
            this.expires = expires;
        }
        public long getIssued_at() {
            return issued_at;
        }
        public void setIssued_at(long issued_at) {
            this.issued_at = issued_at;
        }
        public String getUser_id() {
            return user_id;
        }
        public void setUser_id(String user_id) {
            this.user_id = user_id;
        }
        public String getCode() {
            return code;
        }
        public void setCode(String code) {
            this.code = code;
        }
        
    }

    /**
     * 1) Splits the signed request (eg. 238fsdfsd.oijdoifjsidf899) into 2 parts delineated by a '.' char
     * 2) Decode the first part - the encoded signature - from base64url
     * 3) Decode the second part - the 'payload' - from base64url and then decode the reultant JSON object
     * 
     * @param auth
     * @return
     */
    public static DecodedSignedRequest isValidFBConnectionAuth( User auth ) {
        if ( auth == null ) {
            return null;
        }
        
        String password = auth.getPassword();
        if ( Strings.isNullOrEmpty( password ) || password.indexOf(".") == -1 ) {
            return null;
        }
        
        // get the secret
        String secret = AppUtil.get( AppConstants.FACEBOOK_SECRET_ENV_VAR );
        
        String signedRequest = auth.getPassword();
        
        // the password will contain the signed_request
        // FB Doc: signedRequest (a signed parameter that contains information about the person using the app)
        
        DecodedSignedRequest result = null;
        try {
            // get the JSON payload
            // e.g {
            //      "algorithm":"HMAC-SHA256",
            //      "code":"AQDxazuyS-vOLDgQGDblSomknswl7--GUgdVpwvjDbZ4EY9uJZyTonrlixdLgfzz-gHo0sQDF7uxn2DPqA9ACt9MSb_OvSuOha-u6VK-nCqof2HS6uGhG7UZRJoXNgLnFBiOM4lOrVC6baASctsOWkcJb7HJMIkGHO0cEf37JUwe687TBoGjld5AkMUq8B7He9byepe5UZ1OT2YSijYy1gnIME9d8QEH6JGOboqAzX7hKBi6P3QR2BSO2av7-6MdiD9QRJK2AAAp6U2X69O5rrYZ5pTRTZmjLROSEiIBLZ2o94Cne8i7I4BsWy-D0rczXODi_VPTEgiU01BqoOKqRL6c",
            //      "issued_at":1437757761,
            //      "user_id":"10206163744026730"
            //     }
            result = AlgoUtil.parse_signed_request(signedRequest, secret);
        } catch (Exception e) {
            logger.error( "Failed authenticating Facebook signed request.", e);
        }
        
        return result;
    }
    
    
}
