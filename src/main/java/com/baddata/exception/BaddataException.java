/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.exception;

public class BaddataException extends Exception {
    
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * 
     * @param message
     */
    public BaddataException(String message) {
        super(message);
    }
    
    /**
     * 
     * @param message
     * @param cause
     */
    public BaddataException(String message, Throwable cause) {
        super(message, cause);
    }

}
