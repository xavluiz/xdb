/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.baddata.api.dto.job.Progress;
import com.baddata.api.factory.ResourceBase;
import com.baddata.exception.ApiServiceException;
import com.baddata.log.EventLogger.ApiErrorCode;

@Path("job")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JobResource extends ResourceBase {

	@GET
    @Path("/progress")
    public Progress getProgress() {
        try {
        	this.buildRequestSearchSpec();
            return getJobBroker().getProgress(searchSpec);
        } catch (ApiServiceException e) {
            throw createWebApplicationException( e, ApiErrorCode.LOAD_SALESFORCE_OPPORTUNITY_FIELDS_FAILED.getCode() );
        }
    }

    @GET
    @Path("/progress/{id}")
    public Progress getProgressById(@PathParam("id") String jobId) {
        try {
        	this.buildRequestSearchSpec();
            return getJobBroker().getProgressById(jobId);
        } catch (ApiServiceException e) {
            throw createWebApplicationException( e, ApiErrorCode.LOAD_SALESFORCE_OPPORTUNITY_FIELDS_FAILED.getCode() );
        }
    }
}
