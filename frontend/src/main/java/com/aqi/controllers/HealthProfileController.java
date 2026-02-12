package com.aqi.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

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

        // -----------------------------
        // Gender Setup (No Default Bias)
        // -----------------------------
        genderCombo.getItems().addAll(
                "Select Gender",
                "Male",
                "Female",
                "Other"
        );
        genderCombo.setValue("Select Gender");

        // -----------------------------
        // Breathing Severity Setup
        // -----------------------------
        breathingCombo.getItems().addAll(
                "None",
                "Mild",
                "Moderate",
                "Severe"
        );
        breathingCombo.setValue("None");

        // -----------------------------
        // Age Numeric Constraint
        // -----------------------------
        ageField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                ageField.setText(newVal.replaceAll("[^\\d]", ""));
            }
        });

        // -----------------------------
        // Save Button Action
        // -----------------------------
        saveButton.setOnAction(e -> handleSave());
    }

    private void handleSave() {

        if (!validateInputs()) {
            showAlert("Please complete all required fields correctly.");
            return;
        }

        // TODO: Connect to backend API here

        successBox.setVisible(true);
    }

    private boolean validateInputs() {

        // Age required
        if (ageField.getText().isEmpty())
            return false;

        // Gender must not be placeholder
        if (genderCombo.getValue().equals("Select Gender"))
            return false;

        // Location required
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
}
