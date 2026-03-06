package com.aqi.controllers;

import com.aqi.services.ApiService;
import com.aqi.utils.UserSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

// Uses the same Gson already in pom.xml — no new dependency needed
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        editButton.setOnAction(e -> navigateTo("/views/UpdateHealthProfile.fxml"));
        backButton.setOnAction(e -> navigateTo("/views/HealthProfile.fxml"));
        new Thread(this::loadProfile).start();
    }

    // ── Load and display profile ──────────────────────────────────────────

    private void loadProfile() {
        String userId = UserSession.getUserId();
        if (userId == null) return;

        String json = ApiService.loadProfile(userId);
        if (json == null) {
            Platform.runLater(() -> usernameLabel.setText("No profile found"));
            return;
        }

        try {
            JsonObject p = JsonParser.parseString(json).getAsJsonObject();

            Platform.runLater(() -> {

                // ── Avatar initials ───────────────────────────────────────
                String username = UserSession.getUsername();
                if (username != null && !username.isEmpty()) {
                    usernameLabel.setText(username);
                    String initials = username.length() >= 2
                            ? username.substring(0, 2).toUpperCase()
                            : username.toUpperCase();
                    avatarInitials.setText(initials);
                } else {
                    usernameLabel.setText("My Profile");
                    avatarInitials.setText("MP");
                }

                // ── Age ───────────────────────────────────────────────────
                if (has(p, "dob")) {
                    LocalDate dob = LocalDate.parse(p.get("dob").getAsString());
                    int age = Period.between(dob, LocalDate.now()).getYears();
                    ageBadge.setText("Age " + age);
                }

                // ── Gender ────────────────────────────────────────────────
                if (has(p, "gender")) {
                    String gender = p.get("gender").getAsString();
                    genderBadge.setText(genderEmoji(gender) + "  " + gender);

                    if ("Female".equals(gender)) {
                        pregnantCard.setVisible(true);
                        pregnantCard.setManaged(true);
                        if (has(p, "is_pregnant")) {
                            pregnantValue.setText(
                                    p.get("is_pregnant").getAsBoolean() ? "✅  Yes" : "❌  No"
                            );
                        }
                    }
                }

                // ── Location ──────────────────────────────────────────────
                if (has(p, "location")) {
                    locationBadge.setText("📍  " + p.get("location").getAsString());
                }

                // ── Breathing severity ────────────────────────────────────
                if (has(p, "asthma_breathing")) {
                    String level = p.get("asthma_breathing").getAsString();
                    breathingValue.setText(level);
                    breathingValue.setStyle(breathingColor(level));
                }

                // ── Specific conditions ───────────────────────────────────
                if (has(p, "breathing_conditions")) {
                    JsonArray conditions = p.get("breathing_conditions").getAsJsonArray();
                    if (!conditions.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < conditions.size(); i++) {
                            sb.append(conditions.get(i).getAsString());
                            if (i < conditions.size() - 1) sb.append(", ");
                        }
                        conditionsValue.setText(sb.toString());
                    } else {
                        conditionsValue.setText("None specified");
                    }
                }

                // ── Smoker ────────────────────────────────────────────────
                if (has(p, "is_smoker")) {
                    smokerValue.setText(
                            p.get("is_smoker").getAsBoolean() ? "✅  Yes" : "❌  No"
                    );
                }

                // ── Allergic ──────────────────────────────────────────────
                if (has(p, "is_allergic")) {
                    allergicValue.setText(
                            p.get("is_allergic").getAsBoolean() ? "✅  Yes" : "❌  No"
                    );
                }

                // ── Last updated ──────────────────────────────────────────
                if (has(p, "updated_at")) {
                    try {
                        String raw = p.get("updated_at").getAsString();
                        LocalDate d = LocalDate.parse(raw.substring(0, 10));
                        String time = raw.substring(11, 16);
                        updatedAtValue.setText(dateFormatter.format(d) + " at " + time);
                    } catch (Exception e) {
                        updatedAtValue.setText("—");
                    }
                }
            });

        } catch (Exception e) {
            System.err.println("Failed to parse profile: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean has(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull();
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

    // ── Navigation ────────────────────────────────────────────────────────

    private void navigateTo(String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = (Stage) backButton.getScene().getWindow();
            Scene scene = new Scene(root,
                    stage.getScene().getWidth(),
                    stage.getScene().getHeight());
            scene.getStylesheets().add(
                    getClass().getResource("/styles/theme.css").toExternalForm()
            );
            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
