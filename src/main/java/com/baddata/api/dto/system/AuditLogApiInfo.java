/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.system;

import com.baddata.api.dto.TypedObject;

public class AuditLogApiInfo extends TypedObject {

    private String userName = "";
    private String sessionId = "";
    private String method = "";
    private String url = "";
    private int status = 0;
    private int size = 0;
    private long elapsed = 0l;
    private String error = "";
    private String referer = "";

    public String getUserName() {
        return userName;
    }
    public void setUserName(String userName) {
        this.userName = userName;
    }
    public String getSessionId() {
        return sessionId;
    }
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    public String getMethod() {
        return method;
    }
    public void setMethod(String method) {
        this.method = method;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public int getStatus() {
        return status;
    }
    public void setStatus(int status) {
        this.status = status;
    }
    public int getSize() {
        return size;
    }
    public void setSize(int size) {
        this.size = size;
    }
    public long getElapsed() {
        return elapsed;
    }
    public void setElapsed(long elapsed) {
        this.elapsed = elapsed;
    }
    public String getError() {
        return error;
    }
    public void setError(String error) {
        this.error = error;
    }
    public String getReferer() {
        return referer;
    }
    public void setReferer(String referer) {
        this.referer = referer;
    }

}
