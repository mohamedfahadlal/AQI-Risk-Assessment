package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.sql.*;
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
    @FXML private Button              saveButton;
    @FXML private HBox                successBox;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    public void initialize() {
        genderCombo.getItems().addAll("Select Gender", "Male", "Female", "Other");
        genderCombo.setValue("Select Gender");

        breathingCombo.getItems().addAll("None", "Mild", "Moderate", "Severe");
        breathingCombo.setValue("None");

        dobPicker.setConverter(new StringConverter<LocalDate>() {
            @Override public String toString(LocalDate date) {
                return date != null ? dateFormatter.format(date) : "";
            }
            @Override public LocalDate fromString(String text) {
                if (text != null && !text.trim().isEmpty()) {
                    try { return LocalDate.parse(text.trim(), dateFormatter); }
                    catch (DateTimeParseException e) { return null; }
                }
                return null;
            }
        });

        dobPicker.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisabled(empty || date.isAfter(LocalDate.now()));
            }
        });

        ageDisplayLabel.setVisible(false);
        ageDisplayLabel.setManaged(false);
        dobPicker.valueProperty().addListener((obs, o, newVal) -> {
            if (newVal != null) {
                ageDisplayLabel.setText("Age: " + Period.between(newVal, LocalDate.now()).getYears() + " yrs");
                ageDisplayLabel.setVisible(true);
                ageDisplayLabel.setManaged(true);
            } else {
                ageDisplayLabel.setVisible(false);
                ageDisplayLabel.setManaged(false);
            }
        });

        breathingCombo.valueProperty().addListener((obs, o, newVal) -> {
            boolean show = newVal != null && !newVal.equals("None");
            conditionLabel.setVisible(show); conditionLabel.setManaged(show);
            conditionBox.setVisible(show);   conditionBox.setManaged(show);
            if (!show) {
                asthmaCheck.setSelected(false); copdCheck.setSelected(false);
                bronchitisCheck.setSelected(false); otherBreathingCheck.setSelected(false);
            }
        });

        genderCombo.valueProperty().addListener((obs, o, newVal) -> {
            boolean isFemale = "Female".equals(newVal);
            pregnantCheck.setVisible(isFemale);   pregnantCheck.setManaged(isFemale);
            pregnantLabel.setVisible(isFemale);   pregnantLabel.setManaged(isFemale);
            if (!isFemale) pregnantCheck.setSelected(false);
        });

        saveButton.setOnAction(e -> handleSave());
        new Thread(this::loadExistingProfile).start();
    }

    // ── Load existing profile from DB ─────────────────────────────────────

    private void loadExistingProfile() {
        String userId = UserSession.getUserId();
        if (userId == null) return;

        String query = "SELECT * FROM health_profiles WHERE user_id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Date dob          = rs.getDate("dob");
                String gender     = rs.getString("gender");
                String location   = rs.getString("location");
                String breathing  = rs.getString("asthma_breathing");
                boolean smoker    = rs.getBoolean("is_smoker");
                boolean allergic  = rs.getBoolean("is_allergic");
                boolean pregnant  = rs.getBoolean("is_pregnant");
                Array conditions  = rs.getArray("breathing_conditions");

                Platform.runLater(() -> {
                    if (dob != null)      dobPicker.setValue(dob.toLocalDate());
                    if (gender != null)   genderCombo.setValue(gender);
                    if (location != null) locationField.setText(location);
                    if (breathing != null) breathingCombo.setValue(breathing);
                    smokerCheck.setSelected(smoker);
                    allergicCheck.setSelected(allergic);
                    pregnantCheck.setSelected(pregnant);

                    if (conditions != null) {
                        try {
                            String[] arr = (String[]) conditions.getArray();
                            for (String c : arr) {
                                switch (c) {
                                    case "Asthma"             -> asthmaCheck.setSelected(true);
                                    case "COPD"               -> copdCheck.setSelected(true);
                                    case "Chronic Bronchitis" -> bronchitisCheck.setSelected(true);
                                    case "Other"              -> otherBreathingCheck.setSelected(true);
                                }
                            }
                        } catch (SQLException e) { e.printStackTrace(); }
                    }
                });
            }
        } catch (SQLException e) {
            System.err.println("Failed to load profile: " + e.getMessage());
        }
    }

    // ── Save profile to DB ────────────────────────────────────────────────

    private void handleSave() {
        if (!validateInputs()) { showAlert("Please complete all required fields."); return; }
        if (!breathingCombo.getValue().equals("None") && getSelectedConditions().length == 0) {
            showAlert("Please select at least one breathing condition type."); return;
        }

        new Thread(() -> {
            String userId = UserSession.getUserId();
            String sql = """
                INSERT INTO health_profiles
                    (user_id, dob, gender, location, asthma_breathing,
                     breathing_conditions, is_smoker, is_allergic, is_pregnant, updated_at)
                VALUES (?::uuid, ?, ?::gender_type, ?, ?::asthma_level, ?, ?, ?, ?, NOW())
                ON CONFLICT (user_id) DO UPDATE SET
                    dob                  = EXCLUDED.dob,
                    gender               = EXCLUDED.gender,
                    location             = EXCLUDED.location,
                    asthma_breathing     = EXCLUDED.asthma_breathing,
                    breathing_conditions = EXCLUDED.breathing_conditions,
                    is_smoker            = EXCLUDED.is_smoker,
                    is_allergic          = EXCLUDED.is_allergic,
                    is_pregnant          = EXCLUDED.is_pregnant,
                    updated_at           = NOW()
                """;

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, userId);
                pstmt.setDate(2, Date.valueOf(dobPicker.getValue()));
                pstmt.setString(3, genderCombo.getValue());
                pstmt.setString(4, locationField.getText().trim());
                pstmt.setString(5, breathingCombo.getValue());
                pstmt.setArray(6, conn.createArrayOf("text", getSelectedConditions()));
                pstmt.setBoolean(7, smokerCheck.isSelected());
                pstmt.setBoolean(8, allergicCheck.isSelected());
                pstmt.setBoolean(9, pregnantCheck.isSelected());

                pstmt.executeUpdate();
                Platform.runLater(() -> {
                    showSuccess();
                    // After saving, navigate to View Profile
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override public void run() {
                            Platform.runLater(() ->
                                    SceneManager.switchScene("/views/ViewProfile.fxml", "View Profile")
                            );
                        }
                    }, 2000);
                });

            } catch (SQLException e) {
                Platform.runLater(() -> showAlert("Failed to save: " + e.getMessage()));
                e.printStackTrace();
            }
        }).start();
    }

    private String[] getSelectedConditions() {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (asthmaCheck.isSelected())         list.add("Asthma");
        if (copdCheck.isSelected())           list.add("COPD");
        if (bronchitisCheck.isSelected())     list.add("Chronic Bronchitis");
        if (otherBreathingCheck.isSelected()) list.add("Other");
        return list.toArray(new String[0]);
    }

    private boolean validateInputs() {
        if (dobPicker.getValue() == null)                   return false;
        if ("Select Gender".equals(genderCombo.getValue())) return false;
        if (locationField.getText().trim().isEmpty())       return false;
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
        successBox.setManaged(true); successBox.setVisible(true);
        successBox.setOpacity(0); successBox.setScaleX(0.85); successBox.setScaleY(0.85); successBox.setTranslateY(15);
        FadeTransition fade = new FadeTransition(Duration.millis(350), successBox); fade.setFromValue(0); fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(350), successBox); scale.setFromX(0.85); scale.setFromY(0.85); scale.setToX(1); scale.setToY(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(350), successBox); slide.setFromY(15); slide.setToY(0);
        new ParallelTransition(fade, scale, slide).play();
        PauseTransition pause = new PauseTransition(Duration.seconds(2));
        pause.setOnFinished(e -> hideSuccess());
        pause.play();
    }

    private void hideSuccess() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), successBox);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> { successBox.setVisible(false); successBox.setManaged(false); successBox.setOpacity(1); successBox.setScaleX(1); successBox.setScaleY(1); successBox.setTranslateY(0); });
        fadeOut.play();
    }
}
