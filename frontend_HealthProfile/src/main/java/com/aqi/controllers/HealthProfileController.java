package com.aqi.controllers;

<<<<<<< HEAD
import com.aqi.services.ApiService;
import com.aqi.utils.UserSession;
import com.google.gson.*;
import javafx.animation.*;
import javafx.application.Platform;
=======
import javafx.animation.*;
>>>>>>> origin/main
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
<<<<<<< HEAD
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class HealthProfileController {

    @FXML private DatePicker          dobPicker;
    @FXML private Label               ageDisplayLabel;
    @FXML private ComboBox<String>    genderCombo;
    @FXML private TextField           locationField;
    @FXML private ComboBox<String>    breathingCombo;
    @FXML private CheckBox            smokerCheck;
    @FXML private CheckBox            allergicCheck;
    @FXML private CheckBox            pregnantCheck;
    @FXML private Label               pregnantLabel;
    @FXML private Button              saveButton;
    @FXML private HBox                successBox;

    // DD/MM/YYYY formatter used throughout this controller
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
=======

public class HealthProfileController {

    @FXML private TextField ageField;
    @FXML private ComboBox<String> genderCombo;
    @FXML private TextField locationField;
    @FXML private CheckBox asthmaCheck;
    @FXML private ComboBox<String> breathingCombo;
    @FXML private Button saveButton;
    @FXML private HBox successBox;
>>>>>>> origin/main

    @FXML
    public void initialize() {

        // Gender options
<<<<<<< HEAD
        genderCombo.getItems().addAll("Select Gender", "Male", "Female", "Other");
        genderCombo.setValue("Select Gender");

        // Breathing/Asthma condition options
        breathingCombo.getItems().addAll("None", "Mild", "Moderate", "Severe");
        breathingCombo.setValue("None");

        // ── DD/MM/YYYY format for DatePicker ──────────────────────────────
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
                        return null; // invalid input — picker clears gracefully
                    }
                }
                return null;
            }
        });

        // Restrict future dates — user cannot pick a future birthday
        dobPicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisabled(empty || date.isAfter(LocalDate.now()));
            }
        });

        // Show calculated age live as user picks DOB
        dobPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                int age = Period.between(newVal, LocalDate.now()).getYears();
                ageDisplayLabel.setText("Age: " + age + " yrs");
            } else {
                ageDisplayLabel.setText("");
            }
        });

        // Show pregnant checkbox only when gender = Female
        genderCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isFemale = "Female".equals(newVal);
            pregnantCheck.setVisible(isFemale);
            pregnantCheck.setManaged(isFemale);
            pregnantLabel.setVisible(isFemale);
            pregnantLabel.setManaged(isFemale);
            // Clear it if they switch away from Female
            if (!isFemale) pregnantCheck.setSelected(false);
        });

        saveButton.setOnAction(e -> handleSave());

        // TEMPORARY for standalone testing — remove when integrated with login
        // UserSession.setUserId("paste-a-uuid-from-supabase-users-table-here");

        // Load existing profile in background so UI doesn't freeze
        new Thread(this::loadExistingProfile).start();
    }

    // ── Load existing profile and pre-fill the form ───────────────────────

    private void loadExistingProfile() {
        String userId = UserSession.getUserId();
        if (userId == null) return;

        String json = ApiService.loadProfile(userId);
        if (json == null) return; // No profile yet — leave form blank

        try {
            JsonObject profile = JsonParser.parseString(json).getAsJsonObject();

            // All UI updates must run on the JavaFX thread
            Platform.runLater(() -> {

                // dob comes as YYYY-MM-DD from DB — converter handles DD/MM/YYYY display
                if (has(profile, "dob")) {
                    LocalDate dob = LocalDate.parse(profile.get("dob").getAsString());
                    dobPicker.setValue(dob);
                }
                if (has(profile, "gender")) {
                    genderCombo.setValue(profile.get("gender").getAsString());
                }
                if (has(profile, "location")) {
                    locationField.setText(profile.get("location").getAsString());
                }
                if (has(profile, "asthma_breathing")) {
                    breathingCombo.setValue(profile.get("asthma_breathing").getAsString());
                }
                if (has(profile, "is_smoker")) {
                    smokerCheck.setSelected(profile.get("is_smoker").getAsBoolean());
                }
                if (has(profile, "is_allergic")) {
                    allergicCheck.setSelected(profile.get("is_allergic").getAsBoolean());
                }
                if (has(profile, "is_pregnant")) {
                    pregnantCheck.setSelected(profile.get("is_pregnant").getAsBoolean());
                }
            });

        } catch (Exception e) {
            System.err.println("Failed to parse profile JSON: " + e.getMessage());
        }
    }

    // Helper — check field exists and is not null in JSON
    private boolean has(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull();
    }

    // ── Save profile ──────────────────────────────────────────────────────

    private void handleSave() {
=======
        genderCombo.getItems().addAll(
                "Select Gender",
                "Male",
                "Female",
                "Other"
        );
        genderCombo.setValue("Select Gender");

        // Breathing condition options
        breathingCombo.getItems().addAll(
                "None",
                "Mild",
                "Moderate",
                "Severe"
        );
        breathingCombo.setValue("None");

        // Age numeric constraint
        ageField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                ageField.setText(newVal.replaceAll("[^\\d]", ""));
            }
        });

        saveButton.setOnAction(e -> handleSave());
    }

    private void handleSave() {

>>>>>>> origin/main
        if (!validateInputs()) {
            showAlert("Please complete all required fields correctly.");
            return;
        }

<<<<<<< HEAD
        // Build JSON payload — dob always sent as YYYY-MM-DD to DB
        JsonObject json = new JsonObject();
        json.addProperty("userId",          UserSession.getUserId());
        json.addProperty("dob",             dobPicker.getValue().toString()); // YYYY-MM-DD
        json.addProperty("gender",          genderCombo.getValue());
        json.addProperty("location",        locationField.getText().trim());
        json.addProperty("asthmaBreathing", breathingCombo.getValue());
        json.addProperty("isSmoker",        smokerCheck.isSelected());
        json.addProperty("isAllergic",      allergicCheck.isSelected());
        json.addProperty("isPregnant",      pregnantCheck.isSelected());

        // Save in background thread so UI stays responsive
        new Thread(() -> {
            ApiService.saveProfile(json.toString());
            Platform.runLater(this::showSuccess);
        }).start();
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
=======
        // TODO: Call backend API here

        showSuccess();
    }

    private boolean validateInputs() {

        if (ageField.getText().isEmpty())
            return false;

        if (genderCombo.getValue().equals("Select Gender"))
            return false;

        if (locationField.getText().trim().isEmpty())
            return false;

        return true;
    }

    private void showAlert(String message) {

>>>>>>> origin/main
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess() {
<<<<<<< HEAD
        successBox.setManaged(true);
        successBox.setVisible(true);
=======

        successBox.setManaged(true);
        successBox.setVisible(true);

>>>>>>> origin/main
        successBox.setOpacity(0);
        successBox.setScaleX(0.85);
        successBox.setScaleY(0.85);
        successBox.setTranslateY(15);

        FadeTransition fade = new FadeTransition(Duration.millis(350), successBox);
<<<<<<< HEAD
        fade.setFromValue(0); fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(350), successBox);
        scale.setFromX(0.85); scale.setFromY(0.85);
        scale.setToX(1);      scale.setToY(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(350), successBox);
        slide.setFromY(15); slide.setToY(0);

        new ParallelTransition(fade, scale, slide).play();
=======
        fade.setFromValue(0);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(350), successBox);
        scale.setFromX(0.85);
        scale.setFromY(0.85);
        scale.setToX(1);
        scale.setToY(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(350), successBox);
        slide.setFromY(15);
        slide.setToY(0);

        ParallelTransition show = new ParallelTransition(fade, scale, slide);
        show.play();
>>>>>>> origin/main

        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(e -> hideSuccess());
        pause.play();
    }

    private void hideSuccess() {
<<<<<<< HEAD
        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), successBox);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
=======

        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), successBox);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

>>>>>>> origin/main
        fadeOut.setOnFinished(e -> {
            successBox.setVisible(false);
            successBox.setManaged(false);
            successBox.setOpacity(1);
            successBox.setScaleX(1);
            successBox.setScaleY(1);
            successBox.setTranslateY(0);
        });
<<<<<<< HEAD
=======

>>>>>>> origin/main
        fadeOut.play();
    }
}
