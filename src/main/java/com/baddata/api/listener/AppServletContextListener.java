/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.listener;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.baddata.api.dto.ApiDto;
import com.baddata.api.dto.job.Progress;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.salesforce.SalesforceOauth2Creds;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.lucene.IndexerService;
import com.baddata.log.EventLogger;
import com.baddata.log.EventLogger.EventMessage;
import com.baddata.log.Logger;
import com.baddata.manager.currency.CurrencyLayerManager;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.manager.job.JobManager;
import com.baddata.manager.job.JobManager.JobType;
import com.baddata.manager.salesforce.SalesforceDataLoadManager;
import com.baddata.manager.system.SystemManager;
import com.baddata.manager.upgrade.UpgradeManager;
import com.baddata.manager.user.TokenManager;
import com.baddata.manager.user.UserSessionManager;
import com.baddata.util.DateUtil;
import com.baddata.util.ExecutorServiceUtil;
import com.baddata.util.FileUtil;

import org.joda.time.DateTime;

/**
 * This is the main servlet context listener.  It's responsible for
 * loading the web.xml properties and starting any worker threads that need started.
 *
 */
public class AppServletContextListener implements ServletContextListener {
    
    private static Logger logger = Logger.getLogger(AppServletContextListener.class.getName());
    
    private static final String SERVER_PROPERTIES_FILE_NAME = "server.properties";
    private static final String DEV_SERVER_PROPERTIES_FILE_NAME = "dev.server.properties";
    private static final String VERSION_PROPERTIES_FILE_NAME = "version.properties";

    /**
     * Runs before the application is started
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.debug("Initializing services");
        
        
        //
        // load the web.xml properties
        //
        ServletContext sc = sce.getServletContext();
        Enumeration<String> webXmlParams = sc.getInitParameterNames();
        while ( webXmlParams.hasMoreElements() ) {
            String paramName = webXmlParams.nextElement();
            String paramVal = sc.getInitParameter( paramName );
            System.setProperty( paramName, paramVal );
            logger.info("Loaded property {" + paramName + ":'" + paramVal + "'}");
        }
        
        //
        // load the server.properties
        //
        String serverPropFile = "/WEB-INF/" + SERVER_PROPERTIES_FILE_NAME;
        this.loadServerProperties( sc, serverPropFile );
        String devServerPropFile = "/WEB-INF/" + DEV_SERVER_PROPERTIES_FILE_NAME;
        this.loadServerProperties( sc, devServerPropFile );
        String versionPropFile = "/WEB-INF/" + VERSION_PROPERTIES_FILE_NAME;
        this.loadServerProperties(sc, versionPropFile);
        
        //
        // delete file contents
        FileUtil.writeToFile(FileUtil.getOpportunityLogInfoFilePath(), "", false /*append*/);

        ExecutorServiceUtil.getInstance().init();
        
        logger.info("AppServletContextListener Start: checking to remove locks to restart the index service");
        IndexerService.getInstance().start();
        logger.info("AppServletContextListener Start: completed check to remove locks to restart the index service");
        
        SystemManager.getInstance();
        
        JobManager.getInstance();
        
        // start the token manager
        TokenManager.getInstance();
        
        // start the user session manager
        UserSessionManager.getInstance();
        
        UpgradeManager.getInstance().startUpgradeTasks();
        
        // start the salesforcemanager
        SalesforceDataLoadManager.getInstance();
        
        // start the currency layer instance manager
        CurrencyLayerManager.getInstance().start();
        
        logger.debug("Completed initializing services");
    }
    
    /**
     * Runs when the application shuts down
     */
    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        logger.info("AppServletContextListener Destroyed");

        //
        // Check if there are any jobs, if so they will be deleted on startup
        // so we should add an alert that they will need to resync the data
        JobManager jobMgr = JobManager.getInstance();
        Page p = PersistenceManager.getInstance().getPage(DbIndexType.SALESFORCE_OAUTH2_CREDS_TYPE);
        if (p != null && p.getTotalHits() > 0) {
            List<ApiDto> oauthCreds = (List<ApiDto>) p.getItems();

            for (ApiDto oauthCred : oauthCreds) {
                SalesforceOauth2Creds creds = (SalesforceOauth2Creds) oauthCred;
                Progress progress = jobMgr.getJobInProgress(JobType.SALESFORCE_DATA, creds.getDynamicInstanceTenantId());
                if ( progress != null && !progress.isDone() ) {
                    // add an alert for this user
                    String dateTimeFailed = DateUtil.formatDate(DateTime.now(), DateUtil.shortDateTimeFormatter);
                    EventLogger.log(creds.getUserRef(),
                        EventMessage.SALESFORCE_OPPORTUNITY_DATA_DOWNLOAD_INTERRUPTED_FAILED,
                        dateTimeFailed);
                }
            }
        }
        
        //
        // Wait to complete indexing
        logger.info("AppServletContextListener End: waiting to complete indexing");
        IndexerService.getInstance().stop();
        logger.info("AppServletContextListener End: completed waiting to complete indexing");
        
        //
        // Shutdown ScheduledExecutorService's
        //
        logger.info("Waiting on executor service scheduler to close");
        ExecutorServiceUtil.getInstance().stop();
        
        logger.info("Waiting on shutting down any currency layer tasks");
        CurrencyLayerManager.getInstance().stop();
        
        logger.info("Completed closing services, tomcat is shutting down");
        
    }
    
    /**
     * Load the server.properties file into the system properties memory
     * @param ctx
     */
    protected void loadServerProperties(ServletContext ctx, String fileName) {
        Properties props = System.getProperties();
        
        InputStream is = ctx.getResourceAsStream( fileName );
        if ( is == null ) {
            logger.error( "Failed to locate properties file '" + fileName + "'.", null);
            return;
        }
        
        try {
            props.load( is );
        } catch ( Exception e ) {
            logger.error( "Failed to load server properties file '" + fileName + "', moving on to the next initialization task.", e );
        } finally {
            // close the input stream
            try {
                if ( is != null ) {
                    is.close();
                }
            } catch ( Exception e ) {
                logger.error( "Failed to close input stream for server properties file '" + fileName + "'.", e );
            }
        }
    }

}
