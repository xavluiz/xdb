// Copyright 2016, Baddata, Inc. All rights reserved.
package com.baddata.api.factory;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import com.baddata.util.AppUtil;


public class HttpConnectionManager {

    private static final String CLIENT_HTTPS_PROTOCOL = AppUtil.get( "baddata.client.https.protocol", "TLSv1.2" );

    private static PoolingHttpClientConnectionManager connManager;
    
    public static HttpConnectionManager singletonInstance;

    synchronized public static HttpConnectionManager getSingletonInstance(){
        if (singletonInstance == null){
            try {
                singletonInstance = new HttpConnectionManager();
            } catch ( Exception e ) {
                return null;
            }
        }
        return singletonInstance;
    }
    
    private static TrustManager[] trustAllCerts = new TrustManager[] { new HttpClientTrustManager() };

    private HttpConnectionManager() throws NoSuchAlgorithmException, KeyManagementException {
        // With JVM 1.7, SSLv3, TLSv1, TLSv1.1 and TLSv1.2 are enabled. In the future if we upgrade to JVM 1.8, TLSv1,
        // TLSv1.1, and TLSv1.2 will be the enabled protocols.
        SSLContext sslcontext = SSLContext.getInstance( CLIENT_HTTPS_PROTOCOL );
        sslcontext.init(null, trustAllCerts, new java.security.SecureRandom());

        Registry<ConnectionSocketFactory> connRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", new SSLConnectionSocketFactory(sslcontext))
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .build();
        
        connManager = new PoolingHttpClientConnectionManager(connRegistry);
        connManager.setMaxTotal(100);
        connManager.setDefaultMaxPerRoute(20);
    }

    private static class HttpClientTrustManager implements X509TrustManager {
        // Will add code to check protvided certs if we decide to authenticate remote server
        // Right now, we'll trust all provided certificates

        @Override
        public void checkClientTrusted( X509Certificate[] x509Cert, String authType ) throws CertificateException {

        }

        @Override
        public void checkServerTrusted( X509Certificate[] x509Cert, String authType ) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
    
    public static PoolingHttpClientConnectionManager getClientConnectionManager() {
        return connManager;
    }

    public void shutdown() {
        connManager.shutdown();
    }
}
