/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

@XmlRootElement
public abstract class TypedObject extends ApiDto {

    // auto-populated
    private Long id;
    private Long sortId;
    // not required
    private Long parent;
    // auto-populated
    private DateTime updateTime;
    // auto-populated
    private DateTime createTime;
    // auto-populated
    private String typeId;
    // not required
    private Long userRef;
    // not required
    private String key = "";
    
    // this should be null if the index does not need to be in a specific subdirectory
    protected String tenantId = null;

    public TypedObject() {
        createTime = new DateTime();
        updateTime = new DateTime();
        typeId = this.getClass().getSimpleName();
    }
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
        this.setSortId(id);
    }
    
    public Long getSortId() {
    		return this.sortId;
    }
    
    public void setSortId(Long sortId) {
    		this.sortId = sortId;
    }

    public Long getParent() {
        return parent;
    }

    public void setParent(Long parent) {
        this.parent = parent;
    }

	@XmlJavaTypeAdapter(value=com.baddata.api.config.DateTimeXmlAdapter.class)
	public DateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(DateTime updateTime) {
		this.updateTime = updateTime;
	}
	
	@XmlJavaTypeAdapter(value=com.baddata.api.config.DateTimeXmlAdapter.class)
    public DateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(DateTime createTime) {
        this.createTime = createTime;
    }
    
    public String getTypeid() {
        return typeId;
    }

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public Long getUserRef() {
		return userRef;
	}

	public void setUserRef(Long userRef) {
		this.userRef = userRef;
	}

    public String getKey() {
        if (StringUtils.isBlank(key)) {
            key = id + "";
        }
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
	
}
