/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.exception;

import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.baddata.log.Logger;

public class ApiServiceException extends BaddataException {

	protected static Logger logger = Logger.getLogger(ApiServiceException.class.getName());

	private static final long serialVersionUID = 1L;

	private ApiExceptionType exceptionType = ApiExceptionType.INTERNAL_SERVER_ERROR;

	public enum ApiExceptionType {
		FORBIDDEN, UNAUTHORIZED, BAD_REQUEST, SERVICE_UNAVAILABLE, NOT_FOUND, NOT_MODIFIED, INTERNAL_SERVER_ERROR, VALIDATION_ERROR;
	}
	
	public ApiServiceException(
            String message,
            ApiExceptionType exceptionType) {
        
        super(message);
        this.exceptionType = exceptionType;
        logMe();
    }

	public ApiServiceException(String message, Throwable cause, ApiExceptionType exceptionType) {
		super(message, cause);
		this.exceptionType = exceptionType;
		logMe();
	}

	public ApiExceptionType getExceptionType() {
		return exceptionType;
	}

	public Status getMatchingStatus() {
		switch (exceptionType) {
			case BAD_REQUEST:
			case VALIDATION_ERROR:
				return Status.BAD_REQUEST;
			case UNAUTHORIZED:
			    return Status.UNAUTHORIZED;
			case FORBIDDEN:
				return Status.FORBIDDEN;
			case NOT_FOUND:
			    return Status.GONE;
			case NOT_MODIFIED:
				return Status.NOT_MODIFIED;
			case SERVICE_UNAVAILABLE:
				return Status.SERVICE_UNAVAILABLE;
			default:
				// internal_server_error and unknown exception types will use
				return Status.INTERNAL_SERVER_ERROR;
		}
	}
	
	protected void logMe() {
        logger.error( "ApiServiceException: " + ExceptionUtils.getMessage(this), ExceptionUtils.getRootCause(this) );
    }

}
