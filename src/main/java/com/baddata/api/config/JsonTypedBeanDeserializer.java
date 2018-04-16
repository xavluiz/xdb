package com.baddata.api.config;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import com.baddata.api.dto.TypedObject;
import com.baddata.util.AppUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class JsonTypedBeanDeserializer<T> implements JsonDeserializer<TypedObject> {
    
    private static Gson gson;
    private static Map<String, Class<?>> typedObjMap = new HashMap<String, Class<?>>();

    @Override
    public TypedObject deserialize(JsonElement element, Type type,
            JsonDeserializationContext ctx) throws JsonParseException {
        
        if ( element == null ) {
            return null;
        }
        
        if ( gson == null ) {
            GsonBuilder builder = new GsonBuilder();
            builder.registerTypeAdapter( BigDecimal.class, new BigDecimalJsonDeserializer() );
            builder.registerTypeAdapter( DateTime.class, new DateTimeJsonDeserializer() );
            builder.registerTypeAdapter( ZonedDateTime.class, new ZonedDateTimeJsonDeserializer() );
            gson = builder.create();
        }
        
        
        JsonObject jsonObj = element.getAsJsonObject();
        
        Class<?> classType = this.getClassTypeFromJsonObject(jsonObj);
        
        Object returnObj = null;
        try {
            returnObj = gson.fromJson(element, classType);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return (returnObj != null) ? (TypedObject) returnObj : null;
        
    }
    
    /**
     * Figure out what class type the json object respresents.
     * 
     * @param jsonObj
     * @return
     */
    private Class<?> getClassTypeFromJsonObject(JsonObject jsonObj) {
        
        String classId = jsonObj.get("typeid").getAsString();
        
        Class<?> classType = typedObjMap.get( classId );
        if ( classType == null ) {
            
            List<Class<? extends TypedObject>> typedObjClasses = AppUtil.getDTOClasses();
            
            if ( typedObjClasses != null ) {
                for ( Class<? extends TypedObject> clazz : typedObjClasses ) {
                    String simpleName = clazz.getSimpleName();
                    if ( simpleName.equalsIgnoreCase( classId ) ) {
                        classType = clazz;
                        typedObjMap.put(classId, classType);
                        break;
                    }
                }
            }
        }
        
        return classType;
    }

}
