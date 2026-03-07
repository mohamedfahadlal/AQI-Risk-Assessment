package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Random;

public class ForgotPasswordController {

    @FXML private VBox emailBox;
    @FXML private VBox resetBox;
    @FXML private TextField emailField;
    @FXML private TextField otpField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;

    // Criteria Labels
    @FXML private Label lengthLabel;
    @FXML private Label upperLabel;
    @FXML private Label numberLabel;
    @FXML private Label specialLabel;
    @FXML private TextField confirmPasswordVisibleField;
    @FXML private javafx.scene.control.Button toggleConfirmBtn;

    private boolean isConfirmVisible = false; // Tracks the state

    @FXML private Label statusLabel;

    private String generatedOtp;
    private String verifiedEmail;

    // --- YOUR SYSTEM EMAIL SETTINGS ---
    private final String SYSTEM_EMAIL = "aiqi.noreply@gmail.com"; // Replace with your Gmail
    private final String SYSTEM_PASSWORD = "ehhfdtvbwpawzopg"; // Replace with your 16-digit app password

    @FXML
    public void initialize() {
        // 1. Bind the confirm fields exactly ONCE when the screen loads to prevent UI freezing
        confirmPasswordVisibleField.textProperty().bindBidirectional(confirmPasswordField.textProperty());

        // 2. Add real-time listener to check password criteria as the user types
        newPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            validatePassword(newValue);
        });
    }

    @FXML
    private void toggleConfirmVisibility() {
        isConfirmVisible = !isConfirmVisible;

        if (isConfirmVisible) {
            // Show plain text, hide dots
            confirmPasswordVisibleField.setVisible(true);
            confirmPasswordVisibleField.setManaged(true);
            confirmPasswordField.setVisible(false);
            confirmPasswordField.setManaged(false);
            toggleConfirmBtn.setText("🙈"); // Or change to "Hide"
        } else {
            // Show dots, hide plain text
            confirmPasswordVisibleField.setVisible(false);
            confirmPasswordVisibleField.setManaged(false);
            confirmPasswordField.setVisible(true);
            confirmPasswordField.setManaged(true);
            toggleConfirmBtn.setText("👁"); // Or change to "Show"
        }
    }

    private boolean validatePassword(String password) {
        boolean hasLength = password.length() >= 8;
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasNumber = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[@$!%*?&].*");

        updateLabelStyle(lengthLabel, hasLength);
        updateLabelStyle(upperLabel, hasUpper);
        updateLabelStyle(numberLabel, hasNumber);
        updateLabelStyle(specialLabel, hasSpecial);

        return hasLength && hasUpper && hasNumber && hasSpecial;
    }

    private void updateLabelStyle(Label label, boolean isValid) {
        if (isValid) {
            label.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11px;"); // Green
        } else {
            label.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;"); // Gray
        }
    }

    @FXML
    private void handleSendOtp() {
        String email = emailField.getText().trim();
        if (email.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
            statusLabel.setText("Please enter your email.");
            return;
        }

        statusLabel.setStyle("-fx-text-fill: #007bff;");
        statusLabel.setText("Checking email and sending OTP...");

        // Run network/DB tasks on a background thread to keep UI from freezing
        new Thread(() -> {
            if (checkIfEmailExists(email)) {
                generatedOtp = String.format("%06d", new Random().nextInt(999999));
                verifiedEmail = email;

                boolean emailSent = sendEmail(email, generatedOtp);

                Platform.runLater(() -> {
                    if (emailSent) {
                        statusLabel.setText("");
                        emailBox.setVisible(false); emailBox.setManaged(false);
                        resetBox.setVisible(true);  resetBox.setManaged(true);
                    } else {
                        statusLabel.setStyle("-fx-text-fill: #ef4444;");
                        statusLabel.setText("Failed to send email. Check settings.");
                    }
                });
            } else {
                Platform.runLater(() -> {
                    statusLabel.setStyle("-fx-text-fill: #ef4444;");
                    statusLabel.setText("Email not found in our system.");
                });
            }
        }).start();
    }

    @FXML
    private void handleResetPassword() {
        String enteredOtp = otpField.getText().trim();
        String newPassword = newPasswordField.getText();

        // Use your visible text field if the Eye is active, otherwise use the hidden one
        String confirmPassword = isConfirmVisible ?
                confirmPasswordVisibleField.getText() :
                confirmPasswordField.getText();

        if (enteredOtp.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
            statusLabel.setText("Please fill in all fields.");
            return;
        }

        if (!enteredOtp.equals(generatedOtp)) {
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
            statusLabel.setText("Invalid OTP. Please try again.");
            return;
        }

        // 1. Check if it meets criteria
        if (!validatePassword(newPassword)) {
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
            statusLabel.setText("Password does not meet all criteria.");
            return;
        }

        // 2. Check if passwords match
        if (!newPassword.equals(confirmPassword)) {
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
            statusLabel.setText("Passwords do not match.");
            return;
        }

        // Show a loading status so the user knows something is happening
        statusLabel.setStyle("-fx-text-fill: #007bff;");
        statusLabel.setText("Updating password... please wait.");

        // 3. Move the heavy database task to a background thread!
        new Thread(() -> {
            String hashedPassword = hashPassword(newPassword);
            updatePasswordInDatabase(verifiedEmail, hashedPassword);
        }).start();
    }

    private boolean checkIfEmailExists(String email) {
        String query = "SELECT 1 FROM users WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updatePasswordInDatabase(String email, String hashedPw) {
        String query = "UPDATE users SET password_hash = ? WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, hashedPw);
            pstmt.setString(2, email);
            pstmt.executeUpdate();

            Platform.runLater(() -> {
                statusLabel.setStyle("-fx-text-fill: #10b981;"); // Green success
                statusLabel.setText("Password reset successfully! Returning to login...");

                // Switch back to login after 2 seconds
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override public void run() {
                        Platform.runLater(() -> goToLogin());
                    }
                }, 2000);
            });

        } catch (SQLException e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                statusLabel.setStyle("-fx-text-fill: #ef4444;");
                statusLabel.setText("Database error updating password.");
            });
        }
    }

    private boolean sendEmail(String recipient, String otp) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SYSTEM_EMAIL, SYSTEM_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SYSTEM_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject("Your Password Reset Code");
            message.setText("Hello,\n\nYour 6-digit password reset code is: " + otp +
                    "\n\nIf you did not request this, please ignore this email.");
            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    @FXML
    private void goToLogin() {
        SceneManager.switchScene("/fxml/Login.fxml", "Login");
    }
}