package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DashboardController {

    // These match the fx:id attributes in Dashboard.fxml
    @FXML private Label welcomeLabel;
    @FXML private Label locationLabel;
    @FXML private Label aqiValueLabel;
    @FXML private Label aqiStatusLabel;
    @FXML private Label recommendationLabel;

    @FXML
    public void initialize() {
        // This runs automatically when the Dashboard is loaded
        loadUserData();
        loadAqiData(); 
    }

    /**
     * Fetches the logged-in user's name and location from the MySQL database.
     */
    private void loadUserData() {
        int userId = UserSession.getUserId();
        if (userId == -1) {
            welcomeLabel.setText("Welcome, Guest");
            return;
        }

        String query = "SELECT full_name, location FROM users WHERE id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String name = rs.getString("full_name");
                String location = rs.getString("location");
                
                // Extract just the first name for a friendlier greeting
                String firstName = name.split(" ")[0]; 
                
                welcomeLabel.setText("Welcome, " + firstName);
                locationLabel.setText("Location: " + (location != null && !location.isEmpty() ? location : "Not set"));
            }
            
        } catch (SQLException e) {
            System.err.println("Error loading user data:");
            e.printStackTrace();
        }
    }

    /**
     * Loads AQI data and updates the UI styles based on the severity.
     * Note: Currently using mock data. To be replaced with a live API call.
     */
    private void loadAqiData() {
        // MOCK DATA: For testing the UI. 
        // Try changing this number to 40, 75, or 150 to test the color changes!
        int currentAqi = 75; 
        
        aqiValueLabel.setText(String.valueOf(currentAqi));
        
        // Remove old CSS classes first to prevent them from stacking
        aqiStatusLabel.getStyleClass().removeAll("aqi-good", "aqi-moderate", "aqi-unhealthy");
        
        if (currentAqi <= 50) {
            aqiStatusLabel.setText("Good");
            aqiStatusLabel.getStyleClass().add("aqi-good");
            recommendationLabel.setText("Air quality is satisfactory. Enjoy your outdoor activities!");
        } else if (currentAqi <= 100) {
            aqiStatusLabel.setText("Moderate");
            aqiStatusLabel.getStyleClass().add("aqi-moderate");
            recommendationLabel.setText("Unusually sensitive individuals should consider limiting prolonged outdoor exertion.");
        } else {
            aqiStatusLabel.setText("Unhealthy");
            aqiStatusLabel.getStyleClass().add("aqi-unhealthy");
            recommendationLabel.setText("Everyone may begin to experience health effects. Limit outdoor activities.");
        }
    }

    /**
     * Logs the user out, clears the session, and returns to the Login screen.
     */
    @FXML
    private void handleLogout() {
        UserSession.clearSession();
        SceneManager.switchScene("/fxml/Login.fxml");
    }
}