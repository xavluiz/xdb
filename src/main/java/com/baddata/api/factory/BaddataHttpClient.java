// Copyright 2016 Baddata, Inc. All rights reserved.
package com.baddata.api.factory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.log4j.Logger;

/**
 * 
 * Apache closeable http client with helper request executors and entity readers
 * 
 */
public class BaddataHttpClient {

    private static Logger logger = Logger.getLogger("baddatahttpclient");

    public enum HttpRequestType {
        DELETE, GET, POST, PUT;
    }

    // ************************************************************************
    // Attributes
    // ************************************************************************

    private String url;
    private CloseableHttpClient httpclient = null;

    protected PoolingHttpClientConnectionManager getConnectionManager() {
        return HttpConnectionManager.getClientConnectionManager();
    }

    // ************************************************************************
    // Constructor
    // ************************************************************************

    public BaddataHttpClient(String fullHostName, boolean secure) {
        this(fullHostName, 0 /*port*/, secure);
    }
    
    /**
     * 
     * @param fullHostName
     * @param port
     * @param secure
     */
    public BaddataHttpClient(String fullHostName, int port, boolean secure) {
        if (fullHostName == null) {
            throw new IllegalArgumentException("Expected host name, got null");
        }

        // fullHostName is extracted out of the instance_url (i.e. https://na35.salesforce.com)
        // result will be "na35.salesforce.com"
        if ( secure ) {
            url = "https://" + fullHostName;
        } else {
            url = "http://" + fullHostName;
        }
        
        if ( port > 0 ) {
            url += ":" + port;
        }

        httpclient = createClient();
    }

    private CloseableHttpClient createClient() {
        httpclient = HttpClients.custom()
                .setConnectionManager(getConnectionManager())
                .disableCookieManagement()
                .build();
        
        return httpclient;
    }
    
    public HttpResponse execPostRequest(String uri) throws IOException {
        return getResponse(HttpRequestType.POST, uri, null /*params*/, null /*contentEntity*/);
    }
    
    public HttpResponse execPostRequest(String uri, BaddataHttpParams params) throws IOException {
        return getResponse(HttpRequestType.POST, uri, params, null /*contentEntity*/);
    }

    public HttpResponse execPostRequest(String uri, BaddataHttpParams params, String contentEntity) throws IOException {
        return getResponse(HttpRequestType.POST, uri, params, contentEntity);
    }

    public HttpResponse execPutRequest(String uri, BaddataHttpParams params, String contentEntity) throws IOException {
        return getResponse(HttpRequestType.PUT, uri, params, contentEntity);
    }
    
    public HttpResponse execGetRequest(String uri) throws IOException {
        return execGetRequest(uri, null /*params*/);
    }

    public HttpResponse execGetRequest(String uri, BaddataHttpParams params) throws IOException {

        String getRequestUrl = buildReqUrl(uri);
        HttpRequest request = new HttpRequest(HttpGet.METHOD_NAME, getRequestUrl);
        if ( params != null ) {
            request.setRequestParams(params);
        }
        request.addHeader("accept", "application/json");

        logger.debug("HTTP GET: " + getRequestUrl);
        try {
            return httpclient.execute(request);
        } catch (IOException ioe) {
            request.abort();
            throw ioe;
        }
    }

    public HttpResponse execDeleteRequest(String uri, BaddataHttpParams params) throws IOException {

        String deleteRequestUrl = buildReqUrl(uri);
        HttpRequest request = new HttpRequest(HttpDelete.METHOD_NAME, deleteRequestUrl);
        if ( params != null ) {
            request.setRequestParams(params);
        }
        request.addHeader("accept", "application/json");

        logger.debug("HTTP DELETE: " + deleteRequestUrl);
        try {
            return httpclient.execute(request);
        } catch (IOException ioe) {
            request.abort();
            throw ioe;
        }
    }

    /**
     * This method execute the PUT or POST method.
     * 
     * @param requestType
     * @param uri
     * @param params
     * @param entity
     * @return
     * @throws IOException
     */
    protected HttpResponse getResponse(HttpRequestType requestType, String uri, BaddataHttpParams params, String entity) throws IOException {

        HttpRequest request = null;
        String reqUrl = buildReqUrl(uri);
        logger.debug("HTTP " + requestType.name() + ": " + reqUrl);
        if (requestType.equals(HttpRequestType.POST)) {
            request = new HttpRequest(HttpPost.METHOD_NAME, reqUrl);
        } else if (requestType.equals(HttpRequestType.PUT)) {
            request = new HttpRequest(HttpPut.METHOD_NAME, reqUrl);
        } else {
            throw new IllegalArgumentException("Request type can be only POST or PUT");
        }

        // set the request parameters to control the execution behavior such as
        // connection timeout, sessionid, etc
        if ( params != null ) {
            request.setRequestParams(params);
        }
        request.addHeader("accept", "application/json");

        // set request body content
        if ( entity != null ) {
            request.setEntity(new StringEntity(entity));
        }

        try {
            return httpclient.execute(request);
        } catch (IOException ioe) {
            request.abort();
            throw ioe;
        }
    }

    class HttpRequest extends HttpEntityEnclosingRequestBase {

        String methodName;

        public HttpRequest(String methodName) {
            this.methodName = methodName;
        }

        public HttpRequest(String methodName, String uri) {
            super();
            this.methodName = methodName;
            setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
            setURI(URI.create(uri));
        }

        @Override
        public String getMethod() {
            return methodName;
        }

        protected void setSessionId(String sessionId) {
            if (!StringUtils.isEmpty(sessionId)) {
                String str = "JSESSIONID=" + sessionId;
                this.setHeader(HttpHeaders.COOKIE, str);
            }
        }

        /**
         * This method set the request headers
         * 
         * @param request
         * @param headers
         */
        protected void addHeaders(Map<String, String> headers) {
            if ( headers != null && !headers.isEmpty() ) {
                Set<String> headerParams = headers.keySet();
                for ( String aHeaderParam : headerParams ) {
                    this.setHeader(aHeaderParam, headers.get(aHeaderParam));
                }
            }
        }
        
        protected void addOrUpdateHeader(String key, String value) {
            Header[] headers = this.getAllHeaders();
            if ( headers != null && headers.length > 0 ) {
                for ( Header header : headers ) {
                    if ( header.getName().toLowerCase().equals(key.toLowerCase())) {
                        // update this header by removing it then adding it at the end
                        this.removeHeaders(header.getName());
                        break;
                    }
                }
            }
            
            // add the header
            this.setHeader(key, value);
        }

        /**
         * This method set the parameters to define the method and control the
         * execution runtime behavior such as network connection timeout
         * 
         * @param request
         * @param params
         */
        protected void setRequestParams(BaddataHttpParams params) {
            if ( params != null ) {
                this.addHeaders(params.getHeaders());
                this.setSessionId(params.getSessionId());
            }
        }
    }

    public static String getStringRepresentation(HttpEntity res) throws IOException {
        if (res == null) {
            return null;
        }

        // Timing information--- verified that the data is still streaming
        // when we are called (this interval is about 2s for a large response.)
        // So in theory we should be able to do somewhat better by interleaving
        // parsing and reading, but experiments didn't show any improvement.
        //

        StringBuffer sb = new StringBuffer();
        InputStreamReader reader;
        reader = new InputStreamReader(res.getContent());

        BufferedReader br = new BufferedReader(reader);
        boolean done = false;
        while (!done) {
            String aLine = br.readLine();
            if (aLine != null) {
                sb.append(aLine);
            } else {
                done = true;
            }
        }
        br.close();

        return sb.toString();
    }
    
    protected String buildReqUrl(String uri) {
        // remove the beginning slash if the uri has one
        if ( uri.indexOf("/") == 0 ) {
            uri = uri.substring(1);
        }
        
        // check if the base url has an ending slash already,
        // and if so append the uri
        if ( url.charAt(url.length() - 1) == '/') {
            // just append it
            return url + uri;
        }
        
        // url doesn't have an ending slash, append
        // the uri with a slash in-between
        return url + "/" + uri;
    }
}
