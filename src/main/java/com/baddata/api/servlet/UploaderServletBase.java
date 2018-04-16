/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.servlet;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.baddata.api.dto.UploadResponse;
import com.baddata.api.dto.user.User;
import com.baddata.api.factory.ApiSessionContext;
import com.baddata.api.factory.ResourceBase;
import com.baddata.db.lucene.IndexPathInfo;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.EventLogger.ApiErrorCode;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.baddata.util.FileUtil;
import com.google.common.collect.Lists;

public abstract class UploaderServletBase extends HttpServlet {

    private static final long serialVersionUID = 1L;
    
    protected static Logger logger = Logger.getLogger(UploaderServletBase.class.getName());
    
    protected static String BIZ_PREFIX = "bizaddr".intern();
    protected static String REPO_FOLDER = "repo".intern();
    protected static String UPLOADS_FOLDER = "uploads".intern();
    protected static String TMP_PREFIX = "tmp_".intern();
    
    public File userFileUploadDir = null;
    public String username = "";
    public String prefix = "";
    
    protected enum FileDeleteType {
        DELETE_TMP_ONLY, DELETE_NON_TMP_ONLY, DELETE_ALL
    }
    
    protected boolean isValidDoPostUpload( HttpServletRequest req, HttpServletResponse resp, boolean validateMultipart ) throws IOException {
        
        String authorizationHeader = req.getHeader(HttpHeaders.AUTHORIZATION);
        if ( StringUtils.isBlank( authorizationHeader ) ) {
            authorizationHeader = AppUtil.getSingleQueryParamValueByName(req, AppConstants.AUTH_TOKEN);
        }
        User user = ApiSessionContext.getUser(authorizationHeader);
        boolean isLoggedIn = (user != null);
        if (!isLoggedIn) {
            String errMsg = "File upload failed, invalid session for request: " + req.toString();
            logger.error( errMsg, null);
            ResourceBase.sendJsonErrorBeanResponse(
                    Status.UNAUTHORIZED,
                    resp,
                    ApiErrorCode.INVALID_SESSION_ERROR,
                    "Invalid session, please reauthenticate and try again." );
            return false;
        }
        
        //
        // check if they have the correct csrf token
        //
        String afToken = AppUtil.getSingleQueryParamValueByName(req, AppConstants.AF_TOKEN);
        String csrfToken = ApiSessionContext.getCsrfToken(user.getId());
        if ( StringUtils.isBlank(afToken) || StringUtils.isBlank(csrfToken)) {
            ResourceBase.sendJsonErrorBeanResponse(
                    Status.UNAUTHORIZED,
                    resp,
                    ApiErrorCode.UNAUTHORIZED_ACCESS_ERROR,
                    "Invalid security token, unable to process the request." );
            return false;
        }
        boolean csrfTokenMatches = (afToken.equals(csrfToken)) ? true : false;
        
        //
        // Throw an unauthorized error if the csrf token isn't found
        //
        if ( !csrfTokenMatches ) {
            ResourceBase.sendJsonErrorBeanResponse(
                    Status.UNAUTHORIZED,
                    resp,
                    ApiErrorCode.UNAUTHORIZED_ACCESS_ERROR,
                    "Invalid security token, unable to process the request." );
            return false;
        }
        
        // user isn't null, get the username
        username = user.getUsername();
        
        // check if it's a multipart request
        if ( validateMultipart && !ServletFileUpload.isMultipartContent( req ) ) {
            // we can only handle multipart requests
            ResourceBase.sendJsonErrorBeanResponse(
                    Status.BAD_REQUEST,
                    resp,
                    ApiErrorCode.FILE_UPLOAD_NON_MULTIPART_ERROR,
                    "Invalid content type, please set the content type as 'multipart/form-data'");
            return false;
        }
        
        prefix = ( req.getParameter("prefix") != null) ? req.getParameter("prefix").trim() : "";
        
        // user found, create the destination dir
        String webInfPath = getServletContext().getRealPath(File.separatorChar + REPO_FOLDER);
        IndexPathInfo pathInfo = new IndexPathInfo(webInfPath, UPLOADS_FOLDER, username);
        userFileUploadDir = FileUtil.getOrCreatePathDir( pathInfo );
        
        return true;
    }
    
    protected void postNewUserImage(HttpServletRequest req, HttpServletResponse resp, String imageFileTokenPart) throws ServletException, IOException {
    	if ( !this.isValidDoPostUpload(req, resp, true /*validateMultipart*/) ) {
            return;
        }
        
        // --------------------------------------------------------------------------------------------
        // example payload
        // 
        // Request Payload
        // ------WebKitFormBoundary5LiiiTVMXy8lqfWP
        // Content-Disposition: form-data; name="avatar"
        //
        // admin
        // ------WebKitFormBoundary5LiiiTVMXy8lqfWP
        // Content-Disposition: form-data; name="file"; filename="2016-04-16-bikingwithdavidlam.jpg"
        // Content-Type: image/jpeg
        // 
        // 
        // ------WebKitFormBoundary5LiiiTVMXy8lqfWP--
        // --------------------------------------------------------------------------------------------

        Collection<Part> fileParts = req.getParts();
        if ( CollectionUtils.isEmpty(fileParts) ) {
        	ResourceBase.sendJsonErrorBeanResponse(
                    Status.INTERNAL_SERVER_ERROR,
                    resp,
                    ApiErrorCode.FILE_UPLOAD_ERROR,
                    "No file content to save. Please check the file and try again.");
        	return;
        }
        
        // get the file extension. this will also check if this is an image file
        String ext = this.getExtensionForImageContentType(resp, fileParts, true /*includeDot*/);

        if ( ext == null ) {
        	// no extension found, probably a content type issue. we've already flushed the error, return out
        	return;
        }
        
        // verify it's an image extension
        if (!this.isValidImageFileExtension(ext)) {
            // throw an error
            String errMsg = "The file does not match a valid image filename or content type.";
            logger.error( errMsg, null);
            ResourceBase.sendJsonErrorBeanResponse(
                    Status.INTERNAL_SERVER_ERROR,
                    resp,
                    ApiErrorCode.FILE_UPLOAD_ERROR,
                    "Please provide a valid image to upload.");
            return;
        }
        
        String destinationDirStr = FileUtil.getAndCreateAbsoluteUserImagesDir(username);
        File destinationDir = new File(destinationDirStr);
        String imgPrefix = username + "" + imageFileTokenPart;
        
        //
        // continue to create/build the avatar file name
        this.deleteUserTmpFiles(destinationDir, imgPrefix);
        String imgName = imgPrefix + "" + ext;
        String tmpImgName = TMP_PREFIX + "" + imgPrefix + "" + ext;

        //
        // Go through the parts and write the data to disk
        long totalBytesUploaded = 0;
        for ( Part part : fileParts ) {
        	String contentType = part.getContentType();
        	if ( StringUtils.isBlank( contentType ) ) {
        		// no content type, go to the next one
        		continue;
        	}
        	if ( contentType.toLowerCase().indexOf("image/") != -1 ) {
        		// this is the part to write
        		try {
        		    FileUtil.writeImageFile( destinationDir, tmpImgName, ext, part.getInputStream() );
        		    totalBytesUploaded +=  part.getSize();
				} catch (FileUploadException e) {
					logger.error( "Failed to upload user avatar image.", e );
                    ResourceBase.sendJsonErrorBeanResponse(
                            Status.INTERNAL_SERVER_ERROR,
                            resp,
                            ApiErrorCode.FILE_UPLOAD_ERROR,
                            "Failed to upload the user avatar image. " + AppUtil.getErrMsg(e));
                    return;
				}
        	}
        }
        
        
        //
        // No errors, delete all user files except for the tmp and copy the tmp to the real filename
        // delete existing user files except fo the tmp
        this.deleteUserFiles(destinationDir, imgPrefix);
        // copy the src to the dest
        File srcFile = new File( destinationDir, tmpImgName );
        File destFile = new File( destinationDir, imgName );
        FileUtils.copyFile(srcFile, destFile);
        
        logger.debug("Uploaded file '" + imgName + "' to destination '" + destinationDirStr + "' containing " + totalBytesUploaded + " bytes.");
        
        User user = PersistenceManager.getInstance().getUserByUsername(username);
        if ( user != null ) {
            
            String logMsgToken = "avatar";
            if (imageFileTokenPart.equals(AppConstants.AVATAR_FILE_TOKEN)) {
                user.setAvatar(imgName);
            } else if (imageFileTokenPart.equals(AppConstants.PROFILE_BACKGROUND_FILE_TOKEN)) {
                logMsgToken = "background";
                user.setProfileBackgroundImg(imgName);
            }
            try {
                PersistenceManager.getInstance().update(user);
            } catch (IndexPersistException e) {
                logger.error("Failed to update user's " + logMsgToken + " to " + imgName + ".", e);
            }
        }
            
        //
        // Stream the success response back.
        // Return the filename in the response
        String newContextFileName = FileUtil.getImageContextFileName(username, imageFileTokenPart);
        this.sendSuccessResponse(resp, newContextFileName);
    }
    
    private void deleteUserFiles(File userDir, String imagePrefix) {
        this.deleteUserFilesByDirective(userDir, imagePrefix, FileDeleteType.DELETE_NON_TMP_ONLY);
    }
    
    private void deleteUserTmpFiles(File userDir, String imagePrefix) {
        this.deleteUserFilesByDirective(userDir, imagePrefix, FileDeleteType.DELETE_TMP_ONLY);
    }
    
    private void deleteUserFilesByDirective(File userDir, String imagePrefix, FileDeleteType deleteType) {
        if (userDir.isDirectory()) {
            if (userDir.listFiles() != null) {
                for (File dirFile : userDir.listFiles()) {
                    
                    String fName = dirFile.getName().toLowerCase();
                    String fNameTmpPattern = TMP_PREFIX + "" + imagePrefix;
                    
                    if (!dirFile.isDirectory() &&
                            fName.indexOf(imagePrefix) == 0) {
                        
                        switch (deleteType) {
                            case DELETE_NON_TMP_ONLY:
                                if (fName.indexOf(fNameTmpPattern) == 0) {
                                    continue;
                                } else {
                                    dirFile.delete();
                                }
                                break;
                            case DELETE_TMP_ONLY:
                                if (fName.indexOf(fNameTmpPattern) == 0) {
                                    dirFile.delete();
                                }
                                break;
                            default:
                                dirFile.delete();
                                break;
                        }
                    }
                }
            }
        }
    }
    
    protected String buildNewFilename(HttpServletRequest req, String submittedFilename) {
        // create a unique uuid part to add into the file name
        String uniqueUuid = this.createSmallId();
        
        // get the extension out of the passed in filename if we have it
        String ext = AppUtil.getFileExtension(submittedFilename, true /*includeDot*/);
        
        String newFilename = prefix + "_" + uniqueUuid + "" + ext;
        
        return newFilename;
    }
    
    protected synchronized String createSmallId() {
        // create a unique uuid
        String snallId = UUID.randomUUID().toString();
        if ( snallId.indexOf("-") != -1 ) {
            snallId = snallId.substring(0, snallId.indexOf("-"));
        } else {
            snallId = snallId.substring(0, 8);
        }
        return snallId;
    }
    
    /**
     * Returns the following format to the client.
     * {"fileUrl":"../imageStore/a/admin-avatar.jpg","status":"success"}
     * 
     * @param resp
     * @param contextFilename
     * @throws IOException
     */
    protected void sendSuccessResponse(HttpServletResponse resp, String contextFilename) throws IOException {
    	UploadResponse uploadResponse = new UploadResponse();
        ResourceBase.sendJsonUploadSuccessBeanResponse(resp, uploadResponse);
    }
    
    protected String getExtensionForImageContentType( HttpServletResponse resp, Collection<Part> fileParts, boolean includeDot ) throws IOException {
    	List<String> foundContentTypes = Lists.newArrayList();
    	
    	if ( CollectionUtils.isNotEmpty( fileParts ) ) {

        	//
        	// Go through the file parts to get the extension and make sure it's only an image file
            for ( Part part : fileParts ) {

                String contentType = part.getContentType();
                
                //
                // Go to the next part if the contentType is null/empty
                if ( StringUtils.isBlank( contentType ) ) {
                    // this part doesn't contain the file information we need
                    continue;
                }
                
                //
                // Get the extension
                String fileName = part.getSubmittedFileName();
                contentType = contentType.toLowerCase();
                // only accept "image/*" content type
                if ( contentType.indexOf("image/") != -1 ) {
                	// get the extension out of the passed in filename if we have it
                	return AppUtil.getFileExtension(fileName, includeDot);
                } else {
                	// add this content type to the found content types list
                	foundContentTypes.add(contentType);
                }
            }
        }
    	
    	// unable to return an extension, throw an exception
    	ResourceBase.sendJsonErrorBeanResponse(
                Status.BAD_REQUEST,
                resp,
                ApiErrorCode.FILE_UPLOAD_NON_MULTIPART_ERROR,
                "Invalid content type, please upload an image. Found content type(s): " + StringUtils.join(foundContentTypes, ", "));
    	return null;
    }
    
    protected boolean isValidImageFileExtension(String ext) {
        // verify it's an image extension
        String testExt = (ext.indexOf(".") == 0) ? ext.substring(1) : ext;
        if (testExt.equalsIgnoreCase("jpg") || testExt.equalsIgnoreCase("jpeg") ||
                testExt.equalsIgnoreCase("png") || testExt.equalsIgnoreCase("svg") || testExt.equalsIgnoreCase("gif")) {
            return true;
        }
        return false;
    }
}
