package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import com.aqi.utils.EmailUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

public class SignUpController {

    @FXML private TextField nameField;
    @FXML private TextField emailField; // Replaced duplicate emailInput with this
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label statusLabel;
    @FXML private TextField otpInput;
    @FXML private Button sendOtpBtn;

    // This will hold the generated OTP temporarily
    private String generatedOtp;

    @FXML
    private void handleSignUp() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // 1. Check if any fields are empty
        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }

        // 2. Validate Password Strength
        if (!isValidPassword(password)) {
            showError("Password must be at least 8 chars with an uppercase, lowercase, number, and special character.");
            return;
        }

        // 3. Check if passwords match
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match!");
            return;
        }

        // 4. Check OTP Verification
        String userOtp = otpInput.getText().trim();
        if (generatedOtp == null || !generatedOtp.equals(userOtp)) {
            showAlert(Alert.AlertType.ERROR, "Verification Failed", "The OTP entered is incorrect or expired.");
            return; // Stop registration
        }

        // 5. Database Insertion
        String query = "INSERT INTO users (full_name, email, password) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, name);
            pstmt.setString(2, email);
            pstmt.setString(3, password);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                statusLabel.setTextFill(Color.GREEN);
                statusLabel.setText("Account created successfully! Redirecting...");

                // Pause for 1.5 seconds, then go to login
                new Thread(() -> {
                    try {
                        Thread.sleep(1500);
                        javafx.application.Platform.runLater(this::goToLogin);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            }

        } catch (SQLIntegrityConstraintViolationException e) {
            showError("An account with this email already exists.");
        } catch (SQLException e) {
            showError("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleSendOtp() {
        String email = emailField.getText().trim(); // Using emailField here now

        if (email.isEmpty() || !email.contains("@")) {
            showAlert(Alert.AlertType.ERROR, "Invalid Email", "Please enter a valid email address first.");
            return;
        }

        // Generate a random 6-digit OTP
        generatedOtp = String.format("%06d", (int) (Math.random() * 1000000));

        // Change button text to show it's working
        sendOtpBtn.setText("Sending...");
        sendOtpBtn.setDisable(true);

        // Run the email sending on a background thread so the UI doesn't freeze
        new Thread(() -> {
            boolean success = EmailUtil.sendOtpEmail(email, generatedOtp);

            // Update the UI back on the main JavaFX thread
            javafx.application.Platform.runLater(() -> {
                if (success) {
                    showAlert(Alert.AlertType.INFORMATION, "OTP Sent", "An OTP has been sent to your email!");
                    sendOtpBtn.setText("Sent ✓");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to send OTP. Check your internet or email settings.");
                    sendOtpBtn.setText("Send OTP");
                    sendOtpBtn.setDisable(false);
                }
            });
        }).start();
    }

    /**
     * Uses a Regular Expression (Regex) to enforce password rules:
     * - (?=.*[a-z]) : At least one lowercase letter
     * - (?=.*[A-Z]) : At least one uppercase letter
     * - (?=.*\\d)   : At least one number
     * - (?=.*[^a-zA-Z\\d]) : At least one special character (not a letter or number)
     * - .{8,}       : Minimum 8 characters long
     */
    private boolean isValidPassword(String password) {
        String regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).{8,}$";
        return password.matches(regex);
    }

    private void showError(String message) {
        statusLabel.setTextFill(Color.web("#ef4444")); // Red color
        statusLabel.setText(message);
    }

    // NEW METHOD: Handles the pop-up boxes for OTP notifications
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null); // Removes the extra header text area
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void goToLogin() {
        SceneManager.switchScene("/fxml/Login.fxml");
    }

    @FXML
    private void goToAbout() {
        SceneManager.switchScene("/fxml/About.fxml");
    }
}