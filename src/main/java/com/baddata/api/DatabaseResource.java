/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.baddata.annotation.ApiInfo;
import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.dbindex.IndexInfo;
import com.baddata.api.dto.page.Page;
import com.baddata.api.factory.ResourceBase;
import com.baddata.exception.ApiServiceException;
import com.baddata.log.EventLogger.ApiErrorCode;

@Path("database")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DatabaseResource extends ResourceBase {

	@GET
	@Path("/{indexId}")
	@ApiInfo(requiresUberUserSesssion=true)
	public Page getDatabaseByIndexId(@PathParam("indexId") String indexId) {
		try {
			this.buildRequestSearchSpec();
			return getDatabaseBroker().getDatabaseByIndexId(indexId, searchSpec);
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.RETRIEVE_DATABASE_BY_TYPE_FAILED.getCode() );
		}
	}
	
	@GET
	@Path("/indexids")
	@ApiInfo(requiresUberUserSesssion=true)
	public List<IndexInfo> getDatabaseIndexIds() {
		return getDatabaseBroker().getDatabaseIndexIds();
	}
	
	@PUT
	@Path("/update")
	@ApiInfo(requiresUberUserSesssion=true)
	public void updateRow(TypedObject objectToUpdate) {
	    try {
            getDatabaseBroker().updateRow(objectToUpdate);
        } catch (ApiServiceException e) {
            throw createWebApplicationException( e, ApiErrorCode.UPDATE_ROW_FAILED.getCode() );
        }
	}
	
	@DELETE
	@Path("/delete/{indexId}")
	@ApiInfo(requiresUberUserSesssion=true)
	public void deleteDb(@PathParam("indexId") String indexId) {
	    try {
	        this.buildRequestSearchSpec();
            getDatabaseBroker().deleteDb(indexId, searchSpec.getTenantId());
        } catch (ApiServiceException e) {
            throw createWebApplicationException( e, ApiErrorCode.DELETE_DB_FAILED.getCode() );
        }
	}
}
