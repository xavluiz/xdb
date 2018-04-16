/**
 * Copyright (c) 2015 by Relayride.
 * All rights reserved.
 */
package com.baddata.api.config;

import java.math.BigDecimal;

import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * 
 * This JSON Deserializer is used to deserialize an ISO big decimal string to a BigDecimal object
 * for xml attribute annotations.
 * i.e. @XmlJavaTypeAdapter(value=com.relayride.api.config.BigDecimalXmlAdapter.class)
 *
 */
public class BigDecimalXmlAdapter extends XmlAdapter<String, BigDecimal> {

    @Override
    public String marshal(BigDecimal value) throws Exception {
        if (value!= null) {
            return value.toString();
        }
        return null;
    }

    @Override
    public BigDecimal unmarshal(String value) throws Exception {
        return new BigDecimal(value);
    }

}
