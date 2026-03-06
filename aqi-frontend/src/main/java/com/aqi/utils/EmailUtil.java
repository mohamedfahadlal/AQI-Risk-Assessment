package com.aqi.utils;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class EmailUtil {

    // ⚠️ CRITICAL: Standard Gmail passwords do NOT work here anymore.
    // You MUST go to your Google Account -> Security -> 2-Step Verification -> App Passwords
    // Generate a new App Password and paste that 16-letter code below.
    private static final String SENDER_EMAIL = "aiqi.noreply@gmail.com";
    private static final String APP_PASSWORD = "vyzhptwxbrakapki";

    public static boolean sendOtpEmail(String recipientEmail, String otp) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, APP_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject("Your AQI Dashboard Verification Code");

            String htmlContent = "<h3>Welcome to AQI Dashboard!</h3>"
                    + "<p>Your One-Time Password (OTP) for registration is: <b>" + otp + "</b></p>"
                    + "<p>Please enter this code in the app to complete your sign-up.</p>";

            message.setContent(htmlContent, "text/html");

            Transport.send(message);
            return true; // Email sent successfully!

        } catch (MessagingException e) {
            e.printStackTrace();
            return false; // Email failed to send
        }
    }
}