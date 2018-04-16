/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved
 */
package com.baddata.api.filter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.baddata.api.factory.ApiSessionContext;
import com.baddata.log.Logger;
import com.google.common.collect.Maps;

public class CacheControlFilter implements Filter {
    
    private static final Logger logger = Logger.getLogger(CacheControlFilter.class.getName());
    
    private URL targetOrigin;
    private String allowOrigin;
    
    private URL altTargetOrigin;
    private String altAlowOrigin;
    
    private URL altTargetOriginSecondary;
    private String altAlowOriginSecondary;

    private Map<String, URL> sourceOrigins = Maps.newConcurrentMap();

    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        HttpServletResponse resp = (HttpServletResponse) response;
        
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
        
        // Protection against Type 1 Reflected XSS attacks
        resp.setHeader("X-XSS-Protection", "1; mode=block");
        // Disabling browsers to perform risky mime sniffing
        resp.setHeader("X-Content-Type-Options", "nosniff");
        
        // resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Access-Control-Allow-Headers", "x-requested-with, Content-Type");
        
        // max age, 5 minute
        resp.setHeader("Access-Control-Max-Age", "300000");
        resp.setHeader("Expires", "300000");
        
        resp.setDateHeader("Last-Modified", new Date().getTime());
        resp.setHeader("Cache-Control", "private, no-store, no-cache, must-revalidate, max-age=0, post-check=0, pre-check=0");
        resp.setHeader("Pragma", "no-cache");

        HttpServletRequest req = (HttpServletRequest) request;
        
        String accessDeniedReason;
        
        //
        // Make sure the origin and/or referer is available
        //
        String source = req.getHeader("Origin");
        if ( StringUtils.isBlank( source ) ) {
            source = req.getHeader("Host");
            if ( StringUtils.isBlank(source) ) {
                accessDeniedReason = "Cross site validation error: ORIGIN, HOST request headers are absent/empty for request '" + req.getRequestURI() + "', request forbidden.";
                logger.warn(accessDeniedReason);
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, accessDeniedReason);
                return;
            }
        }
        
        if ( source.indexOf("http") == -1 ) {
            // add the http in front since this is just the dev environment
            if (req.isSecure()) {
                source = "https://" + source;
            } else {
                source = "http://" + source;
            }
        }
        
        if (source.lastIndexOf("/") != source.length() - 1) {
            source += "/";
        }
        
        //
        // Compare the source with what we allow
        //
        URL sourceURL = this.sourceOrigins.get(source);
        if (sourceURL == null) {
            sourceURL = new URL(source);
            this.sourceOrigins.put(source, sourceURL);
        }
        
        //
        // Now compare the protocol and host
        //
        boolean matchesTargetOrigin = this.matchesOrigin(sourceURL);

        if ( !matchesTargetOrigin ) {
            accessDeniedReason = String.format("Cross site validation error. Origin/Host does not match target application '%s'.",
                    sourceURL.toString() );
            logger.warn(accessDeniedReason);
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, accessDeniedReason);
            return;
        }
        resp.addHeader("Access-Control-Allow-Origin", targetOrigin.getHost());
        
        chain.doFilter(req, resp);

    }
    
    private boolean matchesOrigin(URL sourceURL) {
        boolean matchesOriginVal = ( targetOrigin.getProtocol().equalsIgnoreCase(sourceURL.getProtocol()) &&
                targetOrigin.getHost().equalsIgnoreCase(sourceURL.getHost()) );

        // check if the sessionUrl was set
        if (ApiSessionContext.sessionUrl == null && matchesOriginVal) {
            ApiSessionContext.sessionUrl = allowOrigin;
        }

        if (!matchesOriginVal && altTargetOrigin != null) {
            // check if this matches
            matchesOriginVal = (altTargetOrigin.getProtocol().equalsIgnoreCase(sourceURL.getProtocol()) &&
                    altTargetOrigin.getHost().equalsIgnoreCase(sourceURL.getHost()));
            
            // check if the sessionUrl was set
            if (ApiSessionContext.sessionUrl == null && matchesOriginVal) {
                ApiSessionContext.sessionUrl = altAlowOrigin;
            }
        }

        if (!matchesOriginVal && altTargetOriginSecondary != null) {
            // check if this matches
            matchesOriginVal = (altTargetOriginSecondary.getProtocol().equalsIgnoreCase(sourceURL.getProtocol()) &&
                    altTargetOriginSecondary.getHost().equalsIgnoreCase(sourceURL.getHost()));
            
            // check if the sessionUrl was set
            if (ApiSessionContext.sessionUrl == null && matchesOriginVal) {
                // still use the alt origin. don't use the secondary, it's an IP address
                ApiSessionContext.sessionUrl = altAlowOrigin;
            }
        }

        return matchesOriginVal;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("CacheControlFilter init");

        String devOriginUrl = System.getProperty("dev.origin.url");
        String altOriginUrl = System.getProperty("alt.origin.url");
        String altOriginUrlSecondary = System.getProperty("alt.origin.url.secondary");
        String prodOriginUrl = System.getProperty("origin.url", "https://app.baddata.com/");
        
        if ( devOriginUrl != null ) {
            // it's a development environment
            allowOrigin = devOriginUrl;
        } else {
            allowOrigin = prodOriginUrl;
        }
        
        if ( altOriginUrl != null ) {
            altAlowOrigin = altOriginUrl;
            try {
                altTargetOrigin = new URL(altAlowOrigin);
            } catch (MalformedURLException e) {
                logger.error("Failed to set the alternate target origin when initializing the CacheControlFilter", e);
            }
        }
        
        if ( altOriginUrlSecondary != null ) {
            altAlowOriginSecondary = altOriginUrlSecondary;
            try {
                altTargetOriginSecondary = new URL(altAlowOriginSecondary);
            } catch (MalformedURLException e) {
                logger.error("Failed to set the alternate secondary target origin when initializing the CacheControlFilter", e);
            }
        }
        
        try {
            targetOrigin = new URL(allowOrigin);
        } catch (MalformedURLException e) {
            logger.error("Failed to set the target origin when initializing the CacheControlFilter", e);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
        logger.info("CacheControlFilter shutdown");
    }

}
