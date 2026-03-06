package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
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

        String hashedPassword = hashPassword(password);
        String query = "SELECT user_id, username FROM users WHERE email = ? AND password_hash = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, email);
            pstmt.setString(2, hashedPassword);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String userId   = rs.getString("user_id");
                String username = rs.getString("username");

                UserSession.setUserId(userId);
                UserSession.setUsername(username);

                System.out.println("User " + username + " logged in successfully.");

                // If no health profile yet → go setup, else → Dashboard
                if (!hasHealthProfile(userId)) {
                    SceneManager.switchScene("/views/HealthProfile.fxml", "Health Profile");
                } else {
                    SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard");
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

    @FXML private void goToSignUp() { SceneManager.switchScene("/fxml/SignUp.fxml"); }
    @FXML private void goToAbout()  { SceneManager.switchScene("/fxml/About.fxml"); }
}
