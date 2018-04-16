/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved
 */
package com.baddata.broker;

import java.util.List;

import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.system.AuditLogStat;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.RangeQuery;
import com.google.common.collect.Lists;

public class AuditLogBrokerImpl extends BaseBroker {
    
    public AuditLogBrokerImpl(Long userref, String userName) {
        super(userref, userName);
    }

    private void updateSearchSpecRange(SearchSpec searchSpec) {
        if (searchSpec.getSince() != null) {
            //
            // use the since time against the createTime
            List<RangeQuery> rangeQueries = Lists.newArrayList();
            RangeQuery rangeQuery = new RangeQuery("createtime", searchSpec.getSince().getMillis() /*min*/, null /*max*/);
            rangeQueries.add(rangeQuery);
            searchSpec.setRangeQueries(rangeQueries);
        }
    }

    public Page getAuditLogApiEntries(SearchSpec searchSpec) {
        this.updateSearchSpecRange(searchSpec);
        Page page = persistence.get( DbIndexType.AUDIT_LOG_API_INFO_TYPE, searchSpec );
        return page;
    }
    
    public Page getAuditLogErrorEntries(SearchSpec searchSpec) {
        this.updateSearchSpecRange(searchSpec);
        Page page = persistence.get( DbIndexType.AUDIT_LOG_ERROR_INFO_TYPE, searchSpec );
        return page;
    }
    
    public AuditLogStat getStats(SearchSpec searchSpec) {
        this.updateSearchSpecRange(searchSpec);
        AuditLogStat logStat = (AuditLogStat) persistence.getLatestObject(DbIndexType.AUDIT_LOG_STAT_TYPE, null /*tenantId*/);
        return logStat;
    }
}
