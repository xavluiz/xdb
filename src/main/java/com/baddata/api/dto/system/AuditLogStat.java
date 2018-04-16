/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.system;

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.joda.time.DateTime;

import com.baddata.annotation.ApiDataInfo;
import com.baddata.api.dto.TypedObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class AuditLogStat extends TypedObject {
    
    private long uniqueSessions;
    private long uniqueUsers;
    private float avgDuration;
    private long errorsPerUser;
    private DateTime start;
    private DateTime end;
    private Map<String, AuditLogPropertyStat> apiStatsMap = Maps.newHashMap();

    public long getUniqueSessions() {
        return uniqueSessions;
    }
    public void setUniqueSessions(long uniqueSessions) {
        this.uniqueSessions = uniqueSessions;
    }
    public long getUniqueUsers() {
        return uniqueUsers;
    }
    public void setUniqueUsers(long uniqueUsers) {
        this.uniqueUsers = uniqueUsers;
    }
    public float getAvgDuration() {
        return avgDuration;
    }
    public void setAvgDuration(float avgDuration) {
        this.avgDuration = avgDuration;
    }
    public long getErrorsPerUser() {
        return errorsPerUser;
    }
    public void setErrorsPerUser(long errorsPerUser) {
        this.errorsPerUser = errorsPerUser;
    }
    @XmlJavaTypeAdapter(value=com.baddata.api.config.DateTimeXmlAdapter.class)
    public DateTime getStart() {
        return start;
    }
    public void setStart(DateTime start) {
        this.start = start;
    }
    @XmlJavaTypeAdapter(value=com.baddata.api.config.DateTimeXmlAdapter.class)
    public DateTime getEnd() {
        return end;
    }
    public void setEnd(DateTime end) {
        this.end = end;
    }
    
    @XmlTransient
    @ApiDataInfo(isPersisted=false)
    public Map<String, AuditLogPropertyStat> getApiStatsMap() {
        return apiStatsMap;
    }
    @XmlElement
    @ApiDataInfo(isPersisted=false)
    public List<AuditLogPropertyStat> getApiStats() {
        List<AuditLogPropertyStat> apiStats = Lists.newArrayList();
        if ( apiStatsMap != null && !apiStatsMap.isEmpty() ) {
            apiStats.addAll(apiStatsMap.values());
        }
        return apiStats;
    }

}
