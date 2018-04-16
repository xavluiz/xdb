/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.exception;


public class IndexPersistException extends BaddataException {

	/**
    *
    */
	private static final long serialVersionUID = 1L;

	/**
	 * 
	 * @param message
	 */
	public IndexPersistException(String message) {
        super(message);
    }

	
	/**
	 * 
	 * @param message
	 * @param cause
	 */
	public IndexPersistException(String message, Throwable cause) {
		super(message, cause);
	}


}
