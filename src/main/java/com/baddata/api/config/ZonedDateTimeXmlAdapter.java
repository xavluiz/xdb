/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.config;

import java.time.ZonedDateTime;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.google.common.base.Strings;

/**
 * 
 * The ZonedDateTimeXmlAdapter will marshal and unmarshal the java 1.8 ZonedDateTime object.
 * This object caries the date, time, and a timezone in the ISO-8601 calendar system.
 * i.e. @XmlJavaTypeAdapter(value=com.baddata.api.config.ZonedDateTimeXmlAdapter.class)
 * 
 * format: 1969-12-31T16:00:00.000-08:00[America/Los_Angeles]
 */
public class ZonedDateTimeXmlAdapter extends XmlAdapter<String, ZonedDateTime> {

    @Override
    public String marshal(ZonedDateTime value) throws Exception {
        return ( value == null ) ? ZonedDateTime.now().toString() : value.toString();
    }

    @Override
    public ZonedDateTime unmarshal(String value) throws Exception {
        // e.g. ZonedDateTime can parse "2012-02-29T12:00:00+01:00[Europe/Paris]"
        ZonedDateTime zdt = ( Strings.isNullOrEmpty( value ) ) ? ZonedDateTime.now() : ZonedDateTime.parse(value);
        return zdt;
    }

}
