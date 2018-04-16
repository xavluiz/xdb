/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.baddata.api.dto.ObjectId;
import com.baddata.api.dto.page.Page;
import com.baddata.api.factory.ResourceBase;
import com.baddata.exception.ApiServiceException;
import com.baddata.log.EventLogger.ApiErrorCode;

@Path("event")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource extends ResourceBase {
	
	@GET
    public Page getEvents() {
		try {
			this.buildRequestSearchSpec();
			return getEventBroker().getEvents( searchSpec );
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.BAD_API_QUERY_PARAM_FORMAT_ERROR.getCode() );
		}
    }
	
	@PUT
	public void acknowledgeAlert( ObjectId objId ) {
	    try {
            this.getEventBroker().acknowledgeAlerts(objId);
        } catch (ApiServiceException e) {
            throw createWebApplicationException( e, ApiErrorCode.ACKNOWLEDGE_ALERT_FAILED.getCode() );
        }
	}
}
