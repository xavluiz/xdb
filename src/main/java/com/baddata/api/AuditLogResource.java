/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.baddata.annotation.ApiInfo;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.system.AuditLogStat;
import com.baddata.api.factory.ResourceBase;
import com.baddata.exception.ApiServiceException;
import com.baddata.log.EventLogger.ApiErrorCode;

@Path("audit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuditLogResource extends ResourceBase {

    @GET
    @ApiInfo(requiresUberUserSesssion=true)
    @Path("/api")
    public Page getAuditLogApiEntries() {
        try {
            this.buildRequestSearchSpec();
            return getLogBroker().getAuditLogApiEntries( searchSpec );
        } catch (ApiServiceException e) {
            throw createWebApplicationException( e, ApiErrorCode.BAD_API_QUERY_PARAM_FORMAT_ERROR.getCode() );
        }
    }
    
    @GET
    @ApiInfo(requiresUberUserSesssion=true)
    @Path("/error")
    public Page getAuditLogErrorEntries() {
        try {
            this.buildRequestSearchSpec();
            return getLogBroker().getAuditLogErrorEntries( searchSpec );
        } catch (ApiServiceException e) {
            throw createWebApplicationException( e, ApiErrorCode.BAD_API_QUERY_PARAM_FORMAT_ERROR.getCode() );
        }
    }
    
    @GET
    @ApiInfo(requiresUberUserSesssion=true)
    @Path("/stats")
    public AuditLogStat getAuditLogStat() {
        try {
            this.buildRequestSearchSpec();
            return getLogBroker().getStats( searchSpec );
        } catch (ApiServiceException e) {
            throw createWebApplicationException( e, ApiErrorCode.BAD_API_QUERY_PARAM_FORMAT_ERROR.getCode() );
        }
    }

}
