/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.tests;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.junit.Test;

import com.baddata.RestApiBase;
import com.baddata.api.dto.user.User;

import junit.framework.Assert;

public class UserApiTest extends RestApiBase {
	
	@Test
	public void signupTest() throws Exception {
		// logout first, this is a public api
		this.logout();
		
		this.preventAutoSignup = true;
		
		String userName = System.currentTimeMillis() + "@baddata.com";
		String password = "IronAde3131!";
		
		DateTime preCreateTime = DateTime.now();
		
		User user = new User();
		user.setUsername(userName);
		user.setPassword(password);
		
		Response resp = this.jerseyTestClientPostIt(user, "user", "signup");
		
		DateTime postCreateTime = DateTime.now();
		
		// returns a UserProfile dto
		user = (User) this.convertToTypedObjectFromJsonObject( resp.getEntity(), User.class );
		Assert.assertNotNull(user);
		Assert.assertEquals(true, user.getId() != null);
		Assert.assertEquals(true, user.getCreateTime() != null);
		
		Assert.assertEquals(true, preCreateTime.getMillis() < user.getCreateTime().getMillis());
		Assert.assertEquals(true, postCreateTime.getMillis() > user.getCreateTime().getMillis());
		

		Assert.assertEquals(user.getCreateTime(), user.getUpdateTime());
		Assert.assertEquals(Status.OK.getStatusCode(), resp.getStatus());
	}

	@Test
	public void getUserTest() throws Exception {
		User defaultUser = (User) this.getItTypedObject(User.class, "user");
		
		Assert.assertNotNull(defaultUser);
		Assert.assertEquals(defaultUser.getId(), adminUser.getId());
	}
	
}
