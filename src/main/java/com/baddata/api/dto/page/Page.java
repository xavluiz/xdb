/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.page;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.collections4.CollectionUtils;

import com.baddata.api.dto.ApiDto;

@XmlRootElement
public class Page {

	private int limit;
	private int pages;
	private int page;
	private long ellapsed;
	private long totalHits;
	private List<ApiDto> items;
	
	public Page() {
	    items = new ArrayList<ApiDto>();
	}

	public long getTotalHits() {
        return totalHits;
	}

    public void setTotalHits(long totalHits) {
        this.totalHits = totalHits;
    }

    public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public int getPages() {
		return pages;
	}

	public void setPages(int pages) {
		this.pages = pages;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public long getEllapsed() {
		return ellapsed;
	}

	public void setEllapsed(long ellapsed) {
		this.ellapsed = ellapsed;
	}

    public int getItemCount() {
        return (items != null) ? items.size() : 0;
    }
    
    public ApiDto getDataAt( int itemIdx ) {
        if ( CollectionUtils.isNotEmpty( items ) && items.size() > itemIdx ) {
            return items.get( itemIdx );
        }
        return null;
    }

    @XmlElementWrapper(name="items")
    @XmlElement(name="item")
    public List<? extends ApiDto> getItems() {
        return this.items;
    }

    @SuppressWarnings("unchecked")
    public void setItems(List<? extends ApiDto> items) {
        this.items = (List<ApiDto>) items;
    }

    @SuppressWarnings("unchecked")
    public void setTypedItems(List<? extends ApiDto> typedItems) {
        this.items = (List<ApiDto>) typedItems;
    }
}
