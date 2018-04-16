/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class UploadResponse {

    private String status = "success";

	public String getStatus() {
        return status;
    }
    
}
