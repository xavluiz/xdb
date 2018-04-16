/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;

import com.baddata.api.config.BigDecimalJsonDeserializer;
import com.baddata.api.config.DateTimeJsonDeserializer;
import com.baddata.api.config.JsonTypedBeanDeserializer;
import com.baddata.api.config.ZonedDateTimeJsonDeserializer;
import com.baddata.api.dto.BaddataError;
import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.user.User;
import com.baddata.api.query.SearchSpec;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.baddata.util.JsonRepresentation;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * The RestApiBase class gives access to the source resource API's.  It has methods
 * like putIt, getIt, postIt, deleteIt, logout, login, signup,
 * 
 * It will also authenticate the default user.  To test invalid session, please call
 * the "logout()" from your API test.
 *
 * This extends TestBase which has access to the search, index, and user service.
 */
public class RestApiBase extends TestBase {
	
	protected static JerseyTestClient client;
	protected static WebResource resource;
	protected static User adminUser;
	protected static String authToken = null;
	
	protected boolean preventAutoSignup = false;
	
	protected static Gson gson;
	
	public enum MethodType {
		POST,
		PUT,
		GET,
		DELETE;
	}

	@Before
	public void setUp() throws Exception {
		// ensure the TestBase sets things up
		super.setUp();
		
		//
		// Start the JerseyTest client
		//
		if ( client == null ) {
			System.out.println("--- BUILDING JERSEY TEST CLIENT ---");
			client = new JerseyTestClient();
			resource = client.resource();
			resource.getUriBuilder().scheme("https");
			System.out.println("scheme: " + resource.getURI().getScheme());
			resource.accept(MediaType.APPLICATION_JSON_TYPE);
			resource.header(HttpHeaders.AUTHORIZATION, authToken);
			resource.setProperty("Content-Type", "application/json;charset=UTF-8");
		}
		
		if ( gson == null ) {
		    GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter( DateTime.class, new DateTimeJsonDeserializer() );
            gsonBuilder.registerTypeAdapter( BigDecimal.class, new BigDecimalJsonDeserializer() );
            gsonBuilder.registerTypeAdapter( TypedObject.class, new JsonTypedBeanDeserializer<TypedObject>() );
            gsonBuilder.registerTypeAdapter( ZonedDateTime.class, new ZonedDateTimeJsonDeserializer() );
            gson = gsonBuilder.create();
		}
		
		preventAutoSignup = false;
		
		adminUser = null;
		this.loginAdminUser();
		
	}
	
	@After
	public void tearDown() {
		// ensure the TestBase tears things down
		super.tearDown();
	}
	
	protected void logout() throws Exception {
		// post logout
		this.jerseyTestClientPostIt("session", "logout");
		adminUser = null;
		authToken = null;
	}
	
	private User buildAdminUser() {
	    User user = new User();
	    user.setPassword( AppUtil.get(AppConstants.ADMIN1_PASSWORD) );
	    user.setUsername( AppUtil.get(AppConstants.ADMIN1_USERNAME) );
	    user.setEmail( AppUtil.get(AppConstants.ADMIN1_EMAIL) );
	    
	    return user;
	}
	
	public User loginAdminUser() {
	    if (adminUser == null) {
	        adminUser = (User) this.jerseyTestClientPostItToObject(User.class, this.buildAdminUser(), "session", "login");
	        authToken = adminUser.getAuthToken();
	    }
	    return adminUser;
	}
	
	protected Long getUserId() {
	    return this.loginAdminUser().getId();
	}
	
	protected String getTentantId() {
		return this.loginAdminUser().getUsername() + "_" + this.loginAdminUser().getId();
	}
	
	protected User createAndSignupRandomUser() throws Exception {
        String username = System.currentTimeMillis() + "_user";
        User user = new User();
        user.setFullname("random user");
        
        user.setPassword("IronAde3131!");
        user.setUsername(username);
        
        // signup
        Response resp = this.jerseyTestClientPostIt(user, "user", "signup");
        
        user = (User) convertToTypedObjectFromJsonObject(resp.getEntity(), User.class);
        
        if (!preventAutoSignup) {
            resp = this.jerseyTestClientPostIt(user, "session", "login");
            user = (User) convertToTypedObjectFromJsonObject(resp.getEntity(), User.class);
        }
        
        return user;
    }
	
	protected Object convertToTypedObjectFromJsonObject(Object jsonObj, Class<? extends TypedObject> classOfT) {
	    return this.convertToTypeOfObject( (String)jsonObj, classOfT );
	}
	
	protected Object convertToTypeOfObjectFromJsonObject(Object entity, Class<?> classOfT) {
	    if (entity instanceof String) {
	        return this.convertToTypeOfObject( (String)entity, classOfT);
	    } else if (entity instanceof BaddataError) {
	        BaddataError err = (BaddataError) entity;
	        System.out.print("Error: " + err.toString());
	    }
	    return null;
	}
	
	protected Object convertToTypeOfObject(String json, Class<?> classOfT) {
	    if ( json.toLowerCase().indexOf("no content") != -1 && json.indexOf("204") != -1 ) {
	        return null;
	    }
		return gson.fromJson(json, classOfT);
	}
	
	protected User updateUser(User user) throws Exception {

		Response resp = this.jerseyTestClientPutIt(user, "user");
		
		if ( resp.getStatus() != Response.Status.NO_CONTENT.getStatusCode() ) {
			throw new Exception("Failed to update user, reason: " + resp.toString());
		}
		
		user = (User) this.getItTypedObject(User.class, "user", String.valueOf(user.getId()));
		
		return user;
	}
	
	protected TypedObject getItTypedObject(Class<? extends TypedObject> typedClass, String... pathParts) throws Exception {
	    MultivaluedMap<String, String> params = this.getParamsFromLastPart(pathParts);
        if ( params != null ) {
            // remove the query String from the last path part
            pathParts = this.removeQueryStringFromLastPathPart(pathParts);
        }
		String path = this.buildUri(pathParts);
		
		TypedObject obj = null;
		
		if ( params == null ) {
		    obj = (TypedObject) resource.path( path ).accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON ).header(HttpHeaders.AUTHORIZATION, authToken).get(typedClass);
		} else {
		    obj = (TypedObject) resource.path( path ).queryParams(params).accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON ).header(HttpHeaders.AUTHORIZATION, authToken).get(typedClass);
		}
		return obj;
	}
	
	/**
	 * This will perform the GET request and return the result as a JSON string.  Along with
	 * the path parts, you can send in the query string (i.e. ?key=valu&key2=value2) as the last
	 * path part.  It will extrapolate it.
	 * 
	 * @param pathParts
	 * @return
	 * @throws Exception
	 */
	protected String jerseyTestClientGetIt(String... pathParts) throws Exception {
	    MultivaluedMap<String, String> params = this.getParamsFromLastPart(pathParts);
	    if ( params != null ) {
	        // remove the query String from the last path part
	        pathParts = this.removeQueryStringFromLastPathPart(pathParts);
	    }
		String path = this.buildUri(pathParts);
		
		String str = null;
		try {
    		if ( params == null ) {
    		    str = resource.path( path ).accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON ).header(HttpHeaders.AUTHORIZATION, authToken).get(String.class);
    		} else {
    		    str = resource.path( path ).queryParams(params).accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON ).header(HttpHeaders.AUTHORIZATION, authToken).get(String.class);
    		}
		} catch (Exception e) {
		    return e.getMessage();
		}
		return str;
	}
	
	protected Object jerseyTestClientGetItToListType(Class<?> listClassOfT, String...pathParts) throws Exception {
	    List<?> list = (List<?>) this.jerseyTestClientGetItToObject(List.class, pathParts);
	    List<Object> returnList = Lists.newArrayList();
        for (Object userInfoObj : list) {

            JsonObject jsonObject = gson.toJsonTree(userInfoObj).getAsJsonObject();

            Object obj = gson.fromJson(jsonObject.toString(), listClassOfT);
            returnList.add(obj);
        }
        return returnList;
	}
	
	protected Object jerseyTestClientGetItToObject(Class<?> classOfT, String... pathParts) throws Exception {
		String result = this.jerseyTestClientGetIt(pathParts);
		return this.convertToTypeOfObject(result, classOfT);
	}
	
	protected String[] removeQueryStringFromLastPathPart(String... pathParts) {
	    if ( pathParts == null ) {
	        return null;
	    }
	    
	    String tmp = pathParts[pathParts.length - 1];
	    
	    pathParts[pathParts.length - 1] = tmp.split("\\?")[0];
	    
	    return pathParts;
	}
	
	protected MultivaluedMap<String, String> getParamsFromLastPart(String... pathParts) {
	    MultivaluedMap<String, String> params = null;
	    
	    if ( pathParts != null && pathParts.length > 0 ) {
	        String lastPathPart = pathParts[pathParts.length - 1];
	        if ( lastPathPart.indexOf("?") != -1 ) {
	            String[] lastPathPartParts = lastPathPart.split("\\?");
	            if ( lastPathPartParts != null && lastPathPartParts.length == 2 ) {
	                String queryStr = lastPathPartParts[1];
	                params = new MultivaluedMapImpl();
	                if ( queryStr.indexOf("&") != -1 ) {
	                    // split the multiple key=value pairs
	                    String[] queryStrParts = queryStr.split("&");
	                    if ( queryStrParts != null && queryStrParts.length > 0 ) {
	                        for ( String queryStrPart : queryStrParts ) {
	                            if ( queryStrPart != null && queryStrPart.indexOf("=") != -1 ) {
	                                String[] keyValueParts = queryStrPart.split("=");
	                                if ( keyValueParts != null && keyValueParts.length == 2 ) {
	                                    params.add(keyValueParts[0].trim(), keyValueParts[1].trim());
	                                }
	                            }
	                        }
	                    }
	                } else {
	                    // there's only one key=value
	                    String[] keyValueParts = queryStr.split("=");
                        if ( keyValueParts != null && keyValueParts.length == 2 ) {
                            params.add(keyValueParts[0].trim(), keyValueParts[1].trim());
                        }
	                }
	            }
	        }
	    }
	    
	    return params;
	}
	
	protected Page getItPage(SearchSpec searchSpec, String... pathParts) throws Exception {
	    MultivaluedMap<String, String> params = searchSpec.toMultivaluedMap();
		
	    String path = this.buildUri(pathParts);
		
		System.out.println("get page using uri: " + path);
		
        ClientResponse resp = resource.path( path )
		        .queryParams( params )
		        .accept( MediaType.APPLICATION_JSON )
		        .type( MediaType.APPLICATION_JSON )
		        .header(HttpHeaders.AUTHORIZATION, authToken)
		        .get(ClientResponse.class);
        
        String jsonResponse = this.getStringRepresentation( resp.getEntityInputStream() );
        Page p = (Page) this.convertToTypeOfObject(jsonResponse, Page.class);
        
		return p;
	}
	
	protected String getStringRepresentation(InputStream res) throws IOException {
	    if ( res != null ) {
	        StringBuffer sb = new StringBuffer();
	        BufferedReader br = new BufferedReader( new InputStreamReader( res ) );
	        while (true) {
	            String line = br.readLine();
	            if ( line != null ) {
	                sb.append(line);
	            } else {
	                break;
	            }
	        }
	        br.close();
	        return sb.toString();
	    }
	    return null;
	}
	
	
	//
	// JerseyTestClient methods
	//
	protected Response jerseyTestClientDeleteIt(String... pathParts) throws Exception {
		return this.jerseyTestClientPostOrPutOrDeleteIt(MethodType.DELETE, null /* payload */, pathParts);
	}
	
	protected Response jerseyTestClientPutIt(String... pathParts) throws Exception {
		return this.jerseyTestClientPostOrPutOrDeleteIt(MethodType.PUT, null /* payload */, pathParts);
	}
	
	protected Response jerseyTestClientPutIt(TypedObject payload, String... pathParts) throws Exception {
		return this.jerseyTestClientPostOrPutOrDeleteIt(MethodType.PUT, payload, pathParts);
	}
	
	protected Response jerseyTestClientPostIt(String... pathParts) throws Exception {
		return this.jerseyTestClientPostOrPutOrDeleteIt(MethodType.POST, null /* payload */, pathParts);
	}
	
	protected Response jerseyTestClientPostIt(TypedObject payload, String... pathParts) throws Exception {
		return this.jerseyTestClientPostOrPutOrDeleteIt(MethodType.POST, payload, pathParts);
	}
	
	protected Object jerseyTestClientPostItToObject(Class<?> classOfT, TypedObject payload, String... pathParts) {
	    try {
	        Response response = this.jerseyTestClientPostOrPutOrDeleteIt(MethodType.POST, payload, classOfT, pathParts);
	        // OutboundJaxrsResponse obj = (OutboundJaxrsResponse) this.jerseyTestClientPostOrPutOrDeleteIt(MethodType.POST, payload, classOfT, pathParts);
            return this.convertToTypeOfObject(response.getEntity().toString().toString(), classOfT);
        } catch (Exception e) {
            return null;
        }
    }
	
	protected Response jerseyTestClientPostOrPutOrDeleteIt(MethodType methodType, TypedObject payload, String... pathParts) throws Exception {
	    return this.jerseyTestClientPostOrPutOrDeleteIt(methodType, payload, String.class, pathParts);
	}
	
	protected Response jerseyTestClientPostOrPutOrDeleteIt(MethodType methodType, TypedObject payload, Class<?> classOfT, String... pathParts) throws Exception {
		
		String path = this.buildUri(pathParts);
		
		String jsonStr = null;
		if ( payload != null ) {
			jsonStr = JsonRepresentation.getJsonRepresentation(payload, payload.getClass());
		}
		
		String httpResp = null;
		try {
		    
			if ( jsonStr != null ) {
				if ( methodType == MethodType.POST ) {
					httpResp = resource.path( path ).accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON ).header(HttpHeaders.AUTHORIZATION, authToken).post(String.class, jsonStr);
				} else if ( methodType == MethodType.PUT ) {
					httpResp = resource.path( path ).accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON ).header(HttpHeaders.AUTHORIZATION, authToken).put( String.class, jsonStr );
				} else if ( methodType == MethodType.DELETE ) {
					httpResp = resource.path( path ).accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON ).header(HttpHeaders.AUTHORIZATION, authToken).delete( String.class, jsonStr );
				} else {
					throw new Exception("postOrPutOrDeleteIt used only for POST, PUT, or DELETE");
				}
			} else {
				if ( methodType == MethodType.POST ) {
					httpResp = resource.path( path ).accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON ).header(HttpHeaders.AUTHORIZATION, authToken).post( String.class );
				} else if ( methodType == MethodType.PUT ) {
					httpResp = resource.path( path ).accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON ).header(HttpHeaders.AUTHORIZATION, authToken).put( String.class );
				} else if ( methodType == MethodType.DELETE ) {
					httpResp = resource.path( path ).accept( MediaType.APPLICATION_JSON ).type( MediaType.APPLICATION_JSON ).header(HttpHeaders.AUTHORIZATION, authToken).delete( String.class );
				} else {
					throw new Exception("postOrPutOrDeleteIt used only for POST, PUT, or DELETE");
				}
			}
			
		} catch ( Exception e ) {
			if ( e instanceof UniformInterfaceException ) {
				
				// get the URLConnectionClientHandler$URLConnectionResponse from e.r, it's a ByteArrayInputStream
				
				BaddataError err = this.getBaddataErrorFromException(e);
				Status status = this.getStatusFromException(e);
				
				return Response.status(status).entity(err).build();
			}
			
			//
			// rebuild a Response with an internal server error and the exception
			//
			return Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode()).entity(e.toString()).build();
		}
		
		//
		// return ok
		//
		return Response.status(Status.OK.getStatusCode()).entity(httpResp).build();
	}
	
	protected BaddataError getBaddataErrorFromException(Exception e) {
	    if ( e instanceof UniformInterfaceException ) {
            //
            // rebuild a Response with the true status
            //
            UniformInterfaceException uie = (UniformInterfaceException)e;
            
            // get the URLConnectionClientHandler$URLConnectionResponse from e.r, it's a ByteArrayInputStream
            
            BaddataError err = new BaddataError(uie.getMessage(), "" /* code */, e.toString() /* causeDetails */);
            return err;
	    }
	    return null;
	}
	
	protected Status getStatusFromException(Exception e) {
	    Status status = null;
	    
	    if ( e instanceof UniformInterfaceException ) {
	        UniformInterfaceException uie = (UniformInterfaceException)e;
	        int respStatus = uie.getResponse().getStatus();
	        try {
	            status = Status.fromStatusCode( respStatus );
	        } catch (Exception e1) {
	            e.printStackTrace();
	        }
        }
	    return status;
	}
	
	private String buildUri(String... pathParts) {
		List<String> uriParts = new ArrayList<String>();
		if ( pathParts != null ) {
			for ( String pathPart : pathParts ) {
				uriParts.add(pathPart);
			}
		}
		
		return Joiner.on("/").join(uriParts);
	}

}
