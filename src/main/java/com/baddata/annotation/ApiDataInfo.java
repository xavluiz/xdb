/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
*
* The annotation used on outgoing REST DTOs to enable
* auto-generation to recognize a DTO
*
*/
@Retention(value=RetentionPolicy.RUNTIME)
@Target(value={ElementType.METHOD, ElementType.FIELD})
public abstract @interface ApiDataInfo {

    /**
     * Set to let method annotation readers know if the bean attribute data is persisted or not
     * 
     * <code>@ApiDataInfo(isPersisted=false)</code>
     * 
     * @return false if the attribute data should not be persisted
     */
    boolean isPersisted() default true;
}
