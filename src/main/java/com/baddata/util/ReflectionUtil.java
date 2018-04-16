/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.util;


import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.baddata.exception.BaddataException;
import com.baddata.log.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;


/**
 *
 * java reflection utility
 *
 */
public class ReflectionUtil {

    private static Logger logger = Logger.getLogger(ReflectionUtil.class.getName());

    private static Map<String, List<Method>> indexGetterMethodMap = new HashMap<String, List<Method>>();
    private static Map<String, Method> getterNameToGetterMethodMap = new HashMap<String, Method>();
    private static Map<String, String> getterMethodNameToPropertyNameMap = new HashMap<String, String>();
    private static Map<String, Method> fieldNameToSetterMethodMap = new HashMap<String, Method>();
    private static Map<String, List<Method>> indexSetterMethodMap = new HashMap<String, List<Method>>();
    private static Map<String, String> setterMethodNameToPropertyNameMap = new HashMap<String, String>();
    private static Map<String, Field[]> indexFieldMap = new HashMap<String, Field[]>();
    
    private static DateTime dt = new DateTime();
    
    static {
    		dt = dt.withZone(DateTimeZone.UTC);
    }

    public static Field[] getClassFields(final Class<?> aClass) {
        final Field[] classFields = aClass.getFields();

        return classFields;
    }

    public static Object getFieldValue(final Field field, final Object bean) {
        Object value = null;
        try {
            value = field.get(bean);
        } catch (Exception e) {
            logger.error("ReflectionUtil.getGetterMethodValue.", e );
        }

        return value;
    }

    public static Object getGetterMethodValue(final Method method, final Object bean) {
        Object value = null;
        try {
            value = method.invoke(bean, new Object[] {});
        } catch (Exception e) {
            logger.error("ReflectionUtil.getGetterMethodValue.", e );
        }

        return value;
    }

    public static String getGetterMethodValueToString(final Method method, final Object bean) {
        Object value = null;
        try {
            value = method.invoke(bean, new Object[] {});
            return ReflectionUtil.getObjectValueToString(method, value);
        } catch (Exception e) {
            logger.error("ReflectionUtil.getGetterMethodValueToString.", e );
        }

        return null;
    }

    public static void setPublicFieldValue(final Field field, final Object bean, final String val, final Class<?> paramType)
            throws IllegalArgumentException, IllegalAccessException {
        if (val != null) {
            String simpleName = paramType.getSimpleName();

            if (simpleName.equalsIgnoreCase("string") || paramType.isEnum()) {
                field.set(bean, val);
                return;
            }
            if (!val.equals("")) {
                if (simpleName.equalsIgnoreCase("int") || simpleName.equalsIgnoreCase("integer")) {
                    // int
                    field.setInt(bean, Integer.parseInt(val));
                } else if (simpleName.equalsIgnoreCase("boolean")) {
                    // boolean
                    field.setBoolean(bean, Boolean.parseBoolean(val));
                } else if (simpleName.equalsIgnoreCase("long")) {
                    // long
                    field.setLong(bean, Long.parseLong(val));
                } else if (simpleName.equalsIgnoreCase("datetime")) {
                    // org.joda.time.DateTime
                		field.set(bean, dt.withMillis(Long.parseLong(val)));
                } else if (simpleName.equalsIgnoreCase("float")) {
                    // float
                    field.setFloat(bean, Float.parseFloat(val));
                } else if (simpleName.equalsIgnoreCase("double")) {
                    // double
                    field.setDouble(bean, Double.parseDouble(val));
                } else if (simpleName.equalsIgnoreCase("short")) {
                    // short
                    field.setShort(bean, Short.parseShort(val));
                } else if (simpleName.equalsIgnoreCase("char")) {
                    field.setChar(bean, val.charAt(0));
                } else if (simpleName.equalsIgnoreCase("date")) {
                    field.set(bean, new Date(Long.parseLong(val)));
                } else if (simpleName.equals("calendar")) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(Long.parseLong(val));
                    field.set(bean, cal);
                } else if (paramType.isEnum()) {
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    Enum<?> enumVal = Enum.valueOf( (Class<Enum>)paramType, val );
                    field.set(bean, enumVal);
                } else {
                    field.set(bean, val);
                }
            }
        }
    }

    public static void setSetterMethodValue(final Method setterMethod, final Object bean, final String val, final Class<?> paramType)
            throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {
        if (val != null) {
            String simpleName = paramType.getSimpleName();
    
            if (paramType.isEnum()) {
                // enum
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Enum<?> enumVal = Enum.valueOf( (Class<Enum>) paramType, val );
                setterMethod.invoke(bean, enumVal);
                return;
            }
            
            // it's not an enum data type
            setSetterMethodValue(setterMethod, bean, val, simpleName);
        }
    }
    
    public static void setSetterMethodValue(final Method setterMethod, final Object bean, final String val, final String simpleName)
            throws IllegalArgumentException, IllegalAccessException, InvocationTargetException, SecurityException, NoSuchMethodException {

        if (val != null) {
            if (simpleName.equalsIgnoreCase("string")) {
                setterMethod.invoke(bean, val);
                return;
            }
            if (!val.equals("")) {
                if (simpleName.equalsIgnoreCase("int") || simpleName.equalsIgnoreCase("integer")) {
                    // int
                    setterMethod.invoke(bean, Integer.parseInt(val.toString()));
                } else if (simpleName.equalsIgnoreCase("boolean")) {
                    // boolean
                    setterMethod.invoke(bean, Boolean.parseBoolean(val.toString()));
                } else if (simpleName.equalsIgnoreCase("long")) {
                    // long
                    setterMethod.invoke(bean, Long.parseLong(val.toString()));
                } else if (simpleName.equalsIgnoreCase("datetime")) {
                    // org.joda.time.DateTime
                    if (StringUtils.isNumeric(val)) {
                    		setterMethod.invoke(bean, dt.withMillis(Long.parseLong(val)));
                    } else {
                        // try using the date util
                        try {
                            setterMethod.invoke(bean, DateUtil.buildUtcDateTime(val));
                        } catch (BaddataException e) {
                            logger.trace("Failed to build a datetime using value '" + val + "', error: " + e.toString());
                        }
                    }
                } else if (simpleName.equalsIgnoreCase("float")) {
                    // float
                    setterMethod.invoke(bean, Float.parseFloat(val.toString()));
                } else if (simpleName.equalsIgnoreCase("double")) {
                    // double
                    setterMethod.invoke(bean, Double.parseDouble(val.toString()));
                } else if (simpleName.equalsIgnoreCase("short")) {
                    // short
                    setterMethod.invoke(bean, Short.parseShort(val.toString()));
                } else if (simpleName.equalsIgnoreCase("date")) {
                    // date
                		setterMethod.invoke(bean, dt.withMillis(Long.parseLong(val)));
                } else if (simpleName.equalsIgnoreCase("calendar")) {
                    // calendar
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(Long.parseLong(val));
                    setterMethod.invoke(bean, cal);
                } else if (simpleName.equalsIgnoreCase("char")) {
                    // char
                    setterMethod.invoke(bean, val.charAt(0));
                } else if (simpleName.equalsIgnoreCase("bigdecimal")) {
                    // bigdecimal
                    setterMethod.invoke(bean, new BigDecimal(val));
                } else if (simpleName.equalsIgnoreCase("jsonarray")) {
                    // change the string value to a json array
                    JsonParser parser = new JsonParser();
                    JsonElement jsonEl = parser.parse(val);
                    JsonArray jsonArray = jsonEl.getAsJsonArray();
                    setterMethod.invoke(bean, jsonArray);
                } else {
                    // object, not yet supported
                	logger.trace("Updating a dynamic object accessor is not yet supported.");
                }
            }
        }
    }
    
    public static Object getParamTypeValue( final String val, final Class<?> paramType ) {
        if (val != null) {
            String simpleName = paramType.getSimpleName();

            if (simpleName.equalsIgnoreCase("string")) {
                return val;
            }
            if (!val.equals("")) {
                if (simpleName.equalsIgnoreCase("int") || simpleName.equalsIgnoreCase("integer")) {
                    // int
                    return Integer.parseInt(val.toString());
                } else if (simpleName.equalsIgnoreCase("boolean")) {
                    // boolean
                    return Boolean.parseBoolean(val.toString());
                } else if (simpleName.equalsIgnoreCase("long")) {
                    // long
                    return Long.parseLong(val.toString());
                } else if (simpleName.equalsIgnoreCase("datetime")) {
                    // org.joda.time.DateTime
                		return dt.withMillis(Long.parseLong(val));
                } else if (simpleName.equalsIgnoreCase("float")) {
                    // float
                    return Float.parseFloat(val.toString());
                } else if (simpleName.equalsIgnoreCase("double")) {
                    // double
                    return Double.parseDouble(val.toString());
                } else if (simpleName.equalsIgnoreCase("short")) {
                    // short
                    return Short.parseShort(val.toString());
                } else if (paramType.isEnum()) {
                    // enum
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    Enum<?> enumVal = Enum.valueOf( (Class<Enum>)paramType, val );
                    return enumVal;
                } else if (simpleName.equalsIgnoreCase("date")) {
                    // date
                    return new Date(Long.parseLong(val));
                } else if (simpleName.equalsIgnoreCase("calendar")) {
                    // calendar
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(Long.parseLong(val));
                    return cal;
                } else if (simpleName.equalsIgnoreCase("char")) {
                    // char
                    return val.charAt(0);
                } else if (simpleName.equalsIgnoreCase("bigdecimal")) {
                    // bigdecimal
                    return new BigDecimal(val.toString());
                } else {
                    // object, not yet supported
                    logger.trace("Updating a dynamic object accessor is not yet supported.");
                }
            }
        }
        return null;
    }

    public static void setFieldValue(final Object classObj, Field field, final Object val) throws IllegalArgumentException, IllegalAccessException {
        if (val != null) {
            String fieldType = field.getType().getSimpleName().toLowerCase();
            if (fieldType.equals("string")) {
                field.set(classObj, val);
                return;
            }
            if (!val.toString().equals("")) {
                if (fieldType.equals("int") || fieldType.equals("integer")) {
                    field.set(classObj, Integer.parseInt(val.toString()));
                } else if (fieldType.equals("boolean")) {
                    field.set(classObj, Boolean.parseBoolean(val.toString()));
                } else if (fieldType.equals("long")) {
                    field.set(classObj, Long.parseLong(val.toString()));
                } else if (fieldType.equals("float")) {
                    field.set(classObj, Float.parseFloat(val.toString()));
                } else if (fieldType.equals("double")) {
                    field.set(classObj, Double.parseDouble(val.toString()));
                } else if (fieldType.equals("short")) {
                    field.set(classObj, Short.parseShort(val.toString()));
                } else if (fieldType.equals("char") || fieldType.equals("character")) {
                    field.set(classObj, ((Character)val).charValue());
                } else if (fieldType.equals("bigdecimal")) {
                    field.set(classObj, new BigDecimal(val.toString()));
                } else {
                    field.set(classObj, val);
                }
            }
        }
    }

    public static String getFieldValueToString(final Object val, final Class<?> returnType) {
        String type = returnType.getSimpleName();
        String rowVal = "";
        if (type.equalsIgnoreCase("boolean")) {
            rowVal = (val != null) ? Boolean.toString((Boolean)val) : "false";

        } else if (type.equalsIgnoreCase("string")) {
            rowVal = (val != null) ? "'" + (String)val + "'" : "''";

        } else if (type.equalsIgnoreCase("integer") || type.equalsIgnoreCase("int")) {
            rowVal = (val != null) ? Integer.toString((Integer)val) : "0";
            
        } else if (type.equalsIgnoreCase("datetime")) {
            rowVal = (val != null) ? "new DateTime()" : "null";

        } else if (type.equalsIgnoreCase("short")) {
            rowVal = (val != null) ? Short.toString((Short)val) : "0";

        } else if (type.equalsIgnoreCase("long")) {
            rowVal = (val != null) ? Long.toString((Long)val) : "0";

        } else if (type.equalsIgnoreCase("float") || type.equalsIgnoreCase("number")) {
            rowVal = (val != null) ? Float.toString((Float)val) : "0.0";

        } else if (type.equalsIgnoreCase("double")) {
            rowVal = (val != null) ? Double.toString((Double)val) : "0.0";

        } else if (type.equalsIgnoreCase("list") || type.equalsIgnoreCase("array") || type.equalsIgnoreCase("arraylist")) {
            rowVal = "[]";

        } else if (type.equalsIgnoreCase("date") || type.equalsIgnoreCase("calendar")) {
            rowVal = (val != null) ? "new Date()" : "null";

        } else if (returnType.isEnum()) {
            rowVal = (val != null) ? "'" + ((Enum<?>)val).name() + "'" : "''";
        } else {
            rowVal = "null";
        }
        return rowVal;
    }

    private static String getObjectValueToString(final Method method, final Object val) {
        String value = "";
        if (val != null) {
            String returnType = method.getReturnType().getSimpleName().toLowerCase();
            if (returnType.equals("string")) {
                value = val.toString();
            } else if (returnType.equals("int")) {
                value = Integer.toString((Integer)val);
            } else if (returnType.equals("boolean")) {
                value = Boolean.toString((Boolean)val);
            } else if (returnType.equals("long")) {
                value = Long.toString((Long)val);
            } else if (returnType.equals("datetime")) {
                value = ((DateTime)val).toString();
            } else if (returnType.equals("float")) {
                value = Float.toString((Float)val);
            } else if (returnType.equals("double")) {
                value = Double.toString((Double)val);
            } else if (returnType.equals("short")) {
                value = Short.toString((Short)val);
            } else if (returnType.equals("char") || returnType.equals("character")) {
                value = Character.toString((Character)val);
            } else if (returnType.equals("date")) {
                // dow mon dd hh:mm:ss zzz yyyy
                value = ((Date)val).toString();
            } else if (returnType.equals("calendar")) {
                // dow mon dd hh:mm:ss zzz yyyy
                value = ((Calendar)val).getTime().toString();
            } else if (val instanceof Object[] && ((Object[])val).length > 0) {
                StringBuffer sb = new StringBuffer();
                returnType = method.getReturnType().getName();
                sb.append("{").append("\"").append(returnType).append("\"").append(":").append("[");
                for (int idx = 0; idx < ((Object[])val).length; idx++) {
                    Object obj = ((Object[])val)[idx];
                    List<Method> getterMethods = ReflectionUtil.getGetterMethods(obj.getClass());
                    if (getterMethods != null) {
                        if (idx > 0) {
                            sb.append(",");
                        }
                        sb.append("{");
                        for (int i = 0; i < getterMethods.size(); i++) {
                            Method getter = getterMethods.get(i);

                            if (i > 0) {
                                // add bean delimiter
                                sb.append(",");
                            }

                            String fieldType = getter.getReturnType().getName();

                            String methodName = getter.getName().substring(3, getter.getName().length());
                            if (methodName != null && methodName.length() > 0) {
                                methodName = methodName.substring(0, 1).toLowerCase() + "" + methodName.substring(1);
                            }

                            if (fieldType.equals("java.lang.Byte")
                                    || fieldType.equals("java.lang.Object")) {
                                Object objVal = ReflectionUtil.getGetterMethodValue(getter, obj);
                                sb.append("\"").append(methodName).append("\"").append(":").append("\"").append(objVal.toString()).append("\"");
                            } else {
                                sb.append("\"").append(methodName).append("\"").append(":").append("\"").append(
                                		ReflectionUtil.getGetterMethodValueToString(getter, obj)).append("\"");
                            }
                        }
                        sb.append("}");
                    }
                }
                sb.append("]").append("}");
                value = sb.toString().trim();
            }
        }
        return value.trim();
    }

    public static Field[] getBeanFields(final Class<?> aClass) {
        String key = aClass.getCanonicalName();
    	Field[] fields = indexFieldMap.get(key);

    	if (fields != null) {
    		return fields;
    	}

    	fields = aClass.getFields();
    	indexFieldMap.put(key, fields);

    	return fields;
    }

    public static List<Method> getGetterMethods(final Class<?> aClass) {
        String key = aClass.getCanonicalName();
        List<Method> getterMethods = indexGetterMethodMap.get(key);

        // return the cached getter methods
        if (getterMethods != null) {
        	return getterMethods;
        }

        // initialize and cache
        getterMethods = new ArrayList<Method>();
        indexGetterMethodMap.put(key, getterMethods);

        final Method[] methods = aClass.getMethods();

        for (final Method method : methods) {
            if (isGetter(method)) {
                getterMethods.add(method);
            }
        }

        // return getter methods
        return getterMethods;
    }
    
    public static Method getGetterMethodFromGetterName(final Class<?> aClass, String getterName) {
        getterName = getGetterFieldName(getterName).toLowerCase();
        String key = aClass.getCanonicalName() + ":" + getterName;
        
        Method m = getterNameToGetterMethodMap.get(key);
        if ( m != null ) {
            return m;
        }
        
        final Method[] methods = aClass.getMethods();
        
        for (final Method method : methods) {
            
            if (isGetter(method)) {
                String getterMethodFieldName = getGetterFieldName(method);
                if ( getterName.equals( getterMethodFieldName ) ) {
                    getterNameToGetterMethodMap.put(key, method);
                    return method;
                }
            }
        }
        return null;
    }

    public static Method getGetterMethodFromField(final Class<?> aClass, Field field) {
        String getterName = getMethodGetterFromField(field);
        
        return getGetterMethodFromGetterName(aClass, getterName);
    }

    public static List<Method> getSetterMethods(final Class<?> aClass) {
        String key = aClass.getCanonicalName();
    	List<Method> setterMethods = indexSetterMethodMap.get(key);
    	if (setterMethods != null) {
    		return setterMethods;
    	}

    	setterMethods = new ArrayList<Method>();
    	indexSetterMethodMap.put(key, setterMethods);

        final Method[] methods = aClass.getMethods();

        for (final Method method : methods) {
            if (isSetter(method)) {
                setterMethods.add(method);
            }
        }

        return setterMethods;
    }

    public static boolean isGetter(final Method method) {
        if (!method.getName().startsWith("get") && !method.getName().startsWith("is") && !method.getName().startsWith("use"))
            return false;
        if (method.getParameterTypes().length != 0)
            return false;
        if (void.class.equals(method.getReturnType()))
            return false;
        return true;
    }

    public static boolean isSetter(final Method method) {
        if (!method.getName().startsWith("set"))
            return false;
        if (method.getParameterTypes().length != 1)
            return false;
        return true;
    }

    public static String getMethodGetterFromField(Field field) {
        String name = field.getName();
        String suffix = name.substring(0, 1).toUpperCase() + name.substring(1);
        if (field.getType().getSimpleName().equalsIgnoreCase("boolean") && !name.startsWith("use") && !name.startsWith("is")) {
            return "is" + suffix;
        } else {
            return "get" + suffix;
        }
    }

    public static String getGetterFieldName(Method getter) {
        return getGetterFieldName(getter.getName());
    }

    /**
     * Create the field name for the index doc (a getter starting with a 'get' or 'is' or 'use')
     * @param getterName
     * @return
     */
    public static String getGetterFieldName(String getterName) {
        // lowercase the getter name. this will return a lowercase'd field name
        getterName = getterName.toLowerCase();
    	String fieldName = getterMethodNameToPropertyNameMap.get(getterName);
    	if (fieldName != null) {
    		return fieldName;
    	}

        // strip out the the 'get', 'use', or 'is'
        fieldName = (getterName.startsWith("get") || getterName.startsWith("use")) ?
                getterName.substring(3, getterName.length()) :
                    getterName.substring(2, getterName.length());

        // lowercase the 1st char
        if (fieldName != null && fieldName.length() > 0) {
            fieldName = fieldName.substring(0, 1).toLowerCase() + "" + fieldName.substring(1);
        }
        getterMethodNameToPropertyNameMap.put(getterName, fieldName);

        return fieldName;
    }
    
    public static Method getSetterMethodFromFieldName(final Class<?> aClass, String setterName) {
        setterName = setterName.toLowerCase();
        String key = aClass.getCanonicalName() + ":" + setterName;
        
        Method setterMethod = fieldNameToSetterMethodMap.get(key);
        if (setterMethod != null) {
            return setterMethod;
        }

        List<Method> setterMethods = getSetterMethods(aClass);

        for (Method m : setterMethods) {
            String fieldNameFromSetter  = getFieldNameFromSetter(m);
            if ( fieldNameFromSetter != null ) {
                fieldNameFromSetter = fieldNameFromSetter.toLowerCase();
                if (  fieldNameFromSetter.equalsIgnoreCase( setterName ) ) {
                    fieldNameToSetterMethodMap.put(key, m);
                    return m;
                }
            }
        }
        return null;
    }

    public static Method getSetterMethodFromSetterName(final Class<?> aClass, String setterName) {
        
        setterName = setterName.toLowerCase();
        String key = aClass.getCanonicalName() + ":" + setterName;
        
    	Method setterMethod = fieldNameToSetterMethodMap.get(key);
    	if (setterMethod != null) {
    		return setterMethod;
    	}

    	List<Method> setterMethods = getSetterMethods(aClass);

    	for (Method m : setterMethods) {
    	    String methodSetterName = m.getName().toLowerCase();
    		if (  methodSetterName.equalsIgnoreCase( setterName ) ) {
    			fieldNameToSetterMethodMap.put(key, m);
    			return m;
    		}
    	}
    	return null;
    }

    public static String getFieldNameFromSetter(Method setter) {
    	String setterName = setter.getName().toLowerCase();
    	String fieldName = setterMethodNameToPropertyNameMap.get(setterName);
    	if (fieldName != null) {
    		return fieldName;
    	}

        // strip out the the 'set'
        fieldName = setterName.substring(3, setterName.length());

        // lowercase the 1st char
        if (fieldName != null && fieldName.length() > 0) {
            fieldName = fieldName.substring(0, 1).toLowerCase() + "" + fieldName.substring(1);
        }
        setterMethodNameToPropertyNameMap.put(setterName, fieldName);

        return fieldName;
    }

    public static boolean isClassOfBase(Class<?> childClass, Class<?> baseClass) {
        Class<?> superClass = childClass.getSuperclass();
        String superClassName = (superClass != null) ? superClass.getName() : "";
        if (superClass != null && !superClassName.equalsIgnoreCase("java.lang.Object")) {
            if (superClass.isAssignableFrom(baseClass)) {
                superClass = superClass.getSuperclass();
                return true;
            }
            return isClassOfBase(superClass, baseClass);
        }
        return false;
    }

}
