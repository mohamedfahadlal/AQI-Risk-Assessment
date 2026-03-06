package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @FXML
    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both email and password.");
            return;
        }

        // Updated to match SignUpController: uses 'user_id', 'username', and 'password_hash'
        String query = "SELECT user_id, username FROM users WHERE email = ? AND password_hash = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, email);
            // Hash the input so it matches the SHA-256 strings in your database
            pstmt.setString(2, hashPassword(password));

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Fetch user_id as String to match your UserSession singleton
                String userId = rs.getString("user_id");
                String username = rs.getString("username");

                // Save to session
                UserSession.setUserId(userId);
                UserSession.setUsername(username);

                System.out.println("User " + username + " logged in successfully.");

                // Updated path to match your specific file hierarchy
                SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard");
            } else {
                statusLabel.setText("Invalid email or password.");
            }

        } catch (SQLException e) {
            statusLabel.setText("Database connection error.");
            e.printStackTrace();
        }
    }

    /**
     * Hashes the password using SHA-256 to match the SignUpController logic.
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    @FXML
    private void goToSignUp() {
        SceneManager.switchScene("/fxml/SignUp.fxml");
    }

    @FXML
    private void goToAbout() {
        SceneManager.switchScene("/fxml/About.fxml");
    }
}