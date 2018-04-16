/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.util;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.baddata.api.factory.ResourceBase;
import com.baddata.db.lucene.IndexPathInfo;
import com.baddata.exception.ApiServiceException;
import com.baddata.log.EventLogger.ApiErrorCode;
import com.baddata.log.Logger;
import com.google.common.io.Files;

public final class FileUtil {

	private static Logger logger = Logger.getLogger(FileUtil.class.getName());

	private static FileUtil ref;

	// this is only used if synthetic.data.dir within server.properties is not set
	private static String defaultSyntheticDatasetDir = "dataset5";

	public static FileUtil getInstance() {
		if (ref == null) {
			synchronized(FileUtil.class) {
				if (ref == null) {
					ref = new FileUtil();
				}
			}
		}
		return ref;
	}

	/**
	 * Write the given string to the given file.
	 * @throws ApiServiceException
	 */
	public static void writeToFile(String path, String content, boolean append) {

		BufferedWriter out = null;

		try {
			out = new BufferedWriter(new FileWriter(path, append));
			out.write(content);
			out.flush();
		} catch (Exception e) {
			logger.error("Interrupted IO Error, failed to write contents to file: " + path, e);
		} finally {
			if ( out != null ) {
				try {
					out.close();
				} catch (Exception e) {
					logger.error("Failed to close buffered writer when writing to file: " + path, null);
				}
			}
		}
	}

	/**
	 * Determine the parent folder from a fully qualified path.
	 */
	public static String dirname(String path) {
		return new File(path).getParent();
	}

	/**
	 * Determine the file name from a fully qualified path.
	 */
	public static String basename(String path) {
		return new File(path).getName();
	}

	protected static String getCurrencyDirPath() {
		return getWebPath() + File.separatorChar + "webapps" + File.separatorChar + "ROOT" + File.separatorChar + AppConstants.CURRENCY_DIR_NAME;
	}

	protected static File getCurrencyDir() {
		return new File( getCurrencyDirPath() );
	}

	public static File getCurrencyExchangeRateJsonFile() {
		String fileName = AppConstants.CURRENCY_FILE_NAME;

		File currencyDir = getCurrencyDir();
		// if currency date doesn't exist, at least create that
		if ( !currencyDir.exists() ) {
			try {
				FileUtils.forceMkdir( currencyDir );
			} catch (IOException e) {
				logger.error("Failed to create the currency directory: '" + currencyDir.getAbsolutePath() + "'", e);
				return null;
			}
		}

		// Currency dir exists or we just created, now get the currency quote file.
		String currencyFile = getCurrencyDirPath() + File.separatorChar + fileName;

		// it may not exist, but now the caller will have the file object to create one
		return new File(currencyFile);
	}

	public static String getOpportunityLogInfoFilePath() {
		String fileName = AppConstants.OPPORTUNITY_LOG_INFO;

		return getWebPath() + File.separatorChar + "logs" + File.separatorChar + fileName;

	}

	public static String getOpportunityFieldHistoryDownloadLogInfoFilePath() {
		String fileName = AppConstants.OPPORTUNITY_FIELD_HISTORY_LOG_INFO;

		return getWebPath() + File.separatorChar + "logs" + File.separatorChar + fileName;
	}

	public static String getOpportunityCurrencyConversionLogInfoFilePath() {
		String fileName = AppConstants.OPPORTUNITY_CURRENCY_CONVERSION_LOG_INFO;

		return getWebPath() + File.separatorChar + "logs" + File.separatorChar + fileName;
	}

	public static File getExistingImageFile(String username, String fileTokenPart) {

		File userDir = new File(getAndCreateAbsoluteUserImagesDir(username));
		if ( !userDir.exists() ) {
			// directory doesn't exist, so the file doesn't exist either, return false.
			return null;
		}

		// directory exists, check if there's a file for this user
		File[] dirFiles = userDir.listFiles();
		if ( dirFiles == null || dirFiles.length == 0 ) {
			// no files found, return false
			return null;
		}

		// found files, look for one with the username + the avatar token
		String matchingPattern = username + fileTokenPart + ".";
		for ( File f : dirFiles ) {
			if ( f.getName().startsWith(matchingPattern) ) {
				return f;
			}
		}
		return null;
	}

	public static String getUserDirName(String username) {
		return username.toLowerCase().substring(0, 1);
	}

	public static void initializeImageStore() {
		File imageStoreDir = new File(getWebPath() + File.separatorChar + AppConstants.IMAGE_STORE_NAME);
		if ( !imageStoreDir.exists() ) {
			//
			// It doesn't, create it
			try {
				FileUtils.forceMkdir( imageStoreDir );
				logger.debug("CREATED USER IMAGES DIR: '" + imageStoreDir.getAbsolutePath() + "'.");
			} catch (IOException e) {
				logger.error( "Failed to create file '" + imageStoreDir.getAbsolutePath() + "'.", e);
			}
		}
	}

	protected static File getUserImagesDir(String username) {

		// get the 1st character of the username
		String userDirName = getUserDirName(username);

		String userDirFileName = getWebPath() + File.separatorChar + AppConstants.IMAGE_STORE_NAME + File.separatorChar + userDirName;

		return new File(userDirFileName);
	}

	public static String getAndCreateAbsoluteUserImagesDir(String username) {

		//
		// Create it if it doesn't exist
		File userImagesDir = getUserImagesDir(username);
		if ( !userImagesDir.exists() ) {

			//
			// It doesn't, create it
			try {
				FileUtils.forceMkdir( userImagesDir );
				logger.debug("CREATED USER IMAGES DIR: '" + userImagesDir.getAbsolutePath() + "'.");
			} catch (IOException e) {
				logger.error( "Failed to create file '" + userImagesDir.getAbsolutePath() + "'.", e);
				return null;
			}
		}

		return userImagesDir.getAbsolutePath();
	}

	public static File getBaseTeplateFile(String fileTokenPart) {
		String avatarAbsFileName = getWebPath() + 
				File.separatorChar + "webapps" +
				File.separatorChar + "ROOT" +
				getBaseImageContextFileName(fileTokenPart);

		return new File(avatarAbsFileName);
	}

	public static File getUserImageAbsoluteFile(String username, String fileTokenPart) {
		String userImageAbsFileName = getWebPath() + 
				getImageContextFileName(username, fileTokenPart);

		return new File(userImageAbsFileName);
	}

	public static String getBaseImageContextFileName(String fileTokenPart) {
		return File.separatorChar + "images" + File.separatorChar + "user" + fileTokenPart + ".jpg";
	}

	public static void deleteUserAccountImages(String username) {
		File userImagesDir = getUserImagesDir(username);

		String filePattern = username + "-";

		if (userImagesDir.exists()) {
			// go through the files and delete the ones related to this user
			for ( File f : userImagesDir.listFiles() ) {
				if ( !f.isDirectory() && f.getName().indexOf(filePattern) != -1 ) {
					//
					// Delete it
					f.delete();
				}
			}
		}
	}

	public static String getImageContextFileName(String username, String fileTokenPart) {

		//
		// Get the base extension
		File baseAvatarFile = getBaseTeplateFile(fileTokenPart);
		String ext = AppUtil.getFileExtension(baseAvatarFile.getName(), true /*includeDot*/);

		// start off with...
		// "imageStore/<userdirname>/<username>-avatar."
		// we should end up with something like "imageStore/<userdirname>/<username>-avatar.jpg"
		// where <userdirname> may be something like "a" and <username> something like "john123"
		// the only thing we need now is the extension
		String userDirName = getUserDirName(username);

		String contextFileName = AppConstants.IMAGE_STORE_NAME + File.separatorChar + userDirName + File.separatorChar + username + fileTokenPart;

		//
		// Look for an existing user specific avatar file
		File existingUserAvatarFile = getExistingImageFile(username, fileTokenPart);

		//
		// if it's null, create one based on the base avatar image
		if ( existingUserAvatarFile == null ) {
			//
			// Doesn't exist, create one for the user.
			// The "getAbsoluteUserImagesDir" will create the user dir if it doesn't exist.
			String userDirPath = getAndCreateAbsoluteUserImagesDir(username);

			String destinationUserAvatarAbsFileName = userDirPath + File.separatorChar + username + fileTokenPart + ext;
			File avatarFile = new File(destinationUserAvatarAbsFileName);

			//
			// Copy the base avatar for this user
			// copy the template to the avatar file
			try {
				FileUtils.copyFile(getBaseTeplateFile(fileTokenPart), avatarFile);
			} catch (IOException e) {
				logger.error("Failed to copy the file.", e);
				return null;
			}
		} else {
			// get the saved file's extension
			ext = AppUtil.getFileExtension(existingUserAvatarFile.getName(), true /*includeDot*/);
		}

		//
		// We're either using the saved user's avatar file's extension or the base avatar file extension
		return contextFileName + ext;
	}

	/**
	 * Checks to see if the directory exists and creates one if it doesn't exist
	 * @param indexType
	 * @return
	 * @throws IOException
	 */
	public static File getLuceneIndex(IndexPathInfo pathInfo, boolean isSearch) {

		String luceneFilePathStr = getWebPath() + File.separatorChar + pathInfo.getFilePath();

		File luceneFilePathFile = new File(luceneFilePathStr);

		if ( !luceneFilePathFile.exists() && !isSearch ) {
			// create it
			try {
				FileUtils.forceMkdir(luceneFilePathFile);
			} catch (IOException e) {
				logger.error("Error creating the lucene path '" + luceneFilePathStr + "'.", e );
			}
		}

		return luceneFilePathFile;
	}

	public static File getLuceneDir() {
		String luceneFilePathStr = getWebPath() + File.separatorChar + AppConstants.LUCENE_STORE_NAME;

		return new File(luceneFilePathStr);
	}

	public static long getLuceneIndexSize(IndexPathInfo pathInfo, boolean isSearch) {
		synchronized (pathInfo.getDbIndexType()) {
			File luceneDir = getLuceneIndex(pathInfo, isSearch);
			if (luceneDir != null && luceneDir.isDirectory()) {
				return getFolderSize(luceneDir);
			}
			return 0;
		}
	}

	public static long getFolderSize(File dir) {
		long size = 0;
		if ( dir != null ) {
			File[] files = dir.listFiles();
			if ( files != null ) {
				for (File file : files) {
					if (file.isFile()) {
						size += file.length();
					} else {
						size += getFolderSize(file);
					}
				}
			}
		}
		return size;
	}

	public static boolean indexExists(IndexPathInfo pathInfo) {
		synchronized (pathInfo.getDbIndexType()) {

			// build filepath
			File f = FileUtil.getLuceneIndex(pathInfo, false /*isSearch*/);

			// return true if file is not null and exists
			return (f != null && f.exists());
		}
	}

	public static boolean deleteFile(File f) throws IOException {
		if ( f == null || !f.exists() ) {
			return true;
		}

		boolean deletedFile = f.delete();

		return deletedFile;
	}

	// Deletes all files and sub directories under dir.
	// Returns true if all deletions were successful.
	// If a deletion fails, the method stops attempting to delete and returns false.
	public static boolean deleteDir(File dir) {
		if ( dir == null ) {
			logger.warn("Null file passed into 'deleteDir', returning 'true' as the directory doesn't exist.");
			return true;
		}

		if ( dir.isDirectory() ) {
			if (dir.list() != null) {
				String[] children = dir.list();
				if (children != null) {
					for (int i = 0; i < children.length; i++) {
						boolean success = deleteDir( new File( dir, children[i] ) );
						if (!success) {
							return false;
						}
					}
				}
			}
		}

		// The directory is now empty so delete it
		return dir.delete();
	}

	public static File getOrCreatePathDir(IndexPathInfo pathInfo) {
		File file = new File( pathInfo.getFilePath() );

		if ( !file.exists() ) {
			try {
				FileUtils.forceMkdir( file );
				logger.debug("CREATED FILE: '" + file.getAbsolutePath() + "'.");
			} catch (IOException e) {
				logger.error( "Failed to create file '" + file.getAbsolutePath() + "'.", e );
			}
		}

		return file;
	}

	@SuppressWarnings("unchecked")
	public static JSONArray getJsonArrayFromCsvFile(File file) {
		if ( !file.getPath().startsWith("/") ) {
			// prepend the root dir
			String absFile = getWebPath() + File.separatorChar + file.getPath();
			file = new File(absFile);
		}
		JSONArray csvJsonArr = new JSONArray();
		Charset charset = Charset.forName("UTF-8");
		try ( BufferedReader br = Files.newReader(file, charset) ) {
			List<String> headers = new ArrayList<String>();
			int lineIdx = 0;
			for ( String line; (line = br.readLine()) != null; ) {

				// Replace all commas with a space then a comma.
				// This helps create an empty value for the following use case:
				//    "5b - Closed Lost,,67800"
				// The field between lost and 67800 is quarter status
				// We need to have a space there so it can be added to the vals array.
				line = line.replaceAll(",", " ,");

				// remove all whitespace from the 1st line (headers)
				if ( lineIdx == 0 ) {
					line = line.replaceAll("\\s+", "");
				}

				// add a space after the last comma if we've determined the last
				// comma is the last character of this line. that way we'll add this
				// as an empty value.
				if ( line.lastIndexOf(",") == line.length() - 1 ) {
					line += " ";
				}

				// split the line by commas
				String[] vals = line.split(",");
				if ( headers.isEmpty() ) {
					// It's the first row, populate the headers.
					// The header line will already be trimmed, just use the vals.
					for ( String val : vals ) {
						headers.add(val);
					}
				} else {
					JSONObject jsonObj = new JSONObject();
					int i = 0;
					for ( String key : headers ) {
						String value = "";
						if ( vals.length > i - 1 ) {
							value = vals[i].trim();
						}
						jsonObj.put(key, value);
						i++;
					}
					// add the JSONObject to the JSONArray
					csvJsonArr.add(jsonObj);
				}
				lineIdx++;
			}
		} catch (IOException e) {
			logger.error("Failed to read the CSV file '" + file.getAbsolutePath() + "'.", e );
		}
		return csvJsonArr;
	}

	public static String getWebPath() {
		String tomcatHome = System.getProperty(AppConstants.TOMCAT_HOME_VAR);
		if (tomcatHome != null) {
			File f = new File(tomcatHome);
			if (f.exists() && f.isDirectory()) {
				return tomcatHome;
			}
		}

		// provide a default if it can't find the server.properties file
		String webPathHome = System.getProperty("CATALINA_HOME");
		if (webPathHome == null) {
			webPathHome = Paths.get(".").toAbsolutePath().normalize().toString();
		}

		// return the web path directory
		return webPathHome;
	}

	public static File getEmailTemplate(String name) {
		String emailTemplate = getWebPath() + File.separatorChar + "webapps" +
				File.separatorChar + "ROOT" + File.separatorChar + "templates" + File.separatorChar + "email" + File.separatorChar + name;

		return new File(emailTemplate);
	}

	public static File getLogFile() {
		String filePath = getWebPath() + File.separatorChar + "logs" + File.separatorChar + System.getProperty( AppConstants.LOG_FILE_NAME, "baddata.log" );
		File f = new File(filePath);
		return f;
	}

	public static File getLocalTestLogFile() {
		String absPath = Paths.get(".").toAbsolutePath().normalize().toString();
		String testPath = System.getProperty(AppConstants.TEST_LOG_PATH, "util");
		String tomcatPath = absPath + File.separatorChar + testPath + File.separatorChar + System.getProperty( AppConstants.LOG_FILE_NAME, "baddata_test.log" );
		File f = new File(tomcatPath);
		return f;
	}

	public static File getSyntheticData(String syntheticFile) {
		// provide a default if it can't find the server.properties file
		String datasetDir = System.getProperty(AppConstants.DEV_SYNTHETIC_DATASET);
		if (datasetDir == null) {
			datasetDir = AppUtil.get(AppConstants.SYNTHETIC_DATASET, defaultSyntheticDatasetDir);
		}
		String filePath = getWebPath() + File.separatorChar + "webapps" + File.separatorChar + "ROOT" + File.separatorChar + datasetDir + File.separatorChar + syntheticFile;
		File f = new File(filePath);
		return f;
	}

	public static void writeBase64EncodedFile( File destDir, String fileName, byte[] decoded, HttpServletResponse resp ) throws IOException {
		File destFile = new File( destDir, fileName );
		OutputStream os = null;
		try {
			os = new BufferedOutputStream( new FileOutputStream( destFile ) );
			os.write( decoded );
		} catch ( Exception e) {
			logger.error( "Failed to upload file to destination '" + destDir.getAbsolutePath() + "'.", e );
			ResourceBase.sendJsonErrorBeanResponse(
					Status.INTERNAL_SERVER_ERROR,
					resp,
					ApiErrorCode.FILE_UPLOAD_ERROR,
					"Unable to complete file upload, reason: '" + e.getMessage() + "'.");
		} finally {
			if ( os != null ) {
				try {
					os.close();
				} catch (Exception e) {
					logger.error("Failed to close output stream.", e );
				}
			}
		}
	}

	public static void writeImageFile( File destDir, String fileName, String imageExtension, InputStream inputStream ) throws FileUploadException, IOException {
		//
		// Ensure it's an image
		//
		try {
			BufferedImage bi = ImageIO.read( inputStream );
			if ( bi == null ) {
				throw new FileUploadException("Invalid image content.");
			}

			File newFile = new File( destDir, fileName );

			if ( newFile.exists() ) {
				// delete it
				newFile.delete();
			}

			imageExtension = (StringUtils.isBlank(imageExtension)) ? "jpg" : imageExtension;
			imageExtension = (imageExtension.indexOf(".") == 0) ? imageExtension.substring(1) : imageExtension;
			ImageIO.write(bi, imageExtension, newFile);

		} finally {
			if ( inputStream != null ) {
				try {
					inputStream.close();
					inputStream = null;
				} catch ( Exception e ) {
					inputStream = null;
				}
			}
		}
	}

	public static byte[] storeAndChecksumImageFile( File destDir, String fileName, Part part ) throws FileUploadException, IOException {

		InputStream partInputStream = part.getInputStream();

		return storeAndChecksumImageFile(destDir, fileName, partInputStream);
	}

	public static byte[] storeAndChecksumImageFile( File destDir, String fileName, InputStream inputStream ) throws FileUploadException, IOException {
		//
		// Ensure it's an image
		//
		try {
			String mimeType = URLConnection.guessContentTypeFromStream( inputStream );
			if ( mimeType == null || mimeType.toLowerCase().indexOf("image") == -1 ) {
				throw new FileUploadException("Invalid image content.");
			}
		} catch (Exception e) {
			throw new FileUploadException("Invalid image content. " + ExceptionUtils.getMessage(e));
		}

		File newFile = new File( destDir, fileName );

		if ( newFile.exists() ) {
			// delete it
			newFile.delete();
		}

		try {
			if ( !newFile.createNewFile() ) {
				logger.error("Failed creating new upload file '" + newFile.getAbsolutePath() + "'.", null);
				throw new FileUploadException("Failed to save file");
			}

			FileOutputStream fos = new FileOutputStream( newFile, false /* append */);
			MessageDigest md = MessageDigest.getInstance( AlgoUtil.MD5_ALGO );
			InputStream is = new DigestInputStream( inputStream, md );

			try {
				byte[] buffer = new byte[4096];
				int readLen;
				while ( ( readLen = is.read( buffer ) ) != -1 ) {
					// write out the file
					fos.write( buffer, 0, readLen );
				}
				fos.flush();
			} catch (Exception e) {
				throw new FileUploadException("Invalid image content. " + ExceptionUtils.getMessage(e));
			} finally {
				// clean up the input and output
				if ( inputStream != null ) {
					try {
						inputStream.close();
						inputStream = null;
					} catch ( Exception e ) {
						inputStream = null;
					}
				}
				if ( is != null ) {
					try {
						is.close();
						is = null;
					} catch ( Exception e ) {
						is = null;
					}
				}
				if ( fos != null ) {
					try {
						fos.flush();
						fos.close();
						fos = null;
					} catch ( Exception e ) {
						fos = null;
					}
				}
			}

			return md.digest();

		} catch (IOException e) {
			// delete the new file since the upload failed
			if ( newFile.exists() ) {
				newFile.delete();
			}
			throw new FileUploadException( e.getMessage(), e );
		} catch (NoSuchAlgorithmException e) {
			// delete the new file since the upload failed
			if ( newFile.exists() ) {
				newFile.delete();
			}

			// this should never happen since java ships with MD5
			logger.error( "Unable to utilize 'MD5' message digest algorithm.", e );
			throw new RuntimeException( e );
		}
	}

}
