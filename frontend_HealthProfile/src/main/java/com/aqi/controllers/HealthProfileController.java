package com.aqi.controllers;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

public class HealthProfileController {

    @FXML private TextField ageField;
    @FXML private ComboBox<String> genderCombo;
    @FXML private TextField locationField;
    @FXML private CheckBox asthmaCheck;
    @FXML private ComboBox<String> breathingCombo;
    @FXML private Button saveButton;
    @FXML private HBox successBox;

    @FXML
    public void initialize() {

        // Gender options
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

        if (!validateInputs()) {
            showAlert("Please complete all required fields correctly.");
            return;
        }

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

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess() {

        successBox.setManaged(true);
        successBox.setVisible(true);

        successBox.setOpacity(0);
        successBox.setScaleX(0.85);
        successBox.setScaleY(0.85);
        successBox.setTranslateY(15);

        FadeTransition fade = new FadeTransition(Duration.millis(350), successBox);
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
