package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

public class SignUpController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label statusLabel;

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

        // 2. NEW: Validate Password Strength
        if (!isValidPassword(password)) {
            showError("Password must be atleast 8 chars with an uppercase, lowercase, number, and special character.");
            return;
        }

        // 3. Check if passwords match
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match!");
            return;
        }

        // 4. Database Insertion
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

    @FXML
    private void goToLogin() {
        SceneManager.switchScene("/fxml/Login.fxml");
    }
    @FXML
    private void goToAbout() {
        SceneManager.switchScene("/fxml/About.fxml");
    }
}