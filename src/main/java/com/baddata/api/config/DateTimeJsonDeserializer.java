/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.config;

import java.lang.reflect.Type;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * 
 * This JSON Deserializer is used to deserialize an ISO 8601 date string to a Joda-DateTime object
 *
 */
public class DateTimeJsonDeserializer implements JsonDeserializer<DateTime> {

    @Override
    public DateTime deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) throws JsonParseException {
        
        final DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        
        return fmt.parseDateTime(json.getAsString());
    }
}
