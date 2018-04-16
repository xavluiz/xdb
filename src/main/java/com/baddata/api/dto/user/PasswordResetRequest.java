/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.user;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class PasswordResetRequest {

    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    
}
