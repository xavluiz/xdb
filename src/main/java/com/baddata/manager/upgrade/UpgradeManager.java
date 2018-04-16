/**
 * Copyright (c) 2018 by Baddata.
 * All rights reserved.
 */
package com.baddata.manager.upgrade;

import java.util.List;

import javax.mail.search.SearchException;

import org.apache.commons.collections4.CollectionUtils;

import com.baddata.api.dto.salesforce.SalesforceOauth2Creds;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.exception.IndexPersistException;
import com.baddata.manager.db.PersistenceManager;
import com.google.common.collect.Lists;

public class UpgradeManager {

	private static UpgradeManager ref;
	
	private PersistenceManager persistence = PersistenceManager.getInstance();
	
	private List<SalesforceOauth2Creds> credsList = Lists.newArrayList();

    /**
     * Singleton instance
     * @return
     */
    public static UpgradeManager getInstance() {
        if (ref == null) {
            synchronized(UpgradeManager.class) {
                if ( ref == null ) {
                    ref = new UpgradeManager();
                }
            }
        }
        return ref;
    }
    
    public void startUpgradeTasks() {
    		this.credsList = (List<SalesforceOauth2Creds>) persistence.getAllForIndex(DbIndexType.SALESFORCE_OAUTH2_CREDS_TYPE, null /*tenantId*/);
    	
    		this.deleteOauthCredsWithTenantId();
    		this.disableSandboxForUsers();
    		this.deleteStaleData();
    }
    
    private void deleteOauthCredsWithTenantId() {
    		if ( CollectionUtils.isNotEmpty(credsList) ) {
			for (SalesforceOauth2Creds creds : credsList) {
				List<SalesforceOauth2Creds> credsInTenancy = (List<SalesforceOauth2Creds>)
						persistence.getAllForIndex(DbIndexType.SALESFORCE_OAUTH2_CREDS_TYPE, creds.getInstanceTenantId());
				if (CollectionUtils.isNotEmpty(credsInTenancy)) {
					for (SalesforceOauth2Creds tenancyCreds : credsInTenancy) {
						try {
							persistence.delete(tenancyCreds);
						} catch (IndexPersistException | SearchException e) {
							//
						}
					}
				}
			}
    		}
    }
    
    private void disableSandboxForUsers() {
		if ( CollectionUtils.isNotEmpty(credsList) ) {
			for (SalesforceOauth2Creds creds : credsList) {
				if ( creds.isUseSandboxMode() ) {
					// disable it
					creds.setUseSandboxMode(false);
					try {
						persistence.update(creds);
					} catch (IndexPersistException e) {
						//
					}
				}
			}
		}
	}
    
    private void deleteStaleData() {
    	
    		if ( CollectionUtils.isNotEmpty(credsList) ) {
			for (SalesforceOauth2Creds creds : credsList) {
				String tenantId = creds.getInstanceTenantId();
				try {
					persistence.deleteObjectsByIndexType(DbIndexType.SF_COVERAGE_SUMMARY_TYPE, tenantId);
					persistence.deleteObjectsByIndexType(DbIndexType.SF_COVERAGE_STAT_TYPE, tenantId);
				} catch (IndexPersistException | SearchException e) {
					//
				}
			}
    		}
    }
}
