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
    private static final String APP_PASSWORD = "vdpycgjauurlwgon";

    public static boolean sendOtpEmail(String recipientEmail, String otp) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

// ADD THIS LINE: This forces the correct security protocol and usually fixes the [EOF] error!
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

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
            message.setSubject("Your AiQI Verification Code");


            message.setSubject("Welcome to AiQI! Your Verification Code 🌬️");

            String htmlBody = "<div style='font-family: Arial, sans-serif; color: #333333; max-width: 500px; margin: 0 auto; padding: 25px; border: 1px solid #e5e7eb; border-radius: 10px; background-color: #f9fafb;'>"
                    + "<h2 style='color: #0ea5e9; margin-top: 0;'>Welcome to the AiQI App!</h2>"
                    + "<p style='font-size: 16px; line-height: 1.6;'>Hi there,</p>"
                    + "<p style='font-size: 16px; line-height: 1.6;'>We are thrilled to have you on board! To finish setting up your account and start tracking real-time air quality, please enter the verification code below:</p>"

                    // This centers the OTP, makes it huge, bold, blue, and spaces the numbers out
                    + "<div style='text-align: center; margin: 35px 0; padding: 15px; background-color: #ffffff; border-radius: 8px; border: 1px dashed #0ea5e9;'>"
                    + "<span style='font-size: 36px; font-weight: bold; color: #0ea5e9; letter-spacing: 8px;'>" + otp + "</span>"
                    + "</div>"

                    + "<p style='font-size: 14px; color: #6b7280;'><em>If you didn't request this code, you can safely ignore this email.</em></p>"
                    + "<br>"
                    + "<p style='font-size: 16px; margin-bottom: 0;'>Breathe easy,<br><strong style='color: #0ea5e9;'>The AiQI Team</strong></p>"
                    + "</div>";

// 3. IMPORTANT: Tell JavaMail to send it as HTML, not plain text!
            message.setContent(htmlBody, "text/html; charset=utf-8");

            Transport.send(message);
            return true; // Email sent successfully!

        } catch (MessagingException e) {
            e.printStackTrace();
            return false; // Email failed to send
        }
    }
}