/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.config;

import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.SerializationConfig;

import com.sun.jersey.spi.resource.Singleton;

/**
 * 
 * Used to handle/transform response data into JSON the client can consume.
 *
 */
// An injectable interface providing runtime lookup of provider instances. 
@Provider
@Singleton
public class JsonProvider extends JacksonJaxbJsonProvider {

    public JsonProvider() {
        super();
        
        // ignore unknown properties (we have getters that do not have a matching field)
        this.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // DateTime in ISO format "2012-04-07T17:00:00.000+0000" instead of 'long' format
        this.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false );
        
        // this will ensure an empty array is returned instead of null
        // i.e. page -> "data":null will be "data":[]
        this.configure(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    }
}
