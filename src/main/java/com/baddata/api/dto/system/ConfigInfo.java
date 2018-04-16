/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.system;

import javax.xml.bind.annotation.XmlRootElement;

import com.baddata.api.dto.TypedObject;

@XmlRootElement
public class ConfigInfo extends TypedObject {

    /**
     * devMode only pertains to admin users.
     * It's set to true in the system manager by default
     */
    private boolean devMode;
    private boolean maintenanceMode;
    private String maintenanceMessage;
    private String version;

    public boolean isDevMode() {
        return devMode;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    public void setMaintenanceMode(boolean maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
    }

    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }

    public void setMaintenanceMessage(String maintenanceMessage) {
        this.maintenanceMessage = maintenanceMessage;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
