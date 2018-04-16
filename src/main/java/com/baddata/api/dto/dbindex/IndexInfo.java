/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.dbindex;

import javax.xml.bind.annotation.XmlRootElement;

import com.baddata.api.dto.ApiDto;

@XmlRootElement
public class IndexInfo extends ApiDto {

	private String indexId;
	private boolean requiresTenantId;
	private boolean apiDeletable;
	
	public String getIndexId() {
		return indexId;
	}
	public void setIndexId(String indexId) {
		this.indexId = indexId;
	}
	public boolean isRequiresTenantId() {
		return requiresTenantId;
	}
	public void setRequiresTenantId(boolean requiresTenantId) {
		this.requiresTenantId = requiresTenantId;
	}
	public boolean isApiDeletable() {
        return apiDeletable;
    }
    public void setApiDeletable(boolean apiDeletable) {
        this.apiDeletable = apiDeletable;
    }

}
