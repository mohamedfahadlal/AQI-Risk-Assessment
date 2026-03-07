package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import com.aqi.utils.EmailUtil;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

public class SignUpController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label statusLabel;

    // UI Elements for Password Strength
    @FXML private Region passStrengthLine;
    @FXML private Region confirmPassStrengthLine;
    @FXML private VBox passwordConditionsBox;
    @FXML private Label lengthLabel;
    @FXML private Label upperLabel;
    @FXML private Label lowerLabel;
    @FXML private Label numberLabel;
    @FXML private Label specialLabel;

    // UI Elements for OTP
    @FXML private VBox otpBox;
    @FXML private TextField otpInput;

    // Multi-purpose Button
    @FXML private Button mainActionBtn;

    private String generatedOtp;

    // 0 = Send OTP state, 1 = Validate OTP state, 2 = Ready to Register state
    private int buttonState = 0;
    private boolean isPasswordStrong = false;

    // Color trackers for smooth animation
    private Color currentPassColor = Color.web("#e5e7eb");
    private Color currentConfirmColor = Color.web("#e5e7eb");

    @FXML
    public void initialize() {
        // Set initial backgrounds so they aren't null when animation starts
        passStrengthLine.setBackground(new Background(new BackgroundFill(currentPassColor, new CornerRadii(3), Insets.EMPTY)));
        confirmPassStrengthLine.setBackground(new Background(new BackgroundFill(currentConfirmColor, new CornerRadii(3), Insets.EMPTY)));

        // Listen to Password Field typing in real-time
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            evaluatePasswordStrength(newValue);
            checkPasswordsMatch();
        });

        // Listen to Confirm Password typing
        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            checkPasswordsMatch();
        });
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            evaluatePasswordStrength(newValue);
            checkPasswordsMatch();
        });

        // Listen to Confirm Password typing
        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            checkPasswordsMatch();
        });

        // NEW: Show the password rules only when the password field is clicked/focused
        passwordField.focusedProperty().addListener((observable, wasFocused, isNowFocused) -> {
            if (isNowFocused && buttonState == 0) {
                // Reveal the checklist when clicked
                passwordConditionsBox.setVisible(true);
                passwordConditionsBox.setManaged(true);
            } else if (!isNowFocused && passwordField.getText().isEmpty()) {
                // Hide it again if they click away without typing anything
                passwordConditionsBox.setVisible(false);
                passwordConditionsBox.setManaged(false);
            }
        });
    }

    private void animateLineColor(Region line, Color startColor, Color targetColor, boolean isPassLine) {
        if (startColor.equals(targetColor)) return; // Skip if it's already the right color

        ObjectProperty<Color> colorProperty = new SimpleObjectProperty<>(startColor);
        colorProperty.addListener((obs, oldColor, newColor) -> {
            line.setBackground(new Background(new BackgroundFill(newColor, new CornerRadii(3), Insets.EMPTY)));
        });

        // 300ms fade transition
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(300), new KeyValue(colorProperty, targetColor))
        );
        timeline.play();

        // Update the trackers
        if (isPassLine) currentPassColor = targetColor;
        else currentConfirmColor = targetColor;
    }

    private void evaluatePasswordStrength(String password) {
        boolean hasLength = password.length() >= 8;
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasNum = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[^a-zA-Z0-9].*");

        updateTickLabel(lengthLabel, hasLength, "At least 8 characters");
        updateTickLabel(upperLabel, hasUpper, "1 Uppercase letter");
        updateTickLabel(lowerLabel, hasLower, "1 Lowercase letter");
        updateTickLabel(numberLabel, hasNum, "1 Number");
        updateTickLabel(specialLabel, hasSpecial, "1 Special character");

        // Calculate total strength (0 to 5)
        int strength = (hasLength?1:0) + (hasUpper?1:0) + (hasLower?1:0) + (hasNum?1:0) + (hasSpecial?1:0);

        Color targetColor;
        if (password.isEmpty()) {
            targetColor = Color.web("#e5e7eb"); // Gray
            isPasswordStrong = false;
        } else if (strength <= 2) {
            targetColor = Color.web("#ef4444"); // Red
            isPasswordStrong = false;
        } else if (strength <= 4) {
            targetColor = Color.web("#f59e0b"); // Orange
            isPasswordStrong = false;
        } else {
            targetColor = Color.web("#22c55e"); // Green
            isPasswordStrong = true;
        }

        animateLineColor(passStrengthLine, currentPassColor, targetColor, true);
    }

    private void updateTickLabel(Label lbl, boolean met, String text) {
        if (met) {
            lbl.setText("✓ " + text);
            lbl.setTextFill(Color.web("#22c55e")); // Green
        } else {
            lbl.setText("✗ " + text);
            lbl.setTextFill(Color.web("#ef4444")); // Red
        }
    }

    private void checkPasswordsMatch() {
        String pass = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        Color targetColor;
        if (confirm.isEmpty()) {
            targetColor = Color.web("#e5e7eb"); // Gray
        } else if (pass.equals(confirm)) {
            targetColor = Color.web("#22c55e"); // Green
        } else {
            targetColor = Color.web("#ef4444"); // Red
        }

        animateLineColor(confirmPassStrengthLine, currentConfirmColor, targetColor, false);
    }

    @FXML
    private void handleMainAction() {
        statusLabel.setText(""); // clear old errors

        if (buttonState == 0) {
            // STEP 1: SEND OTP
            String name = nameField.getText().trim();
            String email = emailField.getText().trim();

            if (name.isEmpty() || email.isEmpty() || passwordField.getText().isEmpty() || confirmPasswordField.getText().isEmpty()) {
                showError("Please fill in all fields before sending OTP.");
                return;
            }
            if (!isPasswordStrong) {
                showError("Please create a stronger password to continue.");
                return;
            }
            if (!passwordField.getText().equals(confirmPasswordField.getText())) {
                showError("Passwords do not match!");
                return;
            }

            // Validations passed! Send the email.
            mainActionBtn.setText("Sending...");
            mainActionBtn.setDisable(true);
            generatedOtp = String.format("%06d", (int) (Math.random() * 1000000));

            new Thread(() -> {
                boolean success = EmailUtil.sendOtpEmail(email, generatedOtp);
                Platform.runLater(() -> {
                    mainActionBtn.setDisable(false);
                    if (success) {
                        // Switch UI to State 1 (Validate)
                        buttonState = 1;
                        mainActionBtn.setText("Validate OTP");

                        // Hide rules, show OTP box
                        passwordConditionsBox.setVisible(false);
                        passwordConditionsBox.setManaged(false);
                        otpBox.setVisible(true);
                        otpBox.setManaged(true);

                        statusLabel.setTextFill(Color.web("#22c55e")); // Green
                        statusLabel.setText("An OTP has been sent to your email!");
                    } else {
                        mainActionBtn.setText("Send OTP");
                        showError("Failed to send OTP. Check your internet connection.");
                    }
                });
            }).start();

        } else if (buttonState == 1) {
            // STEP 2: VALIDATE OTP
            String userOtp = otpInput.getText().trim();
            if (generatedOtp != null && generatedOtp.equals(userOtp)) {
                // Success! Switch UI to State 2 (Register)
                buttonState = 2;
                mainActionBtn.setText("Register Account");
                mainActionBtn.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; -fx-font-weight: bold;"); // Turn button green
                otpInput.setDisable(true); // Lock the OTP field
                statusLabel.setTextFill(Color.GREEN);
                statusLabel.setText("Email verified successfully! Click Register.");
            } else {
                showError("Invalid or incorrect OTP. Please try again.");
            }

        } else if (buttonState == 2) {
            // STEP 3: REGISTER IN DATABASE
            registerUserInDatabase();
        }
    }

    private void registerUserInDatabase() {
        // Notice the column names now perfectly match your friend's Supabase schema
        String query = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, nameField.getText().trim());
            pstmt.setString(2, emailField.getText().trim());

            // Hash the password before saving it!
            String hashedPassword = hashPassword(passwordField.getText());
            pstmt.setString(3, hashedPassword);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                statusLabel.setTextFill(Color.GREEN);
                statusLabel.setText("Account created securely in Supabase! Redirecting...");

                new Thread(() -> {
                    try {
                        Thread.sleep(1500);
                        Platform.runLater(this::goToLogin);
                    } catch (InterruptedException e) { e.printStackTrace(); }
                }).start();
            }
        } catch (SQLException e) {
            // PostgreSQL handles duplicate emails slightly differently, but we catch the error here
            if (e.getMessage().contains("duplicate key value")) {
                showError("An account with this email already exists.");
                resetToStart();
            } else {
                showError("Database error: " + e.getMessage());
                e.printStackTrace(); // Prints the exact error to your IntelliJ console
            }
        }
    }

    // --- Add this helper method anywhere inside your SignUpController class ---
    // This turns "MyPassword123!" into a secure, scrambled string of characters
    private String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    private void resetToStart() {
        buttonState = 0;
        mainActionBtn.setText("Send OTP");
        mainActionBtn.setStyle("-fx-background-color: #0ea5e9; -fx-text-fill: white; -fx-font-weight: bold;");
        otpBox.setVisible(false);
        otpBox.setManaged(false);
        passwordConditionsBox.setVisible(true);
        passwordConditionsBox.setManaged(true);
    }

    private void showError(String message) {
        statusLabel.setTextFill(Color.web("#ef4444"));
        statusLabel.setText(message);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML private void goToLogin() { SceneManager.switchScene("/fxml/Login.fxml"); }
    @FXML private void goToAbout() { SceneManager.switchScene("/fxml/About.fxml"); }

    public void handleBackToDashboard(ActionEvent actionEvent) {
    }
}