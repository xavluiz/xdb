/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.config;

import java.lang.reflect.Type;
import java.math.BigDecimal;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public class BigDecimalJsonDeserializer implements JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) throws JsonParseException {
        
        return BigDecimal.valueOf(json.getAsDouble());

    }
}
