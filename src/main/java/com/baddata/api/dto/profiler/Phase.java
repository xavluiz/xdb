/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.profiler;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.baddata.api.dto.TypedObject;

@XmlRootElement
public class Phase extends TypedObject {

    private String name;
    private List<PhaseStat> stats;
    
    public Phase() {
        stats = new ArrayList<PhaseStat>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElementWrapper(name="stats")
    @XmlElement(name="stat")
    public List<PhaseStat> getStats() {
        return stats;
    }

    public void setStats(List<PhaseStat> stats) {
        this.stats = stats;
    }
    
    public void addStat(PhaseStat stat) {
        this.stats.add( stat );
    }

}
