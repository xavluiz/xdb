/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.persistence;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.Before;
import org.junit.Test;

import com.baddata.TestBase;
import com.baddata.api.dto.system.BaddataLogEvent;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.EventLogger.SeverityType;

import junit.framework.Assert;

public class BooleanFlagSearchTests extends TestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        // delete the previous bd log events
        List<BaddataLogEvent> logEvents = (List<BaddataLogEvent>) persistence.getAllForIndex(DbIndexType.BADDATA_LOG_EVENT_TYPE, null /*tenantId*/);
        if ( CollectionUtils.isNotEmpty(logEvents) ) {
            for ( BaddataLogEvent logEvent : logEvents ) {
                persistence.delete(logEvent);
            }
        }
    }
    
    @Test
    public void createAndFetchObjectsByBooleanFlagTest() throws Exception {
        // create one just to test that we found it in the db
        BaddataLogEvent testEvent1 = new BaddataLogEvent(
                SeverityType.SUPPORT_NOTICE, "111-1111", "This is just a test for event 1", "This is the title of the event", new Long(1));
        testEvent1.setNotified(true);
        BaddataLogEvent testEvent2 = new BaddataLogEvent(
                SeverityType.SUPPORT_NOTICE, "111-2222", "This is just a test for event 2", "This is the title of the event", new Long(1));
        BaddataLogEvent testEvent3 = new BaddataLogEvent(
                SeverityType.SUPPORT_NOTICE, "111-3333", "This is just a test for event 3", "This is the title of the event", new Long(1));
        try {
            persistence.create(testEvent1);
            persistence.create(testEvent2);
            persistence.create(testEvent3);
        } catch (IndexPersistException e) {
            logger.error("Failed to create the test log event.", e );
        }
        
        List<BaddataLogEvent> allLogEvents = (List<BaddataLogEvent>)
                persistence.getAllForIndex(DbIndexType.BADDATA_LOG_EVENT_TYPE, null /*tenantId*/);
        
        //
        // look for notifications that haven't been sent
        //
        List<BaddataLogEvent> logEvents = 
                (List<BaddataLogEvent>) persistence.getAllForObjectByFieldAndKeyword(
                        DbIndexType.BADDATA_LOG_EVENT_TYPE, "notified", "false");
        
        Assert.assertEquals(3, allLogEvents.size());
        Assert.assertEquals(2, logEvents.size());
    }
}
