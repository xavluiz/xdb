/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.factory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.reflections.Reflections;

import com.baddata.annotation.ApiInfo;
import com.baddata.api.dto.BaddataError;
import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.UploadResponse;
import com.baddata.api.dto.user.User;
import com.baddata.api.query.SearchSpec;
import com.baddata.broker.AuditLogBrokerImpl;
import com.baddata.broker.CurrencyBrokerImpl;
import com.baddata.broker.DatabaseBrokerImpl;
import com.baddata.broker.EventBrokerImpl;
import com.baddata.broker.JobBrokerImpl;
import com.baddata.broker.SalesforceBrokerImpl;
import com.baddata.broker.SessionBrokerImpl;
import com.baddata.broker.UserBrokerImpl;
import com.baddata.exception.ApiServiceException;
import com.baddata.log.EventLogger.ApiErrorCode;
import com.baddata.log.Logger;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gson.Gson;

public abstract class ResourceBase {
    
    private static Logger logger = Logger.getLogger(ResourceBase.class.getName());
	
	private static Set<Class<? extends ResourceBase>> resourceClassSet = null;
	private static Set<Class<? extends TypedObject>> typedObjClassSet = null;
	private static Set<String> publicRestApiPaths = null;
	private static Map<String, String> requiredUberUserMethodsToPaths = null;
	
	private EventBrokerImpl eventBroker = null;
	private AuditLogBrokerImpl logBroker = null;
	private JobBrokerImpl jobBroker = null;
	private SalesforceBrokerImpl salesforceBroker = null;
	private DatabaseBrokerImpl databaseBroker = null;
	private SessionBrokerImpl sessionBroker = null;
	private UserBrokerImpl userBroker = null;
	private CurrencyBrokerImpl currencyBroker = null;

    /**
     * URI infor object in the given context.
     */
    @Context
    protected UriInfo uriInfo;

    /**
     * Headers for the HTTP request in the given context.
     */
    @Context
    protected HttpHeaders headers;
    
    @Context
    protected HttpServletRequest request;
    
    @Context
    protected HttpServletResponse response;
    
    protected Long userReferenceId;
    protected String userName;
    protected SearchSpec searchSpec;
    
    protected void init() {
        if ( request == null ) {
            return;
        }
        
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if ( StringUtils.isBlank( authorizationHeader ) ) {
            authorizationHeader = request.getParameter(AppConstants.AUTH_TOKEN);
        }
        User user = ApiSessionContext.getUser(authorizationHeader);

        if ( user != null ) {
	        this.userReferenceId = user.getId();
	        this.userName = user.getUsername();
        }
        response.setCharacterEncoding("UTF-8");
    }
    
    protected void addAttachmentHeader(String attachmentFileName) {
        if ( !StringUtils.isBlank(attachmentFileName) ) {
            response.setHeader("Content-Disposition", "attachment; filename=\"" + attachmentFileName + "\"");            
        }        
    }
    
    protected void updateResponseContentType(String contentType) {
    		response.setContentType(contentType);
    }
    
    protected JobBrokerImpl getJobBroker() {
	    	this.init();
	    	if ( jobBroker == null ) {
	    		jobBroker = new JobBrokerImpl(userReferenceId, userName);
	    	}
	    	return jobBroker;
    }
    
    protected SalesforceBrokerImpl getSalesforceBroker() {
    		this.init();
        if ( salesforceBroker == null ) {
            salesforceBroker = new SalesforceBrokerImpl(userReferenceId, userName);
        }
        return salesforceBroker;
    }
    
    protected DatabaseBrokerImpl getDatabaseBroker() {
	    	this.init();
	    	if ( databaseBroker == null ) {
	    		databaseBroker = new DatabaseBrokerImpl(userReferenceId, userName);
	    	}
	    	return databaseBroker;
    }
    
    protected EventBrokerImpl getEventBroker() {
    		this.init();
        if ( eventBroker == null ) {
            eventBroker = new EventBrokerImpl(userReferenceId, userName);
        }
        return eventBroker;
    }
    
    protected CurrencyBrokerImpl getCurrencyBroker() {
    		this.init();
    		if ( currencyBroker == null ) {
    			currencyBroker = new CurrencyBrokerImpl(userReferenceId, userName);
    		}
    		return currencyBroker;
    }
    
    protected AuditLogBrokerImpl getLogBroker() {
        this.init();
        if ( logBroker == null ) {
            logBroker = new AuditLogBrokerImpl(userReferenceId, userName);
        }
        return logBroker;
    }
    
    protected SessionBrokerImpl getSessionBroker() {
    	this.init();
        if ( sessionBroker == null ) {
            sessionBroker = new SessionBrokerImpl(userReferenceId, userName);
        }
        return sessionBroker;
    }
    
    protected UserBrokerImpl getUserBroker() {
    	this.init();
        if ( userBroker == null ) {
            userBroker = new UserBrokerImpl(userReferenceId, userName);
        }
        return userBroker;
    }
    
    /**
     * Builds the SearchSpec DTO from the incoming query string
     * @return
     */
    protected void buildRequestSearchSpec() throws ApiServiceException {
    	this.init();
    	searchSpec = new SearchSpec( userReferenceId );
        searchSpec.parseQueryString( uriInfo );
    }
    
    protected MultivaluedMap<String, String> getQueryParamsMap() {
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        return queryParams;
    }
    
    public ZonedDateTime getZonedDateTimeByName(UriInfo uriInfo, String name) {
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        
        ZonedDateTime zdt = null;
        
        for ( String key : queryParams.keySet() ) {
            
            String val = queryParams.getFirst(key);
            key = key.trim();
            
            if ( key.equalsIgnoreCase( name ) ) {
                
                zdt = ( Strings.isNullOrEmpty( val ) ) ? ZonedDateTime.now() : ZonedDateTime.parse( val );

            }
        }
        
        if ( zdt == null ) {
            zdt = ZonedDateTime.now();
        }

        return zdt;
    }
    
    /**
     * Return the API resource classes.  These will be set once and reused after that.
     * @return Set<Class<? extends ResourceBase>>
     */
    public synchronized static Set<Class<? extends ResourceBase>> scanAllResources() {

		if ( resourceClassSet == null ) {
			Reflections ref = new Reflections( AppConstants.RESOURCE_PKG );
			resourceClassSet = ref.getSubTypesOf(ResourceBase.class);
		}
	     
		return resourceClassSet;
	}
    
    /**
     * Return the API resource DTO beans.  These will be set once and reused after that.
     * @return Set<Class<? extends TypedObject>>
     */
    public synchronized static Set<Class<? extends TypedObject>> scanAllTypedObjects() {

		if ( typedObjClassSet == null ) {
			typedObjClassSet = AppUtil.getDTOClassSet();
		}
	     
		return typedObjClassSet;
	}
    
    /**
     * Return the API resource paths.  These will be set once and reused after that.
     * i.e. ["/session/login", "/user", ...]
     * 
     * @return Set<String>
     */
    public synchronized static Set<String> getPublicApiPaths() {
    	
    	if ( publicRestApiPaths == null ) {
    		publicRestApiPaths = new HashSet<String>();
    		
    		Set<Class<? extends ResourceBase>> resourceClasses = scanAllResources();
    		
    		if ( resourceClasses != null ) {
    			for ( Class<? extends ResourceBase> clazz : resourceClasses ) {
    				buildAnnotatedPathsForClass( clazz );
    			}
    		}
    	}
    	
    	return publicRestApiPaths;
    }
    
    /**
     * Return the required Uber user API resource paths.  These will be set once and reused after that.
     * 
     * @return
     */
    public synchronized static Map<String, String> getRequiredUberUserPaths() {
        if ( requiredUberUserMethodsToPaths == null ) {
            requiredUberUserMethodsToPaths = Maps.newHashMap();
            
            Set<Class<? extends ResourceBase>> resourceClasses = scanAllResources();
            
            if ( resourceClasses != null ) {
                for ( Class<? extends ResourceBase> clazz : resourceClasses ) {
                    buildAnnotatedPathsForClass( clazz );
                }
            }
        }
        
        return requiredUberUserMethodsToPaths;
    }
    
    /**
     * Check if the java class is a RESTful bean or not
     *
     * @param cls
     * @return boolean is true if it's a RESTful bean, and false otherwise
     */
    private synchronized static void buildAnnotatedPathsForClass(Class<?> cls) {
        
        if ( publicRestApiPaths == null ) {
            publicRestApiPaths = new HashSet<String>();
        }
        
        if ( requiredUberUserMethodsToPaths == null ) {
            requiredUberUserMethodsToPaths = Maps.newHashMap();
        }
        
        String classRootPath = "";
        
        // get the resource path
        Annotation[] classAnnotations = cls.getAnnotations();
        if ( classAnnotations != null ) {
        	//
        	// get the Path annotation
        	//
        	for ( Annotation annotation : classAnnotations ) {
        		if ( annotation instanceof Path ) {
        			// it's the path annotation, use this part in the final apiPath
        			classRootPath = ((Path)annotation).value();
        			break;
        		}
        	}
        }
        
        if ( !Strings.isNullOrEmpty(classRootPath) ) {
        	// ensure we don't have an ending slash
        	classRootPath = AppUtil.removeEndingChar(classRootPath, '/');
        	// ensure we have a slash at the beginning
        	classRootPath = (classRootPath.indexOf('/') != 0) ? "/" + classRootPath : classRootPath;
        }
        
        Method[] methods = cls.getMethods();
        if ( methods != null ) {
        	for ( Method method : methods ) {
        		Annotation[] annotations = method.getDeclaredAnnotations();
        		if ( annotations != null ) {
        			boolean isPublicPath = false;
        			boolean requiredUberUserPath = false;
        			String pathVal = null;
        			
        			//
        			// look for the ApiInfo annotation
        			//
        			String methodType = "GET";
        			for ( Annotation annotation : annotations ) {
        				if ( annotation instanceof ApiInfo ) {
        					
        					//
        					// found the ApiInfo annotation, is it public?
        					//
        					isPublicPath = ( (ApiInfo)annotation ).isPublicApi();
        					requiredUberUserPath = ( (ApiInfo)annotation ).requiresUberUserSesssion();
        				} else if ( annotation instanceof Path ) {
        					
        					//
        					// get the Path annotation
        					//
        					pathVal = AppUtil.removeBeginAndEndForwardSlash( ((Path)annotation).value() );
                    }

        				if (annotation.annotationType().getSimpleName().indexOf("PUT") != -1) {
        				    methodType = "PUT";
        				} else if (annotation.annotationType().getSimpleName().indexOf("DELETE") != -1) {
                        methodType = "DELETE";
                    } else if (annotation.annotationType().getSimpleName().indexOf("POST") != -1) {
                        methodType = "POST";
                    }
        			}
        			
        			// default to setting the apiPath to the root path in case pathVal is null
                    String apiPath = classRootPath;
        			
        			if ( isPublicPath ) {
        			    if ( pathVal != null ) {
        			        apiPath += "/" + pathVal;
        			    }
        			    publicRestApiPaths.add( apiPath );
        			}
        			if ( requiredUberUserPath ) {
        			    if ( pathVal != null ) {
                            apiPath += "/" + pathVal;
                        }
        			    requiredUberUserMethodsToPaths.put(methodType, apiPath);
        			}
        		}
        	}
        }

    }
    
    /**
     * Not implemented web application exception.
     * 
     * @return WebApplicationException
     */
    public static WebApplicationException notImplementedException() {
    	ResponseBuilder respBuilder = Response.status(Status.NOT_FOUND);
    	respBuilder.entity(null);
    	return new WebApplicationException(respBuilder.build());
    }
    
    public static WebApplicationException createWebApplicationException( ApiServiceException e, String errCode ) {
    	Status status = e.getMatchingStatus();
    	logger.error("API Error '" + errCode + "', Root Cause Message: " + ExceptionUtils.getRootCauseMessage(e), e);
    	return createWebApplicationException( status, e, errCode, e.getMessage() );
    }

    private static WebApplicationException createWebApplicationException( Status status, Throwable throwable, String errCode, String message ) {
    	ResponseBuilder respBuilder = Response.status(status);
    	
    	BaddataError errorBean = createErrorBean(message, errCode, throwable);
    	respBuilder.entity( errorBean );
    	
    	Response resp = respBuilder.build();
    	WebApplicationException webAppException = new WebApplicationException( resp );
    	
    	return webAppException;
    }
    
    public static BaddataError createErrorBean( String message, String errCode ) {
        BaddataError err = new BaddataError(message, errCode, "" /* causeDetails */);
        return err;
    }
    
    public static BaddataError createErrorBean( String message, String errCode, Throwable throwable ) {
    	String causeDetails = (throwable != null) ? throwable.getMessage() : ExceptionUtils.getMessage(throwable);
    	BaddataError err = new BaddataError(message, errCode, causeDetails);
    	return err;
    }
    
    public static void sendJsonErrorBeanResponse(
            Status respStatus,
            ServletResponse servletResponse,
            ApiErrorCode apiErrorCode,
            String message) throws IOException {

        // Create an BaddataError.  JSONize it and put it
        // in the body of the response with an error code of 200 so that flex can parse it.
        HttpServletResponse httpResp = (HttpServletResponse)servletResponse;
        httpResp.setContentType(MediaType.APPLICATION_JSON);
        httpResp.setStatus( respStatus.getStatusCode() );

        OutputStream ostr = httpResp.getOutputStream();
        
        BaddataError errorBean = ResourceBase.createErrorBean(message, apiErrorCode.getCode());
        String jsonStr = new Gson().toJson( errorBean );
        ostr.write( jsonStr.getBytes() );

        // Calling flush() on the ServletOutputStream commits the response.
        // Either this method or getWriter() may be called to write the body, not both.
        ostr.flush();

        // Forces any content in the buffer to be written to the client.
        // A call to this method automatically commits the response, meaning the status
        // code and headers will be written.
        httpResp.flushBuffer();
    }
    
    public static void sendJsonUploadSuccessBeanResponse( ServletResponse servletResponse, UploadResponse uploadResponse ) throws IOException {
        HttpServletResponse httpResp = (HttpServletResponse)servletResponse;
        httpResp.setContentType( MediaType.APPLICATION_JSON );
        httpResp.setStatus( Status.OK.getStatusCode() );
        
        OutputStream ostr = httpResp.getOutputStream();
        
        String jsonStr = new Gson().toJson(uploadResponse);
        ostr.write( jsonStr.getBytes() );
        
        ostr.flush();
        
        httpResp.flushBuffer();
    }
    
}
