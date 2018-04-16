/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.user;

import javax.xml.bind.annotation.XmlRootElement;

import com.baddata.api.dto.TypedObject;

@XmlRootElement
public class UserSettings extends TypedObject {

    /**
     * Enable SMS notifications
     */
    private boolean allowSms;
    
    /**
     * Enable notifications from other members
     */
    private boolean allowMemberNotifications;
    
    /**
     * Enable notifications about any account changes or updates
     */
    private boolean allowChangeNotifications;
    
    /**
     * Allow other members to see when the user is online or not
     */
    private boolean showOnline;

    public boolean isAllowSms() {
        return allowSms;
    }

    public void setAllowSms(boolean allowSms) {
        this.allowSms = allowSms;
    }

    public boolean isAllowMemberNotifications() {
        return allowMemberNotifications;
    }

    public void setAllowMemberNotifications(boolean allowMemberNotifications) {
        this.allowMemberNotifications = allowMemberNotifications;
    }

    public boolean isAllowChangeNotifications() {
        return allowChangeNotifications;
    }

    public void setAllowChangeNotifications(boolean allowChangeNotifications) {
        this.allowChangeNotifications = allowChangeNotifications;
    }

    public boolean isShowOnline() {
        return showOnline;
    }

    public void setShowOnline(boolean showOnline) {
        this.showOnline = showOnline;
    }

}
