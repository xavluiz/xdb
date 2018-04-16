/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.user;

import javax.xml.bind.annotation.XmlRootElement;

import com.baddata.api.dto.ApiDto;

@XmlRootElement
public class AccountDeleteToken extends ApiDto {

	private String token;

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

}
