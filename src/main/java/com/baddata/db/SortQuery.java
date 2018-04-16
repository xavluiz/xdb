/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.db;

import org.apache.lucene.search.SortField.Type;

import com.baddata.util.AppConstants;

public class SortQuery {
    private String field = AppConstants.CREATE_DATE_KEY;
    private boolean ascending = true;
    private Type sortType = Type.LONG;
    
    public void setField(String field) {
        this.field = field;
    }

    public String getField() {
        return field;
    }
    
    public boolean isAscending() {
        return ascending;
    }
    
    public void setIsAscending(boolean asc) {
        this.ascending = asc;
    }
    
    public void setSortType(Type type) {
        this.sortType = type;
    }
    
    public Type getSortType() {
        return sortType;
    }

    @Override
    public String toString() {
        return "SortQuery [field=" + field + ", ascending=" + ascending
                + ", sortType=" + sortType + "]";
    }
    
}
