/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.config;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

/**
 * 
 * This JSON Deserializer is used to deserialize an ISO 8601 date string to a Joda-DateTime object
 * for xml attribute annotations.
 * i.e. @XmlJavaTypeAdapter(value=com.baddata.api.config.DateTimeXmlAdapter.class)
 * 
 * format: 1969-12-31T16:00:00.000-08-00
 *
 */
public class DateTimeXmlAdapter extends XmlAdapter<String, DateTime> {

    @Override
    public String marshal(DateTime dateTime) throws Exception {
        return ( dateTime != null ) ? dateTime.toString() : new DateTime().toString();
    }

    @Override
    public DateTime unmarshal(String dateTime) throws Exception {
        return ( StringUtils.isNotBlank(dateTime) ) ? new DateTime( dateTime ) : new DateTime( 0 );
    }

}
