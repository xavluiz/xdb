/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.baddata.util.AppConstants;

public class ProfileBackgroundFileUploaderServlet extends UploaderServletBase {

	private static final long serialVersionUID = 1L;

	@Override
    protected void doPost( HttpServletRequest req, HttpServletResponse resp ) throws ServletException, IOException {
		this.postNewUserImage(req, resp, AppConstants.PROFILE_BACKGROUND_FILE_TOKEN);
    }
}
