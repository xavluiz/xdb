package com.baddata.api.factory;

import java.util.HashMap;
import java.util.Map;

public class BaddataHttpParams {

    protected int connectionPoolTimeout = -1; // Defines the timeout in milliseconds used when retrieving a connection from the connection pool manager
    protected int connectionTimeout = -1; //Determines the timeout in milliseconds until a connection is established
    
    /*
     * Defines the socket timeout (SO_TIMEOUT) in milliseconds, which is the timeout for waiting for data or,
     * put differently, a maximum period inactivity between two consecutive data packets). A timeout value of zero is interpreted as an infinite timeout.
     */
    protected int socketTimeout = -1; 
    
    /*
     * Defines whether or not TCP is to send automatically a keepalive probe to the peer after an interval of inactivity (no data exchanged in either direction) 
     * between this host and the peer. The purpose of this option is to detect if the peer host crashes. 
     */
    protected boolean socketKeepAlive = false; 

    String sessionId; // session id set in the set-cookie header
    
    Map<String, String> headers; // http headers to set in the http request 
    
    public BaddataHttpParams() {
        sessionId = null;
        headers = new HashMap<String,String>();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId( String sessionId ) {
        this.sessionId = sessionId;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders( Map<String, String> headers ) {
        this.headers = headers;
    }

    public int getConnectionPoolTimeout() {
        return connectionPoolTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionPoolTimeout( int connectionPoolTimeout ) {
        this.connectionPoolTimeout = connectionPoolTimeout;
    }

    public void setConnectionTimeout( int connectionTimeout ) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public boolean isSocketKeepAlive() {
        return socketKeepAlive;
    }

    public void setSocketTimeout( int socketTimeout ) {
        this.socketTimeout = socketTimeout;
    }

    public void setSocketKeepAlive( boolean socketKeepAlive ) {
        this.socketKeepAlive = socketKeepAlive;
    }
}
