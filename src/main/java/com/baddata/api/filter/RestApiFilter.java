/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved
 */
package com.baddata.api.filter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;

import com.baddata.api.dto.user.User;
import com.baddata.api.dto.user.User.RoleType;
import com.baddata.api.factory.ApiSessionContext;
import com.baddata.api.factory.ResourceBase;
import com.baddata.log.EventLogger.ApiErrorCode;
import com.baddata.log.Logger;
import com.baddata.manager.system.SystemManager;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;

public class RestApiFilter implements Filter {

	private static final Logger logger = Logger.getLogger(RestApiFilter.class.getName());

	private static Pattern tokenOnlyPattern = Pattern.compile("^(\\{.*\\})$");
	private static Pattern tokenPattern = Pattern.compile("(\\{.*\\})");

	private static SystemManager systemMgr = SystemManager.getInstance();

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// TODO Auto-generated method stub
	}

	@Override
	public void doFilter(
			ServletRequest servletRequest,
			ServletResponse servletResponse,
			FilterChain filterChain) throws IOException, ServletException {

		long elapsed = 0;
		String username = "";
		String method = "";
		String reqUrl = "";
		int status = 0;
		int bufferSize = 0;
		String error = "";

		try {
			HttpServletRequest httpReq = (HttpServletRequest) servletRequest;

			HttpServletResponse httpResp = (HttpServletResponse) servletResponse;

			// Get the HTTP Authorization header from the request
			String authorizationHeader = httpReq.getHeader(HttpHeaders.AUTHORIZATION);
			if ( StringUtils.isBlank( authorizationHeader ) ) {
				authorizationHeader = AppUtil.getSingleQueryParamValueByName(httpReq, AppConstants.AUTH_TOKEN);
			}
			User user = ApiSessionContext.getUser(authorizationHeader);
			boolean isLoggedIn = false;
			if (user != null) {
				isLoggedIn = true;
				username = user.getUsername();
			}

			if ( httpReq.getRequestURI().indexOf("download") == -1 ) {
				httpResp.setContentType("application/json;charset=UTF-8");
			}

			long start = System.currentTimeMillis();

			//
			// Log the request
			String qryStr = (httpReq.getQueryString() != null) ? "?" + httpReq.getQueryString() : "";

			//
			// Validate the qryStr to ensure it's not directory traversal
			// The system doesn't support "../" or "./"
			// anywhere in the key or value, so simply look for that
			// and bail if found.
			if ( StringUtils.isNoneBlank(qryStr) && ( qryStr.indexOf("..") != -1 || qryStr.indexOf("./") != -1 ) ) {
				error = Status.BAD_REQUEST + " : " + "Unsupported query string.";
				ResourceBase.sendJsonErrorBeanResponse(
						Status.BAD_REQUEST,
						servletResponse,
						ApiErrorCode.BAD_API_QUERY_PARAM_FORMAT_ERROR,
						"Unsupported query parameter data, unable to process the request." );
			}

			String reqUri = (StringUtils.isNotBlank(httpReq.getRequestURI())) ? httpReq.getRequestURI() : "";
			reqUrl = reqUri + "" + qryStr;
			method = (httpReq.getMethod() != null) ? httpReq.getMethod().toUpperCase() : "GET";

			String lowercaseUri = reqUri.toLowerCase();
			if (lowercaseUri.indexOf("/") == 0) {
				if (lowercaseUri.length() > 0) {
					lowercaseUri = lowercaseUri.substring(1);
				} else {
					lowercaseUri = "";
				}
			}
			String[] lowerCaseUriParts = lowercaseUri.split("/");
			boolean isPublicRequest = this.isPublicRequest( lowerCaseUriParts );
			boolean isForbiddenRequest = this.isForbiddenApi( user, method, lowerCaseUriParts );

			boolean isMaintenanceMode = systemMgr.isMaintenanceMode();
			if (!isPublicRequest && isLoggedIn && isMaintenanceMode && !method.equals("GET")) {
				// throw an exception that we're currently in maintenance mode and POST/DELETE/PUT are not allowed
				ResourceBase.sendJsonErrorBeanResponse(
						Status.SERVICE_UNAVAILABLE,
						servletResponse,
						ApiErrorCode.SERVICE_UNAVAILABLE_ERROR,
						"System is currently in maintenance mode, please try again later." );
			}

			if ( !isForbiddenRequest && (isPublicRequest || isLoggedIn) ) {
				//
				// continue with the filter chain
				// It's either a public API (i.e. login, register, forgot password) or it's a valid session api request
				//
				filterChain.doFilter( servletRequest, servletResponse );

				//
				// Log the response
				elapsed = System.currentTimeMillis() - start;

				status = httpResp.getStatus();
				bufferSize = httpResp.getBufferSize();

				return;
			}

			//
			// If we made it down here, the user is either not authorized or it's an invalid session
			//
			if ( !isPublicRequest && !isForbiddenRequest ) {
				// throw the invalid session error
				error = Status.UNAUTHORIZED + " : " + "Unauthorized access, unable to process the request.";
				ResourceBase.sendJsonErrorBeanResponse(
						Status.UNAUTHORIZED,
						servletResponse,
						ApiErrorCode.UNAUTHORIZED_ACCESS_ERROR,
						"Unauthorized access, unable to process the request." );
			} else if ( isForbiddenRequest ) {
				error = Status.FORBIDDEN + " : " + "Forbidden access, unable to process the request.";
				ResourceBase.sendJsonErrorBeanResponse(
						Status.FORBIDDEN,
						servletResponse,
						ApiErrorCode.FORBIDDEN_ACCESS_ERROR,
						"Forbidden access, unable to process the request." );
			} else {
				// throw the invalid session error
				error = Status.UNAUTHORIZED + " : " + "Invalid session, please reauthenticate and try again.";
				ResourceBase.sendJsonErrorBeanResponse(
						Status.UNAUTHORIZED,
						servletResponse,
						ApiErrorCode.INVALID_SESSION_ERROR,
						"Invalid session, please reauthenticate and try again." );
			}
		} catch (Exception e) {
			// don't create a stack trace out of this one. the app api's and broker should have already
			logger.error("API Filter Error", e);
			error += "; Exception: " + AppUtil.getErrMsg(e);
		} finally {
			if ( this.isAuditableApi(reqUrl, method) ) {
				boolean hasError = (StringUtils.isNotBlank(error)) ? true : false;
				//
				// Audit log everything except the audit log api resource path
				//
				// request: [url: "GET /api/salesforce/opportunity/quarters?tenantUsername=xavierluiz%40yahoo.com", user: admin, status: 200, size: 8192, elapsed: 57]
				StringBuilder sb = new StringBuilder();
				// method, reqUrl, username, status, bufferSize, elapsed, and possibly error
				sb.append("API: [request: \"%s %s\", user: %s, status: %d, size: %d, elapsed: %d");

				// add the error to the log if we have it
				if (hasError) {
					sb.append(", error: \"%s\"");
				}

				sb.append("]");

				if (hasError) {
					// has an error, add it to the log
					logger.apiAudit( String.format(  sb.toString(),
							method, reqUrl, username, status, bufferSize, elapsed, error ) );
				} else {
					// no error, leave the empty error message out
					logger.apiAudit( String.format(  sb.toString(),
							method, reqUrl, username, status, bufferSize, elapsed ) );
				}
			}

		}
	}

	private boolean isPublicRequest(String[] lowerCaseUriParts) {
		Set<String> publicApiPaths = ResourceBase.getPublicApiPaths();

		for ( String rawPath : publicApiPaths ) {
			rawPath = AppConstants.PATH_ROOT_NO_SLASH + "" + rawPath.toLowerCase();

			//
			// check if the raw path matches the lowercase'd request uri
			//
			if ( this.foundUriMatch(lowerCaseUriParts, rawPath) ) {
				return true;
			}
		}

		// it's not public
		return false;
	}

	private boolean isForbiddenApi(User user, String method, String[] lowerCaseUriParts) {

		Map<String, String> adminRequiredApiPaths = ResourceBase.getRequiredUberUserPaths();

		for ( String endpointMethod : adminRequiredApiPaths.keySet() ) {
			String rawPath = adminRequiredApiPaths.get(endpointMethod);
			rawPath = AppConstants.PATH_ROOT_NO_SLASH + "" + rawPath.toLowerCase();
			if ( this.foundUriMatch(lowerCaseUriParts, rawPath) && method.equalsIgnoreCase(endpointMethod) ) {
				if ( user == null || RoleType.getRoleTypeFromValue( user.getRole() ) != RoleType.ADMIN ) {
					//
					// It's an admin required endpoint and the session user is not an admin.
					// Return true, it's forbidden
					//
					return true;
				}
				break;
			}
		}
		// not forbidden
		return false;
	}

	private boolean isAuditableApi(String url, String method) {
		//
		// Don't return true if it's any of these endpoints
		//
		return (url.indexOf("/api/audit/") == -1 && url.indexOf("/api/database/") == -1 && url.indexOf("/api/job/progress") == -1);
	}

	private boolean foundUriMatch(String[] lowerCaseUriParts, String rawPath) {
		String lowercaseUri = StringUtils.join(lowerCaseUriParts, "/");

		String[] rawParts = rawPath.split("/");

		if ( rawParts.length == lowerCaseUriParts.length ) {

			if ( tokenPattern.matcher(rawPath).find() ) {

				boolean updateRawPath = false;
				for (int i = 0; i < rawParts.length; i++) {
					String rawPart = rawParts[i];
					String uriPart = lowerCaseUriParts[i];

					if ( tokenOnlyPattern.matcher(rawPart).find() ) {
						// replace it with the value from the uri part
						rawParts[i] = uriPart;
						updateRawPath = true;
					}
				}
				//
				// replace origLowercaseUri with the new pathPartsRecompiled list
				if (updateRawPath) {
					rawPath = StringUtils.join(rawParts, "/");
				}
			}

			if ( lowercaseUri.indexOf(rawPath) == 0 ) {
				return true;
			}
		}

		return false;
	}

}
