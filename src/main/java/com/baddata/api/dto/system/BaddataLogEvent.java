/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.system;

import javax.xml.bind.annotation.XmlRootElement;

import com.baddata.api.dto.TypedObject;
import com.baddata.log.EventLogger.SeverityType;

@XmlRootElement
public class BaddataLogEvent extends TypedObject {
    
    private SeverityType severity;
    private String eventCode;
    private String formattedMessage;
    private boolean acknowledged;
    private boolean notified;
    private String subject;
    
    public BaddataLogEvent() {
        // we need a default constructor if we have
        // another constructor with args
    }

    public BaddataLogEvent(
            SeverityType severity,
            String eventCode,
            String message,
            String subject,
            Long userRef) {
        
        this.severity = severity;
        this.formattedMessage = message;
        this.subject = subject;
        this.eventCode = eventCode;
        this.setUserRef(userRef);
    }

    public void setSeverity(SeverityType severity) {
        this.severity = severity;
    }
    
    public String getSeverity() {
        return (severity != null) ? severity.name() : SeverityType.EVENT_AUDIT.name();
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getFormattedMessage() {
        return formattedMessage;
    }

    public void setFormattedMessage(String formattedMessage) {
        this.formattedMessage = formattedMessage;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

}
