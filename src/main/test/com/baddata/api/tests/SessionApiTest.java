/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.tests;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import com.baddata.RestApiBase;
import com.baddata.TestBase;
import com.baddata.api.dto.BaddataError;
import com.baddata.api.dto.user.User;
import com.baddata.util.AlgoUtil;
import com.baddata.util.FBUserUtil;
import com.baddata.util.FBUserUtil.DecodedSignedRequest;


public class SessionApiTest extends RestApiBase {
	
	@Test
	public void loginTest() throws Exception {
		
		// make sure to logout to pass this test
		this.logout();
		User user = new User();
		user.setPassword(TestBase.DEFAULT_USER_PASSWORD);
		user.setUsername(TestBase.DEFAULT_USERNAME);
		user.setEmail(TestBase.DEFAULT_USERNAME);
		
		// signup
        Response resp = this.jerseyTestClientPostIt(user, "user", "signup");
        
        user = (User) convertToTypedObjectFromJsonObject(resp.getEntity(), User.class);
        user.setPassword(TestBase.DEFAULT_USER_PASSWORD);
		
		resp = this.jerseyTestClientPostIt(user, "session", "login");
		
		user = (User) this.convertToTypedObjectFromJsonObject( resp.getEntity(), User.class );
		Assert.assertNotNull(user);
        Assert.assertEquals(true, user.getId() != null);
        Assert.assertEquals(true, user.getCreateTime() != null);
        Assert.assertEquals(Status.OK.getStatusCode(), resp.getStatus());
		
		// test logging in with an encrypted password
        user.setPassword( AlgoUtil.encryptAES(user.getPassword()) );
		resp = this.jerseyTestClientPostIt(user, "session", "login");
        
		// should return an error
		BaddataError err = (BaddataError) resp.getEntity();
		Assert.assertNotNull(err);
	}
	
	@Test
	public void logoutTest() throws Exception {
		// access something that requires a valid session
		
		User user = (User) this.getItTypedObject(User.class, "user");
		
		Assert.assertNotNull(user);
		
		// logout
		this.logout();
		
		//
		// try some private APIs
		//
		try {
			user = (User) this.getItTypedObject(User.class, "user");
		} catch (Exception e) {
		    BaddataError err = this.getBaddataErrorFromException(e);
		    Status status = this.getStatusFromException(e);
		    
		    Assert.assertEquals(Status.UNAUTHORIZED, status);
		    
		    Assert.assertEquals(true, StringUtils.isNotBlank(err.getMessage()));
		    
		    System.out.println("err message: " + err.getMessage());
		}
		
		String sessionCheckVal = this.jerseyTestClientGetIt("session", "validate");
		Assert.assertEquals(true, sessionCheckVal.toLowerCase().indexOf("unauthorized") != -1);
		
	}
	
	@Test
	public void fbLoginTest() {
	    // this is a real "signedRequest" string
	    // when comparing to the real "secret" that is set in the system properties within the RestApiBase, it
	    // will be able to truly test signatures
	    String signedRequestStr = "xqkb_yVSP1W4Kh65RDk-eFt37l-voV6O9rPXbXCLU3I.eyJhbGdvcml0aG0iOiJITUFDLVNIQTI1NiIsImNvZGUiOiJBUUR4YXp1eVMtdk9MRGdRR0RibFNvbWtuc3dsNy0tR1VnZFZwd3ZqRGJaNEVZOXVKWnlUb25ybGl4ZExnZnp6LWdIbzBzUURGN3V4bjJEUHFBOUFDdDlNU2JfT3ZTdU9oYS11NlZLLW5DcW9mMkhTNnVHaEc3VVpSSm9YTmdMbkZCaU9NNGxPclZDNmJhQVNjdHNPV2tjSmI3SEpNSWtHSE8wY0VmMzdKVXdlNjg3VEJvR2psZDVBa01VcThCN0hlOWJ5ZXBlNVVaMU9UMllTaWpZeTFnbklNRTlkOFFFSDZKR09ib3FBelg3aEtCaTZQM1FSMkJTTzJhdjctNk1kaUQ5UVJKSzJBQUFwNlUyWDY5TzVycllaNXBUUlRabWpMUk9TRWlJQkxaMm85NENuZThpN0k0QnNXeS1EMHJjelhPRGlfVlBURWdpVTAxQnFvT0txUkw2YyIsImlzc3VlZF9hdCI6MTQzNzc1Nzc2MSwidXNlcl9pZCI6IjEwMjA2MTYzNzQ0MDI2NzMwIn0";
	
	    User auth = new User();
	    auth.setUsername("123");
	    auth.setPassword(signedRequestStr);
	    
	    DecodedSignedRequest decodedSignedReq = FBUserUtil.isValidFBConnectionAuth(auth);
	    Assert.assertEquals(true, decodedSignedReq != null);
	}
}
