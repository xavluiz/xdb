/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.db;

import java.util.List;

import com.baddata.api.dto.DbIndexInfo;
import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.currency.CurrencyQuoteInfo;
import com.baddata.api.dto.dbindex.IndexInfo;
import com.baddata.api.dto.job.Progress;
import com.baddata.api.dto.profiler.Phase;
import com.baddata.api.dto.profiler.PhaseStat;
import com.baddata.api.dto.salesforce.Account;
import com.baddata.api.dto.salesforce.DataSyncInfo;
import com.baddata.api.dto.salesforce.Opportunity;
import com.baddata.api.dto.salesforce.OpportunityField;
import com.baddata.api.dto.salesforce.OpportunityFieldHistory;
import com.baddata.api.dto.salesforce.OpportunityFieldPreferences;
import com.baddata.api.dto.salesforce.OpportunityStageField;
import com.baddata.api.dto.salesforce.OpportunityStageFieldPreference;
import com.baddata.api.dto.salesforce.OpportunityStatus;
import com.baddata.api.dto.salesforce.SalesforceOauth2Creds;
import com.baddata.api.dto.salesforce.SalesforceUser;
import com.baddata.api.dto.salesforce.summary.coverage.SfCoverageRange;
import com.baddata.api.dto.salesforce.summary.coverage.SfCoverageStat;
import com.baddata.api.dto.salesforce.summary.objectives.SfObjectiveStat;
import com.baddata.api.dto.support.SupportReq;
import com.baddata.api.dto.system.AuditLogApiInfo;
import com.baddata.api.dto.system.AuditLogErrorInfo;
import com.baddata.api.dto.system.AuditLogPropertyStat;
import com.baddata.api.dto.system.AuditLogStat;
import com.baddata.api.dto.system.BaddataLogEvent;
import com.baddata.api.dto.system.ConfigInfo;
import com.baddata.api.dto.system.LogMonitor;
import com.baddata.api.dto.user.AuthenticationToken;
import com.baddata.api.dto.user.User;
import com.baddata.api.dto.user.UserSettings;
import com.google.common.collect.Lists;


public class DbIndex {
    
    public enum DbIndexType {
        
        DB_INDEX_INFO_TYPE( DbIndexInfo.class, false /* hasTenantIndex */),
        USER_TYPE( User.class, false /* hasTenantIndex */),
        USER_SETTINGS_TYPE( UserSettings.class, false /* hasTenantIndex */),
        PROGRESS_TYPE( Progress.class, false /* hasTenantIndex */),
        AUTHENTICATION_TOKEN_TYPE( AuthenticationToken.class, false /* hasTenantIndex */, true /*apiDeletable*/),
        PHASE_TYPE( Phase.class, false /* hasTenantIndex */, true /*apiDeletable*/),
        PHASE_STAT_TYPE( PhaseStat.class, false /* hasTenantIndex */, true /*apiDeletable*/),
        BADDATA_LOG_EVENT_TYPE( BaddataLogEvent.class, false /* hasTenantIndex */, true /*apiDeletable*/),
        GENERIC_INFO_TYPE( TypedObject.class, false /* hasTenantIndex */),
        CONFIG_INFO_TYPE( ConfigInfo.class, false /* hasTenantIndex */),
        LOG_MONITOR_TYPE( LogMonitor.class, false /* hasTenantIndex */),
        AUDIT_LOG_API_INFO_TYPE( AuditLogApiInfo.class, false /* hasTenantIndex */, true /*apiDeletable*/),
        AUDIT_LOG_ERROR_INFO_TYPE( AuditLogErrorInfo.class, false /* hasTenantIndex */, true /*apiDeletable*/),
        AUDIT_LOG_STAT_TYPE( AuditLogStat.class, false /* hasTenantIndex */, true /*apiDeletable*/),
        AUDIT_LOG_PROPERTY_STAT_TYPE( AuditLogPropertyStat.class, false /* hasTenantIndex */, true /*apiDeletable*/);
    	
        
        private String indexId;
        private String canonicalName;
        private boolean hasTenantIndex;
        private boolean apiDeletable;
        
        private DbIndexType( Class<? extends TypedObject> clazz, boolean tenantIndex ) {
            this.initialize(clazz, tenantIndex, false /*apiDeletable*/);
        }
        
        private DbIndexType( Class<? extends TypedObject> clazz, boolean tenantIndex, boolean apiDeletable ) {
            this.initialize(clazz, tenantIndex, apiDeletable);
        }
        
        private void initialize(Class<? extends TypedObject> clazz, boolean tenantIndex, boolean apiDeletable) {
            this.indexId = clazz.getSimpleName();
            this.canonicalName = clazz.getCanonicalName();
            this.hasTenantIndex = tenantIndex;
            this.apiDeletable = apiDeletable;
        }
        
        public String getIndexId() {
            return indexId;
        }
        
        public void setIndexId(String indexId) {
            this.indexId = indexId;
        }

		public boolean hasTenantIndex() {
			return hasTenantIndex;
		}

		public void setHasTenantIndex(boolean hasTenantIndex) {
			this.hasTenantIndex = hasTenantIndex;
		}

		public boolean isApiDeletable() {
            return apiDeletable;
        }

        public void setApiDeletable(boolean apiDeletable) {
            this.apiDeletable = apiDeletable;
        }

        public String getCanonicalName() {
            return canonicalName;
        }
        
        public void setCanonicalName(String canonicalName) {
            this.canonicalName = canonicalName;
        }
        
        public static DbIndexType getTypeById(String indexId) {
        	for ( DbIndexType type : DbIndexType.values() ) {
        		if ( type.indexId.equals(indexId) ) {
        			return type;
        		}
        	}
        	DbIndexType unknownType = DbIndexType.GENERIC_INFO_TYPE;
            unknownType.setIndexId(indexId);
            return unknownType;
        }

        @Override
        public String toString() {
            return "DbIndexType [indexId=" + indexId + ", canonicalName=" + canonicalName + "]";
        }
        
    }

    public static DbIndexType getIndexId( TypedObject typedObj ) {
        Class<? extends TypedObject> clazz = typedObj.getClass();
        return getIndexTypeByClass(clazz);
    }
    
    public static DbIndexType getIndexTypeByClass(Class<? extends TypedObject> clazz) {
        for ( DbIndexType type : DbIndexType.values() ) {
            if ( type.getCanonicalName().equals(clazz.getCanonicalName() ) ) {
                return type;
            }
        }
        // create a new one on the fly
        DbIndexType unknownType = DbIndexType.GENERIC_INFO_TYPE;
        unknownType.setCanonicalName(clazz.getCanonicalName());
        unknownType.setIndexId(clazz.getSimpleName());
        unknownType.setHasTenantIndex(false);
        return unknownType;
    }
    
    public static DbIndexType getIndexTypeByIndexIdValue(String indexId) {
    	for ( DbIndexType type : DbIndexType.values() ) {
    		if ( type.getIndexId().equalsIgnoreCase(indexId) ) {
    			return type;
    		}
    	}
    	// create a new one on the fly
        DbIndexType unknownType = DbIndexType.GENERIC_INFO_TYPE;
        unknownType.setCanonicalName(Object.class.getCanonicalName());
        unknownType.setIndexId(indexId);
        unknownType.setHasTenantIndex(false);
        return unknownType;
    }
    
    public static List<IndexInfo> getIndexIds() {
    	List<IndexInfo> indexIds = Lists.newArrayList();
    	for ( DbIndexType type : DbIndexType.values() ) {
    		IndexInfo indexInfo = new IndexInfo();
    		indexInfo.setIndexId(type.getIndexId());
    		indexInfo.setRequiresTenantId(type.hasTenantIndex());
    		indexInfo.setApiDeletable(type.apiDeletable);
    		indexIds.add(indexInfo);
    	}
    	return indexIds;
    }
    
}
