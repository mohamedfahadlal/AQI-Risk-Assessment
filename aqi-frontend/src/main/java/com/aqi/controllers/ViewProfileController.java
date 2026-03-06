package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.sql.*;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

public class ViewProfileController {

    @FXML private Circle  avatarCircle;
    @FXML private Label   avatarInitials;
    @FXML private Label   usernameLabel;
    @FXML private Label   ageBadge;
    @FXML private Label   genderBadge;
    @FXML private Label   locationBadge;
    @FXML private Label   breathingValue;
    @FXML private Label   conditionsValue;
    @FXML private Label   smokerValue;
    @FXML private Label   allergicValue;
    @FXML private VBox    pregnantCard;
    @FXML private Label   pregnantValue;
    @FXML private Label   updatedAtValue;
    @FXML private Button  editButton;
    @FXML private Button  backButton;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    public void initialize() {
        editButton.setOnAction(e -> SceneManager.switchScene("/views/UpdateHealthProfile.fxml", "Update Profile"));
        backButton.setOnAction(e -> SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard"));
        new Thread(this::loadProfile).start();
    }

    private void loadProfile() {
        String userId = UserSession.getUserId();
        if (userId == null) return;

        String query = """
            SELECT hp.*, u.username
            FROM health_profiles hp
            JOIN users u ON u.user_id = hp.user_id
            WHERE hp.user_id = ?::uuid
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (!rs.next()) {
                Platform.runLater(() -> usernameLabel.setText("No profile found"));
                return;
            }

            String username    = rs.getString("username");
            Date dob           = rs.getDate("dob");
            String gender      = rs.getString("gender");
            String location    = rs.getString("location");
            String breathing   = rs.getString("asthma_breathing");
            boolean smoker     = rs.getBoolean("is_smoker");
            boolean allergic   = rs.getBoolean("is_allergic");
            boolean pregnant   = rs.getBoolean("is_pregnant");
            Array conditions   = rs.getArray("breathing_conditions");
            Timestamp updatedAt = rs.getTimestamp("updated_at");

            Platform.runLater(() -> {

                // ── Avatar ────────────────────────────────────────────────
                usernameLabel.setText(username != null ? username : "My Profile");
                if (username != null && username.length() >= 2) {
                    avatarInitials.setText(username.substring(0, 2).toUpperCase());
                } else if (username != null) {
                    avatarInitials.setText(username.toUpperCase());
                }

                // ── Age ───────────────────────────────────────────────────
                if (dob != null) {
                    int age = Period.between(dob.toLocalDate(), LocalDate.now()).getYears();
                    ageBadge.setText("Age " + age);
                }

                // ── Gender ────────────────────────────────────────────────
                if (gender != null) {
                    genderBadge.setText(genderEmoji(gender) + "  " + gender);
                    if ("Female".equals(gender)) {
                        pregnantCard.setVisible(true);
                        pregnantCard.setManaged(true);
                        pregnantValue.setText(pregnant ? "✅  Yes" : "❌  No");
                    }
                }

                // ── Location ──────────────────────────────────────────────
                if (location != null) locationBadge.setText("📍  " + location);

                // ── Breathing ─────────────────────────────────────────────
                if (breathing != null) {
                    breathingValue.setText(breathing);
                    breathingValue.setStyle(breathingColor(breathing));
                }

                // ── Conditions ────────────────────────────────────────────
                if (conditions != null) {
                    try {
                        String[] arr = (String[]) conditions.getArray();
                        conditionsValue.setText(arr.length > 0
                                ? String.join(", ", arr)
                                : "None specified");
                    } catch (SQLException e) {
                        conditionsValue.setText("None specified");
                    }
                }

                // ── Smoker / Allergic ─────────────────────────────────────
                smokerValue.setText(smoker   ? "✅  Yes" : "❌  No");
                allergicValue.setText(allergic ? "✅  Yes" : "❌  No");

                // ── Last updated ──────────────────────────────────────────
                if (updatedAt != null) {
                    LocalDate d = updatedAt.toLocalDateTime().toLocalDate();
                    String time = updatedAt.toLocalDateTime().toLocalTime()
                            .format(DateTimeFormatter.ofPattern("HH:mm"));
                    updatedAtValue.setText(dateFormatter.format(d) + " at " + time);
                }
            });

        } catch (SQLException e) {
            System.err.println("Failed to load profile: " + e.getMessage());
            Platform.runLater(() -> usernameLabel.setText("Error loading profile"));
        }
    }

    private String genderEmoji(String gender) {
        return switch (gender) {
            case "Male"   -> "👨";
            case "Female" -> "👩";
            default       -> "🧑";
        };
    }

    private String breathingColor(String level) {
        return switch (level) {
            case "Mild"     -> "-fx-text-fill: #d97706; -fx-font-weight: bold;";
            case "Moderate" -> "-fx-text-fill: #ea580c; -fx-font-weight: bold;";
            case "Severe"   -> "-fx-text-fill: #dc2626; -fx-font-weight: bold;";
            default         -> "-fx-text-fill: #16a34a; -fx-font-weight: bold;";
        };
    }
}
