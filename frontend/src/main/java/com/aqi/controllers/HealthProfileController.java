package com.aqi.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class HealthProfileController {

    @FXML
    private TextField ageField;

    @FXML
    private ComboBox<String> genderCombo;

    @FXML
    private TextField locationField;

    @FXML
    private CheckBox asthmaCheck;

    @FXML
    private TextField breathingConditionField;

    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        genderCombo.getItems().addAll("Male", "Female", "Other");
    }

    @FXML
    private void handleSave() {

        if (ageField.getText().isEmpty() || locationField.getText().isEmpty()) {
            statusLabel.setText("Please fill required fields.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        try {
            int age = Integer.parseInt(ageField.getText());

            if (age <= 0) {
                statusLabel.setText("Age must be positive.");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }

        } catch (NumberFormatException e) {
            statusLabel.setText("Age must be a number.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        statusLabel.setText("Profile saved successfully!");
        statusLabel.setStyle("-fx-text-fill: green;");
    }

    @FXML
    private void handleCancel() {
        ageField.clear();
        locationField.clear();
        breathingConditionField.clear();
        asthmaCheck.setSelected(false);
        genderCombo.getSelectionModel().clearSelection();
        statusLabel.setText("");
    }
}
