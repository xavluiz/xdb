/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.notification;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class EventNotification {

    private Long objectReference;
    private String objectType;
    private NotificationType notificationType = NotificationType.UPDATE;

    @XmlEnum(String.class)
    public enum NotificationType {

        CREATE("Create"),
        UPDATE("Update"),
        DELETE("Delete");

        private String displayName;

        private NotificationType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

    }

    public Long getObjectReference() {
        return objectReference;
    }

    public void setObjectReference(Long objectReference) {
        this.objectReference = objectReference;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }


}
