/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.http.HttpStatus;

import com.baddata.annotation.ApiInfo;
import com.baddata.api.dto.user.User;
import com.baddata.api.factory.ResourceBase;
import com.baddata.exception.ApiServiceException;
import com.baddata.log.EventLogger.ApiErrorCode;

@Path("session")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SessionResource extends ResourceBase {

	
	@POST
	@ApiInfo(isPublicApi=true)
	@Path("/login")
	public User login(User creds) {
		try {
		    buildRequestSearchSpec();
			return getSessionBroker().login(creds, request, searchSpec);
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.LOGIN_FAILED_ERROR.getCode() );
		}
	}
	
	/**
	 * Is not a public API, leave out (isPublic=true), by default it's not public
	 */
	@POST
	@Path("/logout")
	public void logout() {
		try {
		    getSessionBroker().logout(request);
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.LOGOUT_FAILED_ERROR.getCode() );
		}
	}
	
	/**
     * Leave out @ApiInfo(isPublic=true) to allow the RestApiFilter to do the heavy lifting in
     * determining if the request has a valid session to access this API or not.
     * 
     * @return String of "1"
     */
    @GET
    @Path("/validate")
    public String checkSession() {
        return String.valueOf(HttpStatus.SC_OK);
    }
    
    @GET
    @Path("/csrfToken")
    @Produces(MediaType.TEXT_PLAIN)
    public String getCsrfToken() {
        return getSessionBroker().generateCsrfToken();
    }
    
    
}
