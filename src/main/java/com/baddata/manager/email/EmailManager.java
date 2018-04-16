/**
 * Copyright (c) 2016 by Baddata
 * All rights reserved.
 */
package com.baddata.manager.email;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.baddata.api.dto.user.User;
import com.baddata.api.factory.ApiSessionContext;
import com.baddata.log.Logger;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.baddata.util.FileUtil;

/**
 *
 * Email manager
 *
 */
public class EmailManager {

    private static Logger logger = Logger.getLogger(EmailManager.class.getName());

    private static EmailManager ref;

    private Session mailSession = null;
    private String smtpSupportEmail = "";
    private String smtpSupportEmailName = "";
    private String smtpHost = "";
    private int smtpPort = 465;
    private String smtpUsername = "";
    private String smtpPassword = "";
    private String baseURL = "";
    private String companyName = "";
    
    private String defaultSubjectLine = "BadData Support";

    /**
     * Singleton instance
     * @return
     */
    public static EmailManager getInstance() {
        if (ref == null) {
            synchronized(EmailManager.class) {
                if ( ref == null ) {
                    ref = new EmailManager();
                }
            }
        }
        return ref;
    }

    // private constructor to ensure singleton usage
    protected EmailManager() {
        initializeEmailProperties();
    }

    private void initializeEmailProperties() {

        // production smtp properties
        smtpSupportEmail = AppUtil.get( "smtp.support.email" );
        smtpSupportEmailName = AppUtil.get( "smtp.support.email.name" );
        smtpHost = AppUtil.get( "smtp.host" );
        smtpPort = AppUtil.getAsInt( "smtp.port", 25 );
        smtpUsername = AppUtil.get( "smtp.username" );
        smtpPassword = AppUtil.get( "smtp.password" );
        baseURL = AppUtil.get( "origin.url" );
        companyName = AppUtil.get( "company.name" );

        // Create a Properties object to contain connection configuration information.
        Properties props = System.getProperties();
        props.put("mail.transport.protocol", "smtps");
        props.put("mail.smtp.port", smtpPort);

        // Set properties indicating that we want to use STARTTLS to encrypt the connection.
        // The SMTP session will begin on an unencrypted connection, and then the client
        // will issue a STARTTLS command to upgrade to an encrypted connection.
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");

        // Create a Session object to represent a mail session with the specified properties.
        mailSession = Session.getDefaultInstance( props );
    }

    /**
     * Send an email to the user requesting to change their password
     * @param toAddress is the user's email address requesting to change their password
     * @throws MessagingException
     * @throws UnsupportedEncodingException
     */
    public void sendWelcomeEmail(User toUser) throws UnsupportedEncodingException, MessagingException {
        File emailTemplate = FileUtil.getEmailTemplate(AppConstants.WELCOME_NEW_USER_EMAIL_TEMPLATE);
        
        if (ApiSessionContext.sessionUrl != null && !ApiSessionContext.sessionUrl.equalsIgnoreCase(baseURL)) {
            baseURL = ApiSessionContext.sessionUrl;
        }
        String welcomeLink = baseURL + "#/welcome?authToken=" + toUser.getTokenObj();
        
        String emailContent;
        try {
            emailContent = FileUtils.readFileToString(emailTemplate, "UTF-8");
            emailContent = emailContent.replace("[welcome-email-link]", welcomeLink);
            String usersFirstNameOrUsername = toUser.getUsername();
            if ( StringUtils.isNotBlank( toUser.getFullname() ) ) {
                usersFirstNameOrUsername = toUser.getFullname().split(" ")[0];
            }
            emailContent = emailContent.replace("[users_first_name_or_username]", usersFirstNameOrUsername);
            emailContent = emailContent.replace("[base_url]", baseURL);
            
            logger.info("Sending welcome email to '" + toUser.getEmail() + "', username '" + toUser.getUsername() + 
                    "', with token '" + toUser.getTokenObj() + "', welcome link: '" + welcomeLink + "'.");
            
            // send the email
            this.sendEmail(toUser, null /* ccUser */, "Welcome to " + companyName + "!", emailContent);
        } catch (Exception e) {
            if ( !AppUtil.isUnitTesting() ) {
                logger.error("Failed to read welcome email template for new user '" + toUser.getEmail() + "'.", e);
            }
        }
        
    }

    public void sendPasswordUpdateEmail(User toUser) throws UnsupportedEncodingException, MessagingException {
        
        File emailTemplate = FileUtil.getEmailTemplate(AppConstants.FORGOT_PASSWORD_EMAIL_TEMPLATE);

        // create the change password link
        if (ApiSessionContext.sessionUrl != null && !ApiSessionContext.sessionUrl.equalsIgnoreCase(baseURL)) {
            baseURL = ApiSessionContext.sessionUrl;
        }
        String changePasswordLink = baseURL + "#/resetpass?authToken=" + toUser.getTokenObj();
        
        String emailContent;
        try {
            emailContent = FileUtils.readFileToString(emailTemplate, "UTF-8");
            emailContent = emailContent.replace("[change_password_link]", changePasswordLink);
            String usersFirstNameOrUsername = toUser.getUsername();
            if ( StringUtils.isNotBlank( toUser.getFullname() ) ) {
                usersFirstNameOrUsername = toUser.getFullname().split(" ")[0];
            }
            emailContent = emailContent.replace("[users_first_name_or_username]", usersFirstNameOrUsername);
            emailContent = emailContent.replace("[base_url]", baseURL);
            
            logger.info("Sending password reset email to '" + toUser.getUsername() + "' with token '" + toUser.getTokenObj() + "'.");

            // send the email
            this.sendEmail(toUser, null /* ccUser */, companyName + " Password Support", emailContent);
        } catch (Exception e) {
            if ( !AppUtil.isUnitTesting() ) {
                logger.error("Failed to read forgot password email template for user '" + toUser.getEmail() + "'.", e);
            }
        }
    }

    public void sendEmail(User toUser, User ccUser, String subject, String message) throws UnsupportedEncodingException, MessagingException {
        if ( StringUtils.isBlank(subject) ) {
            subject = defaultSubjectLine;
        }
        // Create a message with the specified information.
        MimeMessage msg = new MimeMessage(mailSession);

        msg.setFrom(new InternetAddress(smtpSupportEmail, smtpSupportEmailName));
        if ( ccUser != null && !ccUser.getEmail().equals(toUser.getEmail()) ) {
            msg.setRecipient(
                Message.RecipientType.CC, new InternetAddress( ccUser.getEmail(), ccUser.getFullname() ) );
        }
        msg.setRecipient( Message.RecipientType.TO, new InternetAddress( toUser.getEmail(), toUser.getFullname() ) );
        msg.setSubject(subject);


        StringBuffer sb = new StringBuffer();
        sb.append(message);

        msg.setContent(sb.toString(), "text/html");

        this.transportEmail(msg);
    }

    private void transportEmail(MimeMessage msg) throws MessagingException {
        // Create a transport.
        Transport transport = mailSession.getTransport();

        // Send the message.
        try {

            // Connect to Amazon SES using the SMTP username and password you specified above.
            transport.connect(smtpHost, smtpUsername, smtpPassword);

            // Send the email.
            transport.sendMessage(msg, msg.getAllRecipients());
            System.out.println("Email sent!");
        } catch (Exception e) {
            logger.error("Failed to send email.", e);
        } finally {
            // Close and terminate the connection.
            transport.close();
        }
    }

}
