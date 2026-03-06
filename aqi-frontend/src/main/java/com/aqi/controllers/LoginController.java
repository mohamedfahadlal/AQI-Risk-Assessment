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

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @FXML
    private void handleLogin() {
        String email    = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both email and password.");
            return;
        }

        String query = "SELECT user_id, username, password_hash FROM users WHERE email = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String savedHash = rs.getString("password_hash");
                String userId    = rs.getString("user_id");
                String username  = rs.getString("username");

                // SHA-256 to match SignUpController
                if (hashPassword(password).equals(savedHash)) {
                    UserSession.setUserId(userId);
                    UserSession.setUsername(username);
                    System.out.println("User " + username + " logged in successfully.");

                    if (!hasHealthProfile(userId)) {
                        SceneManager.switchScene("/views/HealthProfile.fxml", "Health Profile");
                    } else {
                        SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard");
                    }
                } else {
                    statusLabel.setText("Invalid email or password.");
                }
            } else {
                statusLabel.setText("Invalid email or password.");
            }

        } catch (SQLException e) {
            statusLabel.setText("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean hasHealthProfile(String userId) {
        String query = "SELECT 1 FROM health_profiles WHERE user_id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    private String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

    @FXML private void goToSignUp() { SceneManager.switchScene("/fxml/SignUp.fxml", "Sign Up"); }
    @FXML private void goToAbout()  { SceneManager.switchScene("/fxml/About.fxml", "About Us"); }
}