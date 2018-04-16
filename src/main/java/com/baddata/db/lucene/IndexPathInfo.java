/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.db.lucene;

import java.io.File;

import org.apache.commons.lang3.StringUtils;

import com.baddata.db.DbIndex.DbIndexType;
import com.google.common.base.Strings;

public class IndexPathInfo {
	
	private String filePath;
	private String tenantId;
	private DbIndexType indexType;
	
	public IndexPathInfo(String rootFilePath, DbIndexType indexType) {
		this(rootFilePath, null /*tenantId*/, indexType);
	}
	
	public IndexPathInfo(String rootFilePath, String tenantId, DbIndexType indexType) {
		String indexId = (indexType != null) ? indexType.getIndexId() : "";
		
		StringBuilder sb = new StringBuilder();
        
        if ( !Strings.isNullOrEmpty( rootFilePath ) ) {
            sb.append( rootFilePath );
        }
        
        if ( !Strings.isNullOrEmpty(tenantId) ) {
        	this.tenantId = tenantId;
        	// append the tenantId
        	this.addSeparatorAtEndIfNecessary(sb);
        	sb.append(tenantId);
        }
        
        if ( !Strings.isNullOrEmpty(indexId) ) {
        	this.indexType = indexType;
        	this.addSeparatorAtEndIfNecessary(sb);
        	sb.append(indexId);
        }
        
        this.filePath = sb.toString();
	}
	
	public IndexPathInfo(String rootFilePath, String... appendFileParts) {
		StringBuilder sb = new StringBuilder();
        
        if ( !Strings.isNullOrEmpty( rootFilePath ) ) {
            sb.append( rootFilePath );
        }
        
		if ( appendFileParts != null && appendFileParts.length > 0 ) {
			this.addSeparatorAtEndIfNecessary(sb);
            for ( String filePart : appendFileParts ) {
                if ( !Strings.isNullOrEmpty( filePart ) ) {
                    sb.append( filePart );
                    this.addSeparatorAtEndIfNecessary(sb);
                }
            }
        }
		this.filePath = sb.toString();
	}
	
	private void addSeparatorAtEndIfNecessary(StringBuilder sb) {
		String currPath = sb.toString();
		if (currPath.length() > 0 && currPath.charAt(currPath.length() - 1) != File.separatorChar) {
			sb.append(File.separatorChar);
		}
	}

	public String getFilePath() {
		return filePath;
	}
	
	public DbIndexType getDbIndexType() {
		return indexType;
	}

	public String getTenantId() {
		return tenantId;
	}
	
	public String getSyncObject() {
	    return (StringUtils.isNotBlank(tenantId)) ?
	            tenantId + "-" + indexType.name() :
	                indexType.name();
	}

	@Override
	public String toString() {
		return "IndexPathInfo [filePath=" + filePath + ", tenantId=" + tenantId + ", indexType=" + indexType + "]";
	}
	
	

}
