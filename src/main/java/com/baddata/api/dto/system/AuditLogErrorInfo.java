/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.system;

import com.baddata.api.dto.TypedObject;


/**
 * Example
 * 2017-05-24 22:21:31.263, [Thread-8], ExecutorServiceUtil:147, ERROR - Failed wating for a executor service Future 'java.util.concurrent.FutureTask' to complete, error: java.util.concurrent.ExecutionException: java.lang.IndexOutOfBoundsException: Index: 0, Size: 0
    at java.util.concurrent.FutureTask.report(FutureTask.java:122)
    at java.util.concurrent.FutureTask.get(FutureTask.java:192)
    at com.baddata.util.ExecutorServiceUtil.waitForFuturesAndShutdown(ExecutorServiceUtil.java:143)
    at com.baddata.manager.salesforce.SalesforceDataWarehouse.getOpportunityFieldHistories(SalesforceDataWarehouse.java:228)
    at com.baddata.manager.salesforce.SalesforceDataWarehouse.getOpportunityFieldHistorPerType(SalesforceDataWarehouse.java:240)
    at com.baddata.manager.salesforce.SalesforceSummaryManager.buildAggregationSummary(SalesforceSummaryManager.java:873)
    at com.baddata.manager.salesforce.SalesforceSummaryManager.getSalesforceObjectiveSummary(SalesforceSummaryManager.java:751)
    at com.baddata.manager.salesforce.SalesforceSummaryManager.getObjectiveSummaryHistory(SalesforceSummaryManager.java:622)
    at com.baddata.manager.salesforce.SalesforceDataLoadManager$InitializeDataLoad.run(SalesforceDataLoadManager.java:315)
    at com.baddata.manager.salesforce.SalesforceDataLoadManager.onboardData(SalesforceDataLoadManager.java:209)
    at com.baddata.broker.SalesforceBrokerImpl$1.run(SalesforceBrokerImpl.java:465)
 */
public class AuditLogErrorInfo extends TypedObject {
    
    private String message;
    private String stackTrace;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

}
