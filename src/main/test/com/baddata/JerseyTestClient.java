/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.collections4.CollectionUtils;

import com.baddata.api.filter.CacheControlFilter;
import com.baddata.api.filter.RestApiFilter;
import com.baddata.api.listener.AppServletContextListener;
import com.baddata.util.AppConstants;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.WebAppDescriptor;

public class JerseyTestClient extends JerseyTest {

	public JerseyTestClient() throws Exception {
		
		// add the RestApiFilter and the api resources "PROPERTY_PACKAGES" package location
	    // add the AppServletContextListener to simulate webserver bootstrap
		super(new WebAppDescriptor.Builder()
		        .clientConfig(createClientConfig())
				.addFilter(CacheControlFilter.class, "CacheControlFilter")
				.addFilter(RestApiFilter.class, "RestApiFilter")
				.contextListenerClass(AppServletContextListener.class)
				.initParam(PackagesResourceConfig.PROPERTY_PACKAGES, "com.baddata.api")
				.contextPath( AppConstants.PATH_ROOT )
				.build());
		
		//
		// cookie management client filter to allow simulated login testing
		//
		this.client().addFilter(new ClientFilter() {
			
			private List<Object> cookies = new ArrayList<Object>();
			
			@Override
			public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
				
				if ( CollectionUtils.isNotEmpty( cookies ) ){
					request.getHeaders().put("Cookie", cookies);
				}
				
				//
				// This waits for the request to complete
				//
				ClientResponse response = getNext().handle( request );
				
				// take the cookies from the request/response
				MultivaluedMap<String, String> headerMap = response.getHeaders();
				List<String> setCookieList = headerMap.get("Set-Cookie");
				if ( CollectionUtils.isNotEmpty(setCookieList) ) {
				    cookies.addAll(setCookieList);
				}
				
				return response;
			}
		});
	}
	
	/**
	 * Creates custom REST client config which is mandatory since we don't use any JSON providers.
	 * @return Jersey Client Config with the required classes to read/write in(out)coming data.
	 */
	private static ClientConfig createClientConfig() throws Exception {
	    // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers(){return null;}
            public void checkClientTrusted(X509Certificate[] certs, String authType){}
            public void checkServerTrusted(X509Certificate[] certs, String authType){}
        }};

        // Install the all-trusting trust manager
        final ClientConfig config = new DefaultClientConfig();
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            SSLContext.setDefault(sc);
            
            HostnameVerifier allHostsValid = new InsecureHostnameVerifier();
            
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(allHostsValid, sc));
        } catch (Exception e) {
            throw new Exception("Failed to create an SSL context for the JerseyTest client config");
        }
        
	    return config;
	}
	
	private static class InsecureHostnameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String arg0, SSLSession arg1) {
            return true;
        }
	}

}
