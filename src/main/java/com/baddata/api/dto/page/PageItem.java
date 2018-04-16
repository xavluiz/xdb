/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.page;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import com.baddata.api.dto.TypedObject;

@XmlType
public class PageItem {

    private String pageType;
    
    private TypedObject pageData;

    public String getPageType() {
        return pageType;
    }

    public void setPageType(String pageType) {
        this.pageType = pageType;
    }

    @XmlElement
    public TypedObject getPageData() {
        return pageData;
    }

    public void setPageData(TypedObject pageData) {
        this.pageData = pageData;
    }
    
    
}
