/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.util;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;

import com.baddata.api.config.JsonObjectMapper;


public class JsonRepresentation {

	public static String getJsonRepresentation(Object dsRead, Class<?> objClass) throws IOException{
        JsonObjectMapper om = new JsonObjectMapper();
        ObjectMapper mapper = om.getContext(objClass);
        return mapper.writeValueAsString(dsRead);
    }
}
