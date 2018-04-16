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
public abstract @interface ApiInfo {

	/**
	 * Set to let method annotation readers know if the API is public or not.
	 * 
	 * <code>@ApiInfo(isPublic=true)</code>
	 * 
	 * @return true if the annotated API is available without logging in
	 */
	boolean isPublicApi() default false;
	
	/**
	 * Set to let method annotation readers know if the API requires a uber user session or not.
	 * 
	 * @return true if the annotated API requires an uber user session to access
	 */
	boolean requiresUberUserSesssion() default false;

}
