/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.user;

import javax.xml.bind.annotation.XmlRootElement;

import com.baddata.api.dto.TypedObject;

@XmlRootElement
public class AuthenticationToken extends TypedObject {

    private String token;
    private String email;
    private String newpassword;
    private long expirationtime;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNewpassword() {
		return newpassword;
	}

	public void setNewpassword(String newPassword) {
		this.newpassword = newPassword;
	}

	public long getExpirationtime() {
        return expirationtime;
    }

    public void setExpirationtime(long expirationtime) {
        this.expirationtime = expirationtime;
    }

}
