package com.aqi.controllers;

import com.aqi.services.ApiService;
import com.aqi.utils.UserSession;
import com.google.gson.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class UpdateHealthProfileController {

    @FXML private DatePicker          dobPicker;
    @FXML private Label               ageDisplayLabel;
    @FXML private ComboBox<String>    genderCombo;
    @FXML private TextField           locationField;
    @FXML private ComboBox<String>    breathingCombo;
    @FXML private Label               conditionLabel;
    @FXML private VBox                conditionBox;
    @FXML private CheckBox            asthmaCheck;
    @FXML private CheckBox            copdCheck;
    @FXML private CheckBox            bronchitisCheck;
    @FXML private CheckBox            otherBreathingCheck;
    @FXML private CheckBox            smokerCheck;
    @FXML private CheckBox            allergicCheck;
    @FXML private CheckBox            pregnantCheck;
    @FXML private Label               pregnantLabel;
    @FXML private Label               lastUpdatedLabel;
    @FXML private Button              updateButton;
    @FXML private Button              backButton;
    @FXML private HBox                successBox;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    public void initialize() {

        genderCombo.getItems().addAll("Select Gender", "Male", "Female", "Other");
        genderCombo.setValue("Select Gender");

        breathingCombo.getItems().addAll("None", "Mild", "Moderate", "Severe");
        breathingCombo.setValue("None");

        // DD/MM/YYYY format
        dobPicker.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                if (date != null) return dateFormatter.format(date);
                return "";
            }
            @Override
            public LocalDate fromString(String text) {
                if (text != null && !text.trim().isEmpty()) {
                    try {
                        return LocalDate.parse(text.trim(), dateFormatter);
                    } catch (DateTimeParseException e) {
                        return null;
                    }
                }
                return null;
            }
        });

        // Restrict future dates
        dobPicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisabled(empty || date.isAfter(LocalDate.now()));
            }
        });

        // Hide age label until DOB is entered
        ageDisplayLabel.setVisible(false);
        ageDisplayLabel.setManaged(false);

        // Show age live once DOB is picked
        dobPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                int age = Period.between(newVal, LocalDate.now()).getYears();
                ageDisplayLabel.setText("Age: " + age + " yrs");
                ageDisplayLabel.setVisible(true);
                ageDisplayLabel.setManaged(true);
            } else {
                ageDisplayLabel.setVisible(false);
                ageDisplayLabel.setManaged(false);
            }
        });

        // Show condition checkboxes when severity != None
        breathingCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean show = newVal != null && !newVal.equals("None");
            conditionLabel.setVisible(show);
            conditionLabel.setManaged(show);
            conditionBox.setVisible(show);
            conditionBox.setManaged(show);
            if (!show) {
                asthmaCheck.setSelected(false);
                copdCheck.setSelected(false);
                bronchitisCheck.setSelected(false);
                otherBreathingCheck.setSelected(false);
            }
        });

        // Show pregnant checkbox only for Female
        genderCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isFemale = "Female".equals(newVal);
            pregnantCheck.setVisible(isFemale);
            pregnantCheck.setManaged(isFemale);
            pregnantLabel.setVisible(isFemale);
            pregnantLabel.setManaged(isFemale);
            if (!isFemale) pregnantCheck.setSelected(false);
        });

        updateButton.setOnAction(e -> handleUpdate());
        backButton.setOnAction(e -> goBack());

        // Load existing profile to pre-fill
        new Thread(this::loadExistingProfile).start();
    }

    // ── Load and pre-fill existing profile ───────────────────────────────

    private void loadExistingProfile() {
        String userId = UserSession.getUserId();
        if (userId == null) return;

        String json = ApiService.loadProfile(userId);
        if (json == null) return;

        try {
            JsonObject profile = JsonParser.parseString(json).getAsJsonObject();

            Platform.runLater(() -> {
                if (has(profile, "dob"))
                    dobPicker.setValue(LocalDate.parse(profile.get("dob").getAsString()));
                if (has(profile, "gender"))
                    genderCombo.setValue(profile.get("gender").getAsString());
                if (has(profile, "location"))
                    locationField.setText(profile.get("location").getAsString());
                if (has(profile, "asthma_breathing"))
                    breathingCombo.setValue(profile.get("asthma_breathing").getAsString());
                if (has(profile, "breathing_conditions")) {
                    JsonArray conditions = profile.get("breathing_conditions").getAsJsonArray();
                    for (JsonElement el : conditions) {
                        switch (el.getAsString()) {
                            case "Asthma"             -> asthmaCheck.setSelected(true);
                            case "COPD"               -> copdCheck.setSelected(true);
                            case "Chronic Bronchitis" -> bronchitisCheck.setSelected(true);
                            case "Other"              -> otherBreathingCheck.setSelected(true);
                        }
                    }
                }
                if (has(profile, "is_smoker"))
                    smokerCheck.setSelected(profile.get("is_smoker").getAsBoolean());
                if (has(profile, "is_allergic"))
                    allergicCheck.setSelected(profile.get("is_allergic").getAsBoolean());
                if (has(profile, "is_pregnant"))
                    pregnantCheck.setSelected(profile.get("is_pregnant").getAsBoolean());

                // Show last updated timestamp
                if (has(profile, "updated_at")) {
                    String updatedAt = profile.get("updated_at").getAsString();
                    // Format: 2024-03-05T10:30:00 → "Last updated: 05/03/2024 10:30"
                    try {
                        String datePart = updatedAt.substring(0, 10);
                        String timePart = updatedAt.substring(11, 16);
                        LocalDate d = LocalDate.parse(datePart);
                        lastUpdatedLabel.setText("Last updated: "
                                + dateFormatter.format(d) + " at " + timePart);
                    } catch (Exception e) {
                        lastUpdatedLabel.setText("");
                    }
                }
            });

        } catch (Exception e) {
            System.err.println("Failed to load profile: " + e.getMessage());
        }
    }

    private boolean has(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull();
    }

    // ── Collect selected conditions ───────────────────────────────────────

    private JsonArray getSelectedConditions() {
        JsonArray arr = new JsonArray();
        if (asthmaCheck.isSelected())         arr.add("Asthma");
        if (copdCheck.isSelected())           arr.add("COPD");
        if (bronchitisCheck.isSelected())     arr.add("Chronic Bronchitis");
        if (otherBreathingCheck.isSelected()) arr.add("Other");
        return arr;
    }

    // ── Handle update save ────────────────────────────────────────────────

    private void handleUpdate() {
        if (!validateInputs()) {
            showAlert("Please complete all required fields correctly.");
            return;
        }
        if (!breathingCombo.getValue().equals("None") && getSelectedConditions().size() == 0) {
            showAlert("Please select at least one breathing condition type.");
            return;
        }

        JsonObject json = new JsonObject();
        json.addProperty("userId",          UserSession.getUserId());
        json.addProperty("dob",             dobPicker.getValue().toString());
        json.addProperty("gender",          genderCombo.getValue());
        json.addProperty("location",        locationField.getText().trim());
        json.addProperty("asthmaBreathing", breathingCombo.getValue());
        json.add("breathingConditions",     getSelectedConditions());
        json.addProperty("isSmoker",        smokerCheck.isSelected());
        json.addProperty("isAllergic",      allergicCheck.isSelected());
        json.addProperty("isPregnant",      pregnantCheck.isSelected());

        new Thread(() -> {
            ApiService.saveProfile(json.toString());
            Platform.runLater(this::showSuccess);
        }).start();
    }

    // ── Navigate back to Health Profile page ─────────────────────────────

    private void goBack() {
        try {
            Parent root = FXMLLoader.load(
                    getClass().getResource("/views/ViewProfile.fxml")
            );
            Stage stage = (Stage) backButton.getScene().getWindow();
            Scene scene = new Scene(root, stage.getScene().getWidth(), stage.getScene().getHeight());
            scene.getStylesheets().add(
                    getClass().getResource("/styles/theme.css").toExternalForm()
            );
            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Validation ────────────────────────────────────────────────────────

    private boolean validateInputs() {
        if (dobPicker.getValue() == null)                   return false;
        if ("Select Gender".equals(genderCombo.getValue())) return false;
        if (locationField.getText().trim().isEmpty())       return false;
        return true;
    }

    // ── UI helpers ────────────────────────────────────────────────────────

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess() {
        // Update the timestamp label immediately after save
        lastUpdatedLabel.setText("Last updated: "
                + dateFormatter.format(LocalDate.now()) + " at "
                + java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        );

        successBox.setManaged(true);
        successBox.setVisible(true);
        successBox.setOpacity(0);
        successBox.setScaleX(0.85);
        successBox.setScaleY(0.85);
        successBox.setTranslateY(15);

        FadeTransition fade = new FadeTransition(Duration.millis(350), successBox);
        fade.setFromValue(0); fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(350), successBox);
        scale.setFromX(0.85); scale.setFromY(0.85); scale.setToX(1); scale.setToY(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(350), successBox);
        slide.setFromY(15); slide.setToY(0);
        new ParallelTransition(fade, scale, slide).play();

        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> hideSuccess());
        pause.play();
    }

    private void hideSuccess() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), successBox);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            successBox.setVisible(false);
            successBox.setManaged(false);
            successBox.setOpacity(1);
            successBox.setScaleX(1);
            successBox.setScaleY(1);
            successBox.setTranslateY(0);
        });
        fadeOut.play();
    }
}
