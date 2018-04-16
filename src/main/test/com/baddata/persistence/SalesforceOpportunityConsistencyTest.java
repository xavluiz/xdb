/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.persistence;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.baddata.TestBase;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.salesforce.Opportunity;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.RangeQuery;
import com.baddata.db.SortQuery;
import com.google.common.collect.Lists;

public class SalesforceOpportunityConsistencyTest extends TestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void persistOpportunities() throws Exception {
        Long userId = 8l;
        long startrange = 0l;
        long endrange = 0l;
        for ( int i = 0; i < 10; i++ ) {
            Opportunity opp = new Opportunity();
            opp.setName("oppname_"+i);
            opp.setStageName("stagename_"+i);
            DateTime createdDate = DateTime.now().minusDays((10 - i));
            if (i == 8) {
                endrange = createdDate.getMillis();
            } else if (i == 1) {
                startrange = createdDate.getMillis();
            }
            DateTime closeDate = DateTime.now().minusMinutes((10 - 1));
            opp.setCreatedDate(createdDate);
            opp.setCloseDate(closeDate);
            opp.setUserRef(userId);
            persistence.create(opp);
            Thread.sleep(1000);
        }
        
        //
        // Search in chronological order (from oldest to latest)
        //
        
        SearchSpec searchSpec = new SearchSpec(userId);
        searchSpec.setLimit(10000);
        // use the sort query
        SortQuery sortQuery = new SortQuery();
        sortQuery.setField("createddate");
        searchSpec.setSortQuery(sortQuery);
        
        Page p = persistence.get(DbIndexType.SALESFORCE_OPPORTUNITY_TYPE, searchSpec);
        
        List<Opportunity> persistedOpps = (List<Opportunity>) p.getItems();
        for ( int i = 0; i < persistedOpps.size(); i++ ) {
            Opportunity opportunity = persistedOpps.get(i);
            String stageNameToMatch = "stagename_" + i;
            String oppNameToMatch = "oppname_" + i;
            if ( !stageNameToMatch.equals( opportunity.getStageName() ) ||
                    !oppNameToMatch.equals( opportunity.getName() ) ) {
                Assert.fail("Failed to match the correct order");
            }
        }
        
        sortQuery = new SortQuery();
        sortQuery.setField("createddate");
        sortQuery.setIsAscending(false);
        searchSpec.setSortQuery(sortQuery);
        
        p = persistence.get(DbIndexType.SALESFORCE_OPPORTUNITY_TYPE, searchSpec);
        persistedOpps = (List<Opportunity>) p.getItems();
        for ( int i = persistedOpps.size() - 1, x = 0; i >= 0; i--, x++ ) {
            Opportunity opportunity = persistedOpps.get(x);
            String stageNameToMatch = "stagename_" + i;
            String oppNameToMatch = "oppname_" + i;
            if ( !stageNameToMatch.equals( opportunity.getStageName() ) ||
                    !oppNameToMatch.equals( opportunity.getName() ) ) {
                Assert.fail("Failed to match the correct order");
            }
        }
        
        // range query test
        // create a min and max query
        SearchSpec dbQuerySpec = new SearchSpec(userId);
        RangeQuery rq = new RangeQuery("createddate", startrange, endrange);
        
        // add the range query
        dbQuerySpec.setRangeQueries(Lists.newArrayList(rq));
        
        p = persistence.get(DbIndexType.SALESFORCE_OPPORTUNITY_TYPE, dbQuerySpec);
        persistedOpps = (List<Opportunity>) p.getItems();
        Assert.assertEquals(true, persistedOpps.size() == 8);
    }

}
