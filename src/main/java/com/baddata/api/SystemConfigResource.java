/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.baddata.annotation.ApiInfo;
import com.baddata.api.dto.system.ConfigInfo;
import com.baddata.api.factory.ResourceBase;
import com.baddata.exception.ApiServiceException;
import com.baddata.log.EventLogger.ApiErrorCode;
import com.baddata.manager.system.SystemManager;

@Path("system")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SystemConfigResource extends ResourceBase {

    @GET
    @Path("/config")
    public ConfigInfo getConfigInfo() {
        return SystemManager.getInstance().getConfigInfo();
    }
    
    @PUT
    @ApiInfo(requiresUberUserSesssion=true)
    @Path("/config")
    public void updateConfigInfo( ConfigInfo configInfo) {
        try {
            SystemManager.getInstance().updateConfigInfo(configInfo, userReferenceId);
        } catch (ApiServiceException e) {
            throw createWebApplicationException( e, ApiErrorCode.USER_INFO_UPDATE_ERROR.getCode() );
        }
    }
    
    @GET
    @ApiInfo(isPublicApi=true)
    @Path("/resetAdmin")
    @Produces(MediaType.TEXT_PLAIN)
    public String resetAdmin() {
        try {
            this.buildRequestSearchSpec();
            SystemManager.getInstance().resetAdmin(searchSpec.getAuthToken());
            return "OK";
        } catch (ApiServiceException e) {
            throw createWebApplicationException( e, ApiErrorCode.USER_INFO_UPDATE_ERROR.getCode() );
        }
    }
}
