/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.db;

import javax.xml.bind.annotation.XmlRootElement;

import com.baddata.log.Logger;
import com.baddata.util.AppConstants;
import com.google.common.base.Strings;

@XmlRootElement
public class SearchQuery {
    
    private static Logger logger = Logger.getLogger(SearchQuery.class.getName());

    private String field; // specific field (used for an exact search)
    private String pattern;
    private Long id;
    private Long parent;

    private boolean isContentField;
    private boolean hasWildcard;
    private boolean hasQuery;
    private boolean isBoolean;
    
    public SearchQuery() {
        //
    }
    
    public SearchQuery(String field, String pattern) {
        this.field = field;
        this.pattern = pattern;
    }

    public void init() {
        hasQuery = true;
        if ( id == null && parent == null && Strings.isNullOrEmpty(pattern)) {
            hasQuery = false;
            return;
        }

        pattern = (pattern == null) ? "" : pattern.trim();

        // default the "isContentField" to true if the "field"
        // value is null or empty
        if (Strings.isNullOrEmpty(field)) {
            field = AppConstants.CONTENTS_FIELD;
            isContentField = true;
        }

        // if it's a content field, then lowercase the query string
        // since that's the way the content value is indexed
        if (isContentField) {
            pattern = pattern.toLowerCase();
        }

        hasWildcard = (pattern.indexOf("*") != -1);
    }

    public String getField() {
        return field;
    }

    /**
     * Returns the lowercase representation of this string value since
     * the lucene db stores every field in lowercase format.
     * @param field
     */
    public void setField(String field) {
        if ( field == null ) {
            logger.error("Failed to supply the search query field.", null);
            field = "";
        }
        this.field = field.toLowerCase();
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String q) {
        this.pattern = q;
    }

    public boolean isContentField() {
        return isContentField;
    }

    public boolean hasWildcard() {
        return hasWildcard;
    }

    public boolean hasQuery() {
        return hasQuery;
    }

    public boolean isBoolean() {
        return isBoolean;
    }

    public void setBoolean(boolean isBoolean) {
        this.isBoolean = isBoolean;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParent() {
        return parent;
    }

    public void setParent(Long parent) {
        this.parent = parent;
    }
    

}
