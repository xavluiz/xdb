/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved
 */
package com.baddata.broker;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.joda.time.DateTime;

import com.baddata.api.dto.ObjectId;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.system.BaddataLogEvent;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.RangeQuery;
import com.baddata.db.SearchQuery;
import com.baddata.exception.ApiServiceException;
import com.google.common.collect.Lists;


public class EventBrokerImpl extends BaseBroker {
	
	public EventBrokerImpl(Long userref, String userName) {
        super(userref, userName);
	}

    public Page getEvents(SearchSpec searchSpec) {
        //
        // Make sure we're searching for events for this session user
        //
        if (searchSpec == null) {
            // create a basic search spec
            searchSpec = new SearchSpec(userReferenceId);
        } else if (searchSpec.getUserRef() == null) {
            searchSpec.setUserRef(userReferenceId);
        }
        
        //
        // get the current queries passed in from the client, if any
        //
        List<SearchQuery> searchQueries = searchSpec.getQueries();        
        if (searchQueries == null) {
            searchQueries = Lists.newArrayList();
        }
        
        //
        // Make sure we add the acknowledged = false
        // and the severity = USER_ALERT into the search queries
        //
        boolean hasAcknowledgedQuery = false;
        boolean hasSeverityQuery = false;
        if ( CollectionUtils.isNotEmpty( searchQueries ) ) {
            // check if USER_NOTICE and acknowledged are part of the query
            for ( SearchQuery sq : searchSpec.getQueries() ) {
                if ( sq.getField().equalsIgnoreCase("acknowledged") ) {
                    hasAcknowledgedQuery = true;
                }
                if ( sq.getField().equalsIgnoreCase("severity") ) {
                    hasSeverityQuery = true;
                }
            }
        }
        if (!hasAcknowledgedQuery) {
            searchQueries.add(new SearchQuery("acknowledged", "false"));
        }
        
        if ( CollectionUtils.isEmpty( searchSpec.getRangeQueries() ) ) {
            
            // as long as it was created less than a month ago
            long aMonthAgoTimestamp = DateTime.now().minusMonths(1).getMillis();
            RangeQuery rq = new RangeQuery("createtime", aMonthAgoTimestamp /*minimum*/, null /*max*/);
            
            searchSpec.setRangeQueries(Arrays.asList(rq));
        }
        
        Page page = persistence.get( DbIndexType.BADDATA_LOG_EVENT_TYPE, searchSpec );
        
        return page;
    }
    
    public void acknowledgeAlerts(ObjectId objId) throws ApiServiceException {
        if (objId == null || (CollectionUtils.isEmpty(objId.getIds()) && objId.getId() <= 0)) {
            throw new ApiServiceException("Unable to acknowledge alerts without one or more alert IDs", null);
        }
        
        //
        // Go through the alerts matching the ones in the objId list and delete
        // them if the userRef matches the logged in user
        //
        
        // TODO: add a way to fetch all of the IDs via the search spec
        List<Long> ids = (CollectionUtils.isNotEmpty(objId.getIds())) ? Lists.newArrayList(objId.getIds()) : Lists.newArrayList(new Long(objId.getId()));

        for (Long id : ids) {
            BaddataLogEvent existingLogEvent = (BaddataLogEvent) persistence.getById(DbIndexType.BADDATA_LOG_EVENT_TYPE, id);
            if (existingLogEvent != null) {
                // they match, delete it
                try {
                    persistence.delete(existingLogEvent);
                } catch (Exception e) {
                    logger.error("Failed to delete/acknowledge user alert '" + id.longValue() + "'.", e);
                }
            }
        }
        
    }

}
