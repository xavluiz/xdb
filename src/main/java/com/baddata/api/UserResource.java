/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStream;

import javax.imageio.ImageIO;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;

import com.baddata.annotation.ApiInfo;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.user.PasswordResetRequest;
import com.baddata.api.dto.user.User;
import com.baddata.api.dto.user.UserSettings;
import com.baddata.api.factory.ResourceBase;
import com.baddata.exception.ApiServiceException;
import com.baddata.exception.ApiServiceException.ApiExceptionType;
import com.baddata.log.EventLogger.ApiErrorCode;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.baddata.util.FileUtil;
import com.baddata.util.ImageUtil;

@Path("user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource extends ResourceBase {

	@GET
	public User getUser() {
		return getUserBroker().getUser();
	}

	@GET
	@ApiInfo(requiresUberUserSesssion=true)
	@Path("/page")
	public Page getUsers() {
		try {
			this.buildRequestSearchSpec();
			return getUserBroker().getUsers( searchSpec );
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.BAD_API_QUERY_PARAM_FORMAT_ERROR.getCode() );
		}
	}

	@POST
	@ApiInfo(isPublicApi=true)
	@Path("/signup")
	public User signup(User user) {
		try {
			return getUserBroker().signup( user, request );
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.SIGNUP_FAILED_ERROR.getCode() );
		}
	}

	@PUT
	public void updateUser( User user ) {
		try {
			getUserBroker().updateUser( user );
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.USER_UPDATE_ERROR.getCode() );
		}
	}

	@PUT
	@Path("/change_password")
	public void changePassword( User creds ) {
		try {
			getUserBroker().changePassword( creds );
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.USER_CHANGE_PASSWORD_ERROR.getCode() );
		}
	}

	@PUT
	@ApiInfo(isPublicApi=true)
	@Path("/reset_password")
	public void resetPassword( User user ) {
		try {
			// using "password" and "tokenObj" from the user to reset the password
			getUserBroker().resetPassword(user);
		} catch ( ApiServiceException e ) {
			throw createWebApplicationException( e, ApiErrorCode.USER_RESET_PASSWORD_ERROR.getCode() );
		}
	}

	@POST
	@ApiInfo(isPublicApi=true)
	@Path("/send_password_reset")
	public void sendPasswordReset( PasswordResetRequest passwordResetReq ) {
		try {
			getUserBroker().sendResetPasswordNotification( passwordResetReq );
		} catch ( ApiServiceException e ) {
			throw createWebApplicationException( e, ApiErrorCode.USER_REQUEST_RESET_PASSWORD_ERROR.getCode() );
		}
	}

	@GET
	@Path("/settings")
	public UserSettings getUserSettings() {
		return getUserBroker().getUserSettings();
	}

	@DELETE
	@Path("/{userId}")
	public void deleteAccountForUserRef( @PathParam( "userId" ) String userId ) {
		try {
			Long userIdVal = null;
			try {
				userIdVal = Long.valueOf(userId);
			} catch (Exception e) {
				throw createWebApplicationException(new ApiServiceException(
						"Invalid user reference provided.", ApiExceptionType.BAD_REQUEST), ApiErrorCode.USER_DELETE_ERROR.getCode() );
			}
			this.getUserBroker().deleteAccount(userIdVal);
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.USER_ACCOUNT_DELETE_ERROR.getCode() );
		}
	}

	@DELETE
	@Path("/{userId}/by/{adminId}")
	@ApiInfo(requiresUberUserSesssion=true)
	public void deleteAccountForUserRefByAdmin( @PathParam( "userId" ) String userId, @PathParam( "adminId" ) String adminId ) {
		try {
			Long userIdVal = null;
			Long adminIdVal = null;
			try {
				userIdVal = Long.valueOf(userId);
				adminIdVal = Long.valueOf(adminId);
			} catch (Exception e) {
				throw createWebApplicationException(new ApiServiceException(
						"Invalid user reference provided.", ApiExceptionType.BAD_REQUEST), ApiErrorCode.USER_DELETE_ERROR.getCode() );
			}
			this.getUserBroker().deleteAccountByAdmin(userIdVal, adminIdVal);
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.USER_ACCOUNT_DELETE_ERROR.getCode() );
		}
	}

	@GET
	@ApiInfo(isPublicApi=true)
	@Path("/validate_token")
	public User validateWelcomeToken() {
		try {
			this.buildRequestSearchSpec();
			return this.getUserBroker().getUserByAuthToken(searchSpec.getAuthToken());
		} catch (ApiServiceException e) {
			throw createWebApplicationException( e, ApiErrorCode.USER_WELCOME_ERROR.getCode() );
		}
	}

	@GET
	@Path("/image")
	public Response getImage() {
		String asset = "";
		try {
			this.buildRequestSearchSpec();
			asset = searchSpec.getAsset();

			int assetWidth = searchSpec.getAssetWidth();
			int assetHeight = searchSpec.getAssetHeight();

			if (StringUtils.isBlank(asset)) {
				ApiServiceException ex = new ApiServiceException("Unable to fetch image asset, asset name not specified.", ApiExceptionType.BAD_REQUEST);
				throw createWebApplicationException( ex, ApiErrorCode.RETRIEVE_ASSET_FAILED.getCode() );
			}

			String fullPath = FileUtil.getAndCreateAbsoluteUserImagesDir(this.userName);
			fullPath += File.separatorChar + asset;

			File imageFile = new File(fullPath);
			if (!imageFile.exists()) {
				// get the user
				User user = getUserBroker().getUser();
				// create them
				String avatarImgCreated = FileUtil.getImageContextFileName(this.userName, AppConstants.AVATAR_FILE_TOKEN);
				// update the user avatar
				String backgroundImgCreated = FileUtil.getImageContextFileName(this.userName, AppConstants.PROFILE_BACKGROUND_FILE_TOKEN);
				// update the user background image
				user.setAvatar(AppUtil.getUserImageName(avatarImgCreated));
	            user.setProfileBackgroundImg(AppUtil.getUserImageName(backgroundImgCreated));
	            getUserBroker().updateUser(user);
	            // set the asset
	            if (fullPath.indexOf(AppConstants.AVATAR_FILE_TOKEN) != -1) {
	            		asset = avatarImgCreated.substring(avatarImgCreated.lastIndexOf("/"));
	            } else {
	            		asset = backgroundImgCreated.substring(backgroundImgCreated.lastIndexOf("/"));
	            }
	            
	            // replace the full path
	            fullPath = FileUtil.getAndCreateAbsoluteUserImagesDir(this.userName);
	            fullPath += asset;
				imageFile = new File(fullPath);
			}

			BufferedImage image = ImageIO.read(imageFile);

			image = ImageUtil.getScaledInstance(
					image, assetWidth /* width */, assetHeight /* height */, RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);

			if (image != null) {

				File file = new File(fullPath);
				int extIdx = (file != null) ? file.getName().lastIndexOf(".") : -1;
				String ext = (extIdx > 0 && extIdx < file.getName().length() - 1) ? file.getName().substring(extIdx + 1) : "";

				String mimeType = "image/jpeg";
				String formatName = "jpg";
				if (ext.equalsIgnoreCase("svg")) {
					mimeType = "image/svg+xml";
					formatName = "svg";
				} else {
					mimeType = "image/" + ext.toLowerCase();
					formatName = ext.toLowerCase();
				}

				response.setContentType( mimeType );
				response.setHeader("Content-Type", mimeType);
				response.setHeader("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");

				OutputStream out = response.getOutputStream();
				ImageIO.write(image, formatName, out);
				out.flush();
				out.close();

				return Response.ok().build();
			}
		} catch (Exception e) {
			ApiServiceException ex = new ApiServiceException("Failed to locate the image '" + asset + "'. " + AppUtil.getErrMsg(e), ApiExceptionType.INTERNAL_SERVER_ERROR);
			throw createWebApplicationException( ex, ApiErrorCode.RETRIEVE_ASSET_FAILED.getCode() );
		}
		// image not found
		ApiServiceException ex = new ApiServiceException("Failed to locate the image '" + asset + "'", ApiExceptionType.NOT_FOUND);
		throw createWebApplicationException( ex, ApiErrorCode.RETRIEVE_ASSET_FAILED.getCode() );
	}

}
