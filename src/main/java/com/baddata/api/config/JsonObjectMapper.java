/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.config;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;


/**
 * 
 * Custom mapper needed to take handle of various serialization and deserialization features.
 *
 */
// An injectable interface providing runtime lookup of provider instances. 
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JsonObjectMapper implements ContextResolver<ObjectMapper> {
    ObjectMapper mapper;

    public JsonObjectMapper(){
        mapper = new ObjectMapper();
        
        mapper.configure(SerializationConfig.Feature.USE_ANNOTATIONS, true);

        mapper.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false );
        
        /*  mapping a JSON value to a Java collection property, java basic property will not result in an error. */
        mapper.configure(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        
        /* ignore unknown properties (we have getters that do not have matching properties) */
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setVisibility(JsonMethod.FIELD, Visibility.ANY);
    }
    
    @Override
    public ObjectMapper getContext(Class<?> type) {
        return mapper;
    }
}
