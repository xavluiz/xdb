/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.PropertyConfigurator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.baddata.api.dto.ApiDto;
import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.user.AuthenticationToken;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.lucene.IndexerService;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.manager.job.JobManager;
import com.baddata.manager.system.SystemManager;
import com.baddata.manager.user.UserSessionManager;
import com.baddata.util.AppConstants;
import com.baddata.util.FileUtil;

public class TestBase {
	
	final public static String DEFAULT_USERNAME = "test@test.com".intern();
	final public static String DEFAULT_USER_PASSWORD = "IronAde3131!".intern();
	final public static String DEFAULT_USER_FIRST_NAME = "test".intern();
	final public static String DEFAULT_USER_LAST_NAME = "user".intern();
	final public static String DEFAULT_TEST_TENANT_INSTANCE = "testuser_1".intern();
	
	protected static Logger logger = Logger.getLogger(TestBase.class.getName());
	
    protected PersistenceManager persistence;
    protected JobManager jobMgr;
    
    protected static boolean clearDatabase = true;
    
    static {
        // get the current absolute path
        String currentAbsolutePath = Paths.get(".").toAbsolutePath().normalize().toString();
        
        // set the catalina home and base
        String catalinaHome =  currentAbsolutePath + File.separatorChar + "src" + File.separatorChar + "main" + File.separatorChar + "tomcat";
        
        System.setProperty("CATALINA_HOME", catalinaHome);
        System.setProperty("CATALINA_BASE", catalinaHome);
        System.setProperty("catalina.base", catalinaHome);
        System.setProperty(AppConstants.UNIT_TESTING, "true");
        
        System.setProperty(AppConstants.ADMIN1_USERNAME, "admin1");
		System.setProperty(AppConstants.ADMIN1_PASSWORD, "BaddataIO1!");
		System.setProperty(AppConstants.ADMIN1_EMAIL, "xavluiz@gmail.com");
		
		System.setProperty(AppConstants.ADMIN2_USERNAME, "admin2");
        System.setProperty(AppConstants.ADMIN2_PASSWORD, "BaddataIO2@");
        System.setProperty(AppConstants.ADMIN2_EMAIL, "andrew.li.hl@gmail.com");
        
        System.setProperty(AppConstants.ADMIN_USERNAME, "admin");
        System.setProperty(AppConstants.ADMIN_PASSWORD, "datameetreality!");
        System.setProperty(AppConstants.ADMIN_EMAIL, "support@baddata.com");
        
        System.setProperty("dev.origin.url", "http://localhost:9998/");

        //
        // Set the BADDATA_LOGS env var so it can be
        // used by something like the log4j.properties (i.e. ${BADDATA_LOGS})
        //
        String bdLogsHome = currentAbsolutePath + File.separator + "logs";
        System.setProperty( AppConstants.BADDATA_LOGS_HOME_ENV_VAR, bdLogsHome );
        
        System.setProperty(AppConstants.SYNTHETIC_DATASET, "dataset5");
        
        // set the salesforce system properties we would normally load from the server.properties
        System.setProperty(AppConstants.SALESFORCE_CLIENT_ID, "3MVG9szVa2RxsqBY3Ai6jANkSwzKO6zBE2zqDwgwjIbqCLi57UEABnz3EFpv7H3Wp_PDpYjQK5lXbhjKt9v6U");
        System.setProperty(AppConstants.SALESFORCE_CLIENT_SECRET, "5366229420579493418");
        System.setProperty(AppConstants.SALESFORCE_OAUTH_TOKEN_API, "/services/oauth2/token");
        
        // build the log4j file absolute file path
        String log4jFile = currentAbsolutePath + File.separator + "util" + File.separator + "log4j.properties";
        
        // system property injections
        System.setProperty("fb.app.secret", "a1f5e858a52a7cd3d6f8f5039dd6fff9");
        
        //
        // Load the log4j.properties file
        //
        Properties props = new Properties();
        try {
            props.load(new FileInputStream(log4jFile));
            PropertyConfigurator.configure(props);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    @BeforeClass
    public static void beforeClassSetup() {
        //
    }
    
    @AfterClass
    public static void afterClassTeardown() {
        deleteLuceneIndexes();
    }
	
	@Before
	public void setUp() throws Exception {
        persistence = PersistenceManager.getInstance();
        jobMgr = JobManager.getInstance();
        
        AppConstants.MAX_SEARCH_LIMIT = 250;
        
        //
        // make sure the lucene store is gone and ready for new tests
        deleteLuceneIndexes();
        
        // start the user session manager
        UserSessionManager.getInstance().initAdminUsers();
        
        //
        // Ensure "isDevMode" within the SystemConfig DTO is persisted
        SystemManager.getInstance();
	}
	
	@After
    public void tearDown() {
	    // delete the logs dir
        File logsDir = new File(System.getProperty(AppConstants.BADDATA_LOGS_HOME_ENV_VAR));
        if ( logsDir != null && logsDir.isDirectory() ) {
            FileUtil.deleteDir(logsDir);
        }
    }
	
	@SuppressWarnings("unchecked")
	protected static void deleteLuceneIndexes() {
	    if ( clearDatabase ) {
	        // close the writers and remove the locks
	        IndexerService.getInstance().closeWritersAndRemoveLocks();
	        
	        // delete the lucene directory
	        File luceneDir = FileUtil.getLuceneDir();
	        FileUtil.deleteDir(luceneDir);
	    }
	}
	
	@SuppressWarnings("unchecked")
	protected void deleteByType(DbIndexType type, int page) throws Exception {
		Page p = persistence.get(type, 10, page);
		
		if ( p.getItemCount() > 0 ) {
    		List<ApiDto> result = (List<ApiDto>) p.getItems();
    		
    		for ( ApiDto dto : result ) {
    			persistence.delete(((TypedObject)dto));
    		}

    		if ( p.getPages() > 1 && p.getPage() < p.getPages() ) {
    		    // deletions will end up decreasing pages, just
    		    // keep deleting what is returned in the 1st page
    			this.deleteByType( type, 1 );
    		}
    	}
	}
	
	/**
	 * Generate a password token and persist it to the db.
	 * @param username
	 * @param newPassword
	 * @param exptime
	 * @return
	 * @throws Exception
	 */
	protected AuthenticationToken createSavePasswordToken(String username, long exptime) throws Exception {
		AuthenticationToken token = new AuthenticationToken();
		token.setEmail(username);
		token.setExpirationtime(exptime);
		
		Long reference = persistence.create(token);
		
		return (AuthenticationToken) persistence.getById(DbIndexType.AUTHENTICATION_TOKEN_TYPE, reference);
	}

}
