/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DbIndexInfo extends TypedObject {
    private long docCount;
    private String indexName;
    private List<String> fields;
    private long indexTimeTotal;
    private long indexTimeAvg;
    private long spaceTotal;

    public long getDocCount() {
        return docCount;
    }

    public void setDocCount(long docCount) {
        this.docCount = docCount;
    }

    public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexname) {
		this.indexName = indexname;
	}

	@XmlElementWrapper(name="fields")
    @XmlElement(name="field")
	public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public long getIndexTimeTotal() {
        return indexTimeTotal;
    }

    public void setIndexTimeTotal(long totalindextime) {
        this.indexTimeTotal = totalindextime;
    }

    public long getIndexTimeAvg() {
        return indexTimeAvg;
    }

    public void setIndexTimeAvg(long avgindextime) {
        this.indexTimeAvg = avgindextime;
    }

    public long getSpaceTotal() {
        return spaceTotal;
    }

    public void setSpaceTotal(long totalspace) {
        this.spaceTotal = totalspace;
    }

}
