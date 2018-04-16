/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.broker;

import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.dbindex.IndexInfo;
import com.baddata.api.dto.page.Page;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.exception.ApiServiceException;
import com.baddata.exception.ApiServiceException.ApiExceptionType;
import com.baddata.exception.IndexPersistException;
import com.baddata.manager.db.PersistenceManager;

public class DatabaseBrokerImpl extends BaseBroker {
	
	private static PersistenceManager persistence = PersistenceManager.getInstance();

	public DatabaseBrokerImpl(Long userref, String userName) {
        super(userref, userName);
    }
    
    public Page getDatabaseByIndexId(String indexId, SearchSpec searchSpec) {
	    	DbIndexType indexType = DbIndex.getIndexTypeByIndexIdValue(indexId);
	    	// set fetch all to true so we get the correct page of results
	    	searchSpec.setFetchAll(true);
	    	return persistence.get(indexType, searchSpec);
    }
    
    public List<IndexInfo> getDatabaseIndexIds() {
    		return DbIndex.getIndexIds();
    }
    
    public void updateRow(TypedObject objectToUpdate) throws ApiServiceException {
        try {
            persistence.save(objectToUpdate);
        } catch (IndexPersistException e) {
            throw new ApiServiceException("Failed to update object, reason: " + ExceptionUtils.getRootCauseMessage(e), ApiExceptionType.INTERNAL_SERVER_ERROR);
        }
    }
    
    public void deleteDb(String indexId, String tenantId) throws ApiServiceException {
        // TODO: implement delete db
        DbIndexType indexType = DbIndexType.getTypeById(indexId);
        if ( !indexType.isApiDeletable() ) {
            throw new ApiServiceException( "The database index '" + indexType.name() + "' is not allowed for deletion", ApiExceptionType.BAD_REQUEST );
        }
        try {
            persistence.deleteObjectsByIndexType(indexType, tenantId);
        } catch (Exception e) {
            throw new ApiServiceException("Failed to delete database, reason: " + ExceptionUtils.getRootCauseMessage(e), ApiExceptionType.INTERNAL_SERVER_ERROR);
        }
    }
	
}
