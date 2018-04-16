/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.exception;

public class SalesforceException extends BaddataException {

    private static final long serialVersionUID = -8542155389481939907L;

    public SalesforceException(String message) {
        super(message);
    }
    
    public SalesforceException(String message, Throwable t) {
        super(message, t);
    }

}
