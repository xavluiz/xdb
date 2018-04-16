/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.config;

import java.lang.reflect.Type;
import java.time.ZonedDateTime;

import com.google.common.base.Strings;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public class ZonedDateTimeJsonDeserializer implements JsonDeserializer<ZonedDateTime> {

    @Override
    public ZonedDateTime deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) throws JsonParseException {
        // e.g. ZonedDateTime can parse "2012-02-29T12:00:00+01:00[Europe/Paris]"
        
        String value = json.getAsString();
        ZonedDateTime zdt = ( Strings.isNullOrEmpty( value ) ) ? ZonedDateTime.now() : ZonedDateTime.parse( value );
        
        return zdt;
    }

}