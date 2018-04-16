/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.broker;

import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;

public abstract class BaseBroker {

    protected PersistenceManager persistence = PersistenceManager.getInstance();
    protected Logger logger = Logger.getLogger(BaseBroker.class.getName());
    protected Long userReferenceId;
    protected String userName;
    
    public BaseBroker(Long userref, String userName) {
	    	this.userReferenceId = userref;
	    	this.userName = userName;
    }
}
