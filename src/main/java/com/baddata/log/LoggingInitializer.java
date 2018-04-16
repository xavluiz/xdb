/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.log;


import java.net.URL;

import org.apache.log4j.PropertyConfigurator;

public class LoggingInitializer {

    private static final String LOG4J_XML_FILE = "Resources/log4j.properties";
    private static final int ONE_MINUTE = 60000;

    /**
     * Start log4j and watch the config every minute
     */
    public void start() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL url = cl.getResource(LOG4J_XML_FILE);
        System.out.println("url is " + url);
        if (url != null) {
            String configFile = url.getFile();
            PropertyConfigurator.configureAndWatch(configFile, ONE_MINUTE);
        }
    }
}
