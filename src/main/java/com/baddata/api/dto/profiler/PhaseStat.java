/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.profiler;

import javax.xml.bind.annotation.XmlType;

import com.baddata.api.dto.TypedObject;

@XmlType
public class PhaseStat extends TypedObject {

    private String name;
    private long ellapsed;
    
    public PhaseStat() {
        //
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getEllapsed() {
        return ellapsed;
    }

    public void setEllapsed(long ellapsed) {
        this.ellapsed = ellapsed;
    }

}
