/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class BaddataError {
	
	/**
     * Error code.
     */
    private String code = "";

    /**
     * Error message.
     */
    private String message = "";
    
    /**
     * Throwable cause details
     */
    private String causeDetails = "";
    
    public BaddataError() {
    	//
    }
    
    public BaddataError(String message, String code, String causeDetails) {
    	this.init(message, code, causeDetails);
    }
    
    public void init(String message, String code, String causeDetails) {
    	this.causeDetails = (causeDetails != null) ? causeDetails : "";
    	this.code = (code != null) ? code : "";
        this.message = (message != null) ? message : "";
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getCauseDetails(){
        return causeDetails;
    }

	@Override
	public String toString() {
		return "BaddataError [message=" + message
		        + ", code=" + code
				+ ", causeDetails=" + causeDetails + "]";
	}

}
