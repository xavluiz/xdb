/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.persistence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.SortField.Type;
import org.joda.time.DateTime;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.baddata.TestBase;
import com.baddata.api.dto.currency.CurrencyQuoteInfo;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.salesforce.Opportunity;
import com.baddata.api.dto.salesforce.Opportunity.OpportunityFields;
import com.baddata.api.dto.salesforce.OpportunityFieldHistory;
import com.baddata.api.dto.salesforce.OpportunityFieldHistory.OpportunityFieldHistoryFields;
import com.baddata.api.dto.salesforce.summary.coverage.SfCoverageStat;
import com.baddata.api.dto.user.User;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.RangeQuery;
import com.baddata.db.SearchQuery;
import com.baddata.db.SortQuery;
import com.baddata.manager.currency.CurrencyLayerManager;
import com.baddata.manager.salesforce.SalesforceDataLoadManager;
import com.baddata.util.AppConstants;
import com.baddata.util.DateUtil;
import com.baddata.util.FileUtil;
import com.google.common.collect.Lists;

import junit.framework.Assert;

public class DatabaseTests extends TestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }
    
    @Test
    public void createDateSortTest() throws Exception {
        Long userRef = 5l;
        //
        // sort long (date and long values)
        for (int i = 0; i < 5; i++) {
            Opportunity opp = new Opportunity();
            opp.setCreatedDate(DateTime.now());
            opp.setUserRef(userRef);
            persistence.save(opp);
            Thread.sleep(200);
        }
        
        //
        // Sort the opportunities by created date in ascending order
        SearchSpec searchSpec = new SearchSpec(userRef);
        SortQuery sq = new SortQuery();
        sq.setField("createddate");
        searchSpec.setSortQuery(sq);
        
        Page p = persistence.get(DbIndexType.SALESFORCE_OPPORTUNITY_TYPE, searchSpec);
        // expect all items to go from oldest to youngest date
        List<Opportunity> opps = (List<Opportunity>) p.getItems();
        Assert.assertEquals(5, opps.size());
        DateTime currentDate = null;
        for ( Opportunity opp : opps ) {
            DateTime oppCreatedDate = opp.getCreatedDate();
            if (currentDate == null) {
                currentDate = new DateTime(oppCreatedDate);
            }
            if ( currentDate != null && oppCreatedDate.isBefore(currentDate.getMillis()) ) {
                Assert.fail("Not in the correct ascending order");
            }
            currentDate = new DateTime(oppCreatedDate);
        }
        
        //
        // Sort the opportunities by created date in descending order
        searchSpec = new SearchSpec(userRef);
        sq = new SortQuery();
        sq.setField("createddate");
        sq.setIsAscending(false);
        searchSpec.setSortQuery(sq);
        
        p = persistence.get(DbIndexType.SALESFORCE_OPPORTUNITY_TYPE, searchSpec);
        // expect all items to go from oldest to youngest date
        opps = (List<Opportunity>) p.getItems();
        Assert.assertEquals(5, opps.size());
        currentDate = null;
        for ( Opportunity opp : opps ) {
            DateTime oppCreatedDate = opp.getCreatedDate();
            if ( currentDate != null && oppCreatedDate.isAfter(currentDate.getMillis()) ) {
                Assert.fail("Not in the correct descdending order");
            }
            currentDate = new DateTime(oppCreatedDate);
        }
    }
    
    @Test
    public void oppStatSortTest() throws Exception {
        Long userRef = 6l;
        //
        // sort double test
        for (int i = 0; i < 5; i++) {
            Opportunity opp = new Opportunity();
            opp.setAmount(i * 10.1);
            opp.setUserRef(userRef);
            persistence.save(opp);
        }
        
        //
        // Sort the opportunities by amount in descending order
        SearchSpec searchSpec = new SearchSpec(userRef);
        SortQuery sq = new SortQuery();
        sq.setField("amount");
        sq.setSortType(Type.DOUBLE);
        sq.setIsAscending(false);
        searchSpec.setSortQuery(sq);
        
        //
        // Show the amount has been ordered in descending order (i.e. 40.4, 30.3, ...)
        //
        Page p = persistence.get(DbIndexType.SALESFORCE_OPPORTUNITY_TYPE, searchSpec);
        List<Opportunity> opps = (List<Opportunity>) p.getItems();
        Assert.assertEquals(5, opps.size());
        Double prevAmount = null;
        for ( Opportunity opp : opps ) {
            Double oppAmount = opp.getAmount();
            if ( prevAmount != null && oppAmount > prevAmount ) {
                Assert.fail("Not in the correct descending order");
            }
            prevAmount = oppAmount;
        }
    }
    
    @Test
    public void oppStringSortTest() throws Exception {
        Long userRef = 7l;
        //
        // sort long (date and long values)
        for (int i = 0; i < 5; i++) {
            Opportunity opp = new Opportunity();
            opp.setOwnerName(i +"_owner_" + i);
            opp.setUserRef(userRef);
            persistence.save(opp);
        }
        
        //
        // Sort the opportunities by ownername in descending order
        SearchSpec searchSpec = new SearchSpec(userRef);
        SortQuery sq = new SortQuery();
        sq.setField("ownername");
        sq.setSortType(Type.STRING);
        sq.setIsAscending(false);
        searchSpec.setSortQuery(sq);
        
        //
        // Show the owner name has been ordered in descending order (i.e. 4_owner_4, 3_owner_3, ...)
        //
        Page p = persistence.get(DbIndexType.SALESFORCE_OPPORTUNITY_TYPE, searchSpec);
        List<Opportunity> opps = (List<Opportunity>) p.getItems();
        Assert.assertEquals(5, opps.size());
        String prevOwnerName = null;
        for ( Opportunity opp : opps ) {
            String ownerName = opp.getOwnerName();
            if ( prevOwnerName != null && ownerName.compareTo(prevOwnerName) > 0 ) {
                Assert.fail("Not in the correct descending order");
            }
            prevOwnerName = ownerName;
        }
        
        //
        // Sort the opportunities by ownername in ascending order
        searchSpec = new SearchSpec(userRef);
        sq = new SortQuery();
        sq.setField("ownername");
        sq.setSortType(Type.STRING);
        sq.setIsAscending(true);
        searchSpec.setSortQuery(sq);
        
        //
        // Show the owner name has been ordered in ascending order (i.e. 0_owner_0, 1_owner_1, ...)
        //
        p = persistence.get(DbIndexType.SALESFORCE_OPPORTUNITY_TYPE, searchSpec);
        opps = (List<Opportunity>) p.getItems();
        Assert.assertEquals(5, opps.size());
        prevOwnerName = null;
        for ( Opportunity opp : opps ) {
            String ownerName = opp.getOwnerName();
            if ( prevOwnerName != null && ownerName.compareTo(prevOwnerName) < 0 ) {
                Assert.fail("Not in the correct ascending order");
            }
            prevOwnerName = ownerName;
        }
    }
    
    @Test
    public void opportunityFieldHistoryQueryByStageTest() throws Exception {
        SalesforceDataLoadManager sfDataLoadMgr = SalesforceDataLoadManager.getInstance();
        String tenantId = "testuser_1";
        Long userRef = 7l;
        
        //
        // Load the opportunity field history data
        JSONArray records = FileUtil.getJsonArrayFromCsvFile(FileUtil.getSyntheticData("OppFieldHistorySynthetic.csv"));
        
        Assert.assertNotNull("Failed to retrieve the history data to persist", records);
        Assert.assertEquals(true, records.size() > 0);
        
        String stageNameFieldPattern = OpportunityFields.StageName.name().toLowerCase();
        int total = records.size();
        int totalStageNameRecords = 0;
        int totalStageNameRecordsOnOrBeforeDate = 0;
        DateTime maxDate = new DateTime(2016, 3, 15, 0, 0, 0);
        String maxDateStr = maxDate.toString(DateUtil.dateOnlyDateTimeFormatter);
        long maxDateMillis = maxDate.getMillis();
        System.out.println("max date: " + maxDateStr);
        
        Iterator<?> iter = records.iterator();
        List<OpportunityFieldHistory> batch = new ArrayList<OpportunityFieldHistory>();
        
        long now = System.currentTimeMillis();
        while ( iter.hasNext() ) {
            JSONObject iterObj = (JSONObject) iter.next();
            if ( iterObj != null ) {
                try {
                    OpportunityFieldHistory fieldHistory = new OpportunityFieldHistory();
                    
                    // update the tenant id
                    fieldHistory.setTenantId(tenantId);
                    
                    fieldHistory.setOpportunityFieldHistoryId( (String) iterObj.get( OpportunityFieldHistoryFields.Id.name() ) );
                    fieldHistory.setCreatedById( (String) iterObj.get( OpportunityFieldHistoryFields.CreatedById.name() ) );
                    String fieldVal = (String) iterObj.get( OpportunityFieldHistoryFields.Field.name() );
                    String normalizedFieldNameVal = sfDataLoadMgr.getNormalizedFieldName(fieldVal);
                    fieldHistory.setField( normalizedFieldNameVal );
                    if (normalizedFieldNameVal.equals(stageNameFieldPattern)) {
                        totalStageNameRecords++;
                    }
                    
                    fieldHistory.setOldValue( sfDataLoadMgr.getStringFromObject( iterObj.get( OpportunityFieldHistoryFields.OldValue.name() ) ) );
                    fieldHistory.setNewValue( sfDataLoadMgr.getStringFromObject( iterObj.get( OpportunityFieldHistoryFields.NewValue.name() ) ) );
                    fieldHistory.setOpportunityId( (String) iterObj.get( OpportunityFieldHistoryFields.OpportunityId.name() ) );
                    
                    // get the CreatedDate (i.e. 2016-08-05T01:02:09.000+0000) and convert to DateTime
                    String createdDate = (String) iterObj.get( OpportunityFieldHistoryFields.CreatedDate.name() );
                    if ( StringUtils.isNotBlank(createdDate) ) {
                        DateTime dt = DateUtil.buildUtcDateTime(createdDate);
                        fieldHistory.setCreatedDate( dt );
                        
                        if ( !dt.isAfter(maxDateMillis) && normalizedFieldNameVal.equals(stageNameFieldPattern) ) {
                            totalStageNameRecordsOnOrBeforeDate++;
                        }
                    }
                    
                    batch.add(fieldHistory);
                    
                    if ( batch.size() == AppConstants.MAX_INDEX_LIMIT) {
                        // persist
                        persistence.createEntities( batch, DbIndexType.SALESFORCE_OPPORTUNITY_FIELD_HISTORY_TYPE );
                        batch = new ArrayList<OpportunityFieldHistory>();
                    }
                    
                } catch (Exception e) {
                    Assert.fail("Failed to persist the field histories");
                } finally {
                    //
                }

            }
        } // end while loop
        
        if ( !batch.isEmpty() ) {
            // persist
            persistence.createEntities( batch, DbIndexType.SALESFORCE_OPPORTUNITY_FIELD_HISTORY_TYPE );
        }
        
        System.out.println("elapsed index time: " + (System.currentTimeMillis() - now) + " ms");
        
        //
        // fetch the opp field history records with the "stagename" field
        SearchSpec searchSpec = new SearchSpec(true);
        searchSpec.setUserRef(userRef);
        SearchQuery sq = new SearchQuery("field", stageNameFieldPattern);
        searchSpec.addQuery(sq);
        now = System.currentTimeMillis();
        List<OpportunityFieldHistory> oppFieldHistoryList = 
                (List<OpportunityFieldHistory>) persistence.getAllForObjectBySearchSpec(DbIndexType.SALESFORCE_OPPORTUNITY_FIELD_HISTORY_TYPE, searchSpec, tenantId);
        System.out.println("elapsed search time: " + (System.currentTimeMillis() - now) + " ms");
        Assert.assertEquals(true, oppFieldHistoryList.size() == totalStageNameRecords);
        Assert.assertEquals(true, oppFieldHistoryList.size() < total);
        
        //
        // Now fetch with the max date
        RangeQuery rq = new RangeQuery("createddate", maxDateMillis);
        searchSpec.addRangeQuery(rq);
        now = System.currentTimeMillis();
        oppFieldHistoryList = 
                (List<OpportunityFieldHistory>) persistence.getAllForObjectBySearchSpec(DbIndexType.SALESFORCE_OPPORTUNITY_FIELD_HISTORY_TYPE, searchSpec, tenantId);
        System.out.println("elapsed range query search time: " + (System.currentTimeMillis() - now) + " ms");
        Assert.assertEquals(true, oppFieldHistoryList.size() == totalStageNameRecordsOnOrBeforeDate);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void nestedOpportunityFieldHistoryPersistenceTest() throws Exception {
        String tenantId = "test_user_abc";
        for (int i = 0; i < 2; i++) {
            Opportunity opp = new Opportunity();
            opp.setTenantId(tenantId);
            opp.setAmount( (1000.0d * (i + 1)) );
            opp.setName("opp_name_" + i);
            opp.setOpportunityId("opp_id_" + i);
            
            persistence.save(opp);
        }
        
        List<Opportunity> opps = (List<Opportunity>) persistence.getAllForIndex(DbIndexType.SALESFORCE_OPPORTUNITY_TYPE, tenantId);
        Assert.assertEquals(2, opps.size());
    }
    
    @Test
    public void currencyConversionTest() throws Exception {
        CurrencyLayerManager currencyMgr = CurrencyLayerManager.getInstance();
        currencyMgr.start();
        
        //
        // fetch to show we have data
        List<CurrencyQuoteInfo> currencyQuoteInfoList = (List<CurrencyQuoteInfo>) persistence.getAllForIndex(DbIndexType.CURRENCY_QUOTE_INFO_TYPE, null /*tenantId*/);
        
        Assert.assertEquals(true, currencyQuoteInfoList.size() > 0);
        
        // convert CAD amount on for the year 2017 and the 5th month
        String currentCurrencyIsoCode = "CAD";
        CurrencyQuoteInfo currencyQuoteInfo = currencyMgr.getCurrencyQuoteInfo(currentCurrencyIsoCode, 2017, 5);
        // it should equal "USDCAD": 1.367301 = 1.367301
        Assert.assertEquals("1.367301", String.valueOf(currencyQuoteInfo.getExchangeRate()));
    }
    
    @Test
    public void opportuntyCurrencyUpdateTest() throws Exception {
        String tenantId = "abc_123";
        
        for (int i = 0; i < 2; i++) {
            Opportunity opp = new Opportunity();
            opp.setTenantId(tenantId);
            opp.setAmount( (1000.0d * (i + 1)) );
            opp.setName("opp_name_" + i);
            opp.setOpportunityId("opp_id_" + i);
            opp.setCurrencyIsoCode("EUR");
            
            persistence.save(opp);
            
            // create a few of field histories of this opportunity
            for (int x = 0; x < 3; x++) {
                int multiplier = 3 - x;
                OpportunityFieldHistory fieldHistory = new OpportunityFieldHistory();
                fieldHistory.setTenantId(tenantId);
                fieldHistory.setParent(opp.getId());
                DateTime dt = new DateTime();
                dt = dt.minusDays( multiplier );
                fieldHistory.setCreatedDate(dt);
                if ( x < 2 ) {
                    // these will be amount field histories
                    fieldHistory.setField("amount");
                    fieldHistory.setOldValue("" + (1000f * multiplier));
                    fieldHistory.setNewValue("" + (1000f * multiplier + 20));
                } else {
                    fieldHistory.setField("ClosedDate");
                    fieldHistory.setOldValue("1/2/2016");
                    fieldHistory.setNewValue("2/2/2016");
                }
                persistence.save(fieldHistory);
            }
        }
        
        List<Opportunity> opportunities = (List<Opportunity>) persistence.getAllForIndex(DbIndexType.SALESFORCE_OPPORTUNITY_TYPE, tenantId);
        
        // make sure we have opportunity field histories
        List<OpportunityFieldHistory> oppFieldHistories = (List<OpportunityFieldHistory>)
                persistence.getAllForIndex(DbIndexType.SALESFORCE_OPPORTUNITY_FIELD_HISTORY_TYPE, tenantId);
        
        SearchSpec searchSpec = new SearchSpec();
        
        SortQuery sortQuery = new SortQuery();
        sortQuery.setField("createddate");
        sortQuery.setIsAscending(false);
        searchSpec.setSortQuery(sortQuery);
        
        SearchQuery sq = new SearchQuery("field", "amount");
        searchSpec.addQuery(sq);
        
        CurrencyLayerManager currencyMgr = CurrencyLayerManager.getInstance();
        
        for ( Opportunity opp : opportunities ) {
            
            String currencyIsoCode = opp.getCurrencyIsoCode();
            if ( !AppConstants.USD_ISO_CODE.equalsIgnoreCase(currencyIsoCode) ) {
                OpportunityFieldHistory fieldHistory = (OpportunityFieldHistory)
                        persistence.getFirstOne(DbIndexType.SALESFORCE_OPPORTUNITY_FIELD_HISTORY_TYPE, searchSpec, tenantId);
                
                if ( fieldHistory != null ) {
                    int year = fieldHistory.getCreatedDate().getYear();
                    int month = fieldHistory.getCreatedDate().getMonthOfYear();
                    
                    Double amount = currencyMgr.convertAmountToDollars(opp.getOpportunityId(), opp.getAmount(), currencyIsoCode, year, month, "OPPORTUNITY");
                    opp.setAmount(amount);
                }
            }
            
        }
    }
    
    @Test
    public void validateUsernameTest() throws Exception {
        User user1 = new User();
        user1.setUsername("adminx");
        persistence.save(user1);
        
        User user2 = new User();
        user2.setUsername("adminb");
        persistence.save(user1);
        
        Page p = persistence.getPage(DbIndexType.USER_TYPE, "username", "admin", null /*tenantId*/);
        
        Assert.assertEquals(1, p.getItemCount());
        
        p = persistence.getPage(DbIndexType.USER_TYPE);
        
        Assert.assertEquals(true, p.getItemCount() > 1);
    }
    
    @Test
    public void validateSfCoverageStatPersistence() throws Exception {
    		DateTime start = new DateTime();
    		start = start.minusMonths(1);
    		String tenantId = "x1";

    		List<String> dateDescriptions = Lists.newArrayList();
    		List<DateTime> statDates = Lists.newArrayList();
    		for (int i = 0; i < 10; i++) {
    			String dateDesc = "linearity_" + start.getMillis();
    			System.out.println(dateDesc);
    			dateDescriptions.add(dateDesc);
    			
    			SfCoverageStat stat = new SfCoverageStat();
    			stat.setDateDescription(dateDesc);
    			stat.setStatDate(start);
    			stat.setStatEndDate(start);
    			statDates.add(start);
    			stat.setTenantId(tenantId);
    			stat.setAccurate(i);
    			persistence.create(stat);
    			
    			start = start.plusDays(1);
    		}
    		
    		for (String dateDescription : dateDescriptions ) {
	    		SfCoverageStat sfCoverageStat = (SfCoverageStat) persistence.get(
						DbIndexType.SF_COVERAGE_STAT_TYPE, "datedescription", dateDescription, tenantId);
	    		Assert.assertNotNull(sfCoverageStat);
    		}
    }
    
}
