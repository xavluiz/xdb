/**
 * Copyright (c) 2017 by Baddata
 * All rights reserved.
 */
package com.baddata.manager.email;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * smtp.support.email=admin@baddata.com
smtp.support.email.name=Andrew
smtp.host=email-smtp.us-west-2.amazonaws.com
smtp.port=587
smtp.username=AKIAJ5QTFCFNBDFMCFVQ
smtp.password=AgKjl2nZqy8PAo8rQF7qViLRuHpXnvSCVZxof9rZmXSR
 */

public class AmazonSesSample {

    static final String FROM = "admin@baddata.com";   // Replace with your "From" address. This address must be verified.
    static final String TO = "admin@baddata.com";  // Replace with a "To" address. If your account is still in the
                                                       // sandbox, this address must be verified.

    static final String BODY = "This email was sent through the Amazon SES SMTP interface by using Java.";
    static final String SUBJECT = "Amazon SES test (SMTP interface accessed using Java)";

    // Supply your SMTP credentials below. Note that your SMTP credentials are different from your AWS credentials.
    static final String SMTP_USERNAME = "AKIAJ5QTFCFNBDFMCFVQ";  // Replace with your SMTP username.
    static final String SMTP_PASSWORD = "AgKjl2nZqy8PAo8rQF7qViLRuHpXnvSCVZxof9rZmXSR";  // Replace with your SMTP password.

    // Amazon SES SMTP host name. This example uses the US West (Oregon) region.
    static final String HOST = "email-smtp.us-west-2.amazonaws.com";

    // The port you will connect to on the Amazon SES SMTP endpoint. We are choosing port 25 because we will use
    // STARTTLS to encrypt the connection.
    static final int PORT = 25;

    private static AmazonSesSample ref;

    /**
     * Singleton instance
     * @return
     */
    public static AmazonSesSample getInstance() {
        if (ref == null) {
            synchronized(AmazonSesSample.class) {
                if ( ref == null ) {
                    ref = new AmazonSesSample();
                }
            }
        }
        return ref;
    }

    public void sendMail1() throws Exception {

        // Create a Properties object to contain connection configuration information.
        Properties props = System.getProperties();
        props.put("mail.transport.protocol", "smtps");
        props.put("mail.smtp.port", PORT);

        // Set properties indicating that we want to use STARTTLS to encrypt the connection.
        // The SMTP session will begin on an unencrypted connection, and then the client
        // will issue a STARTTLS command to upgrade to an encrypted connection.
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");

        // Create a Session object to represent a mail session with the specified properties.
        Session session = Session.getDefaultInstance(props);

        // Create a message with the specified information.
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(FROM));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(TO));
        msg.setSubject(SUBJECT);
        msg.setContent(BODY + " : smtps","text/plain");

        // Create a transport.
        Transport transport = session.getTransport();

        // Send the message.
        try
        {
            System.out.println("Attempting to send an email through the Amazon SES SMTP interface using smtps..");

            // Connect to Amazon SES using the SMTP username and password you specified above.
            transport.connect(HOST, SMTP_USERNAME, SMTP_PASSWORD);

            // Send the email.
            transport.sendMessage(msg, msg.getAllRecipients());
            System.out.println("Email sent!");
        }
        catch (Exception ex) {
            System.out.println("The email was not sent.");
            System.out.println("Error message: " + ex.getMessage());
        }
        finally
        {
            // Close and terminate the connection.
            transport.close();
        }
    }
}
