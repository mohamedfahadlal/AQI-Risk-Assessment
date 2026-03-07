package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import com.aqi.utils.EmailUtil;
import com.aqi.utils.UserSession;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import java.sql.ResultSet;
import java.sql.SQLException;

public class SignUpController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label statusLabel;

    @FXML private Region passStrengthLine;
    @FXML private Region confirmPassStrengthLine;
    @FXML private VBox passwordConditionsBox;
    @FXML private Label lengthLabel;
    @FXML private Label upperLabel;
    @FXML private Label lowerLabel;
    @FXML private Label numberLabel;
    @FXML private Label specialLabel;

    @FXML private VBox otpBox;
    @FXML private TextField otpInput;
    @FXML private Button mainActionBtn;
    @FXML private Button backToDashboardBtn;
    @FXML private Button aboutNavBtnSu;
    @FXML private Button loginNavBtn;
    @FXML private VBox   signUpCard;
    @FXML private TextField confirmPasswordVisibleField;
    @FXML private Button toggleConfirmBtn;
    private boolean confirmVisible = false;

    private String generatedOtp;
    private int buttonState = 0;
    private boolean isPasswordStrong = false;
    private Color currentPassColor = Color.web("#e5e7eb");
    private Color currentConfirmColor = Color.web("#e5e7eb");

    @FXML
    public void initialize() {
        passStrengthLine.setBackground(new Background(new BackgroundFill(currentPassColor, new CornerRadii(3), Insets.EMPTY)));
        confirmPassStrengthLine.setBackground(new Background(new BackgroundFill(currentConfirmColor, new CornerRadii(3), Insets.EMPTY)));

        // Show back button only when a guest was redirected here from a restricted action
        if (backToDashboardBtn != null) {
            boolean showBack = UserSession.isGuestReturnToDashboard();
            backToDashboardBtn.setVisible(showBack);
            backToDashboardBtn.setManaged(showBack);
        }

        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            evaluatePasswordStrength(newValue);
            checkPasswordsMatch();
        });
        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> checkPasswordsMatch());
        confirmPasswordVisibleField.textProperty().addListener((obs, o, n) -> {
            confirmPasswordField.setText(n);
            checkPasswordsMatch();
        });

        Platform.runLater(() -> {
            // Card pop-in — especially noticeable in guest redirect flow
            if (signUpCard != null) {
                signUpCard.setOpacity(0);
                signUpCard.setScaleX(0.88);
                signUpCard.setScaleY(0.88);
                signUpCard.setTranslateY(20);
                FadeTransition f = new FadeTransition(Duration.millis(400), signUpCard);
                f.setFromValue(0); f.setToValue(1);
                ScaleTransition s = new ScaleTransition(Duration.millis(400), signUpCard);
                s.setFromX(0.88); s.setToX(1.0);
                s.setFromY(0.88); s.setToY(1.0);
                s.setInterpolator(Interpolator.EASE_OUT);
                TranslateTransition t = new TranslateTransition(Duration.millis(400), signUpCard);
                t.setFromY(20); t.setToY(0);
                t.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(f, s, t).play();
            }
            // Hovers
            addHover(mainActionBtn,      "rgba(14,165,233,0.38)");
            addHover(backToDashboardBtn, "rgba(26,115,232,0.30)");
            addHover(aboutNavBtnSu,      "rgba(100,100,100,0.18)");
            addHover(loginNavBtn,        "rgba(100,100,100,0.18)");
        });
    }

    private void addHover(Button btn, String glowColor) {
        if (btn == null) return;
        String base = btn.getStyle() != null ? btn.getStyle() : "";
        btn.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(130), btn);
            st.setToX(1.06); st.setToY(1.06);
            st.setInterpolator(Interpolator.EASE_OUT); st.play();
            btn.setStyle(base + "-fx-effect: dropshadow(gaussian," + glowColor + ",16,0.45,0,2);");
            btn.setCursor(javafx.scene.Cursor.HAND);
        });
        btn.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(130), btn);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT); st.play();
            btn.setStyle(base);
        });
        passwordField.focusedProperty().addListener((observable, wasFocused, isNowFocused) -> {
            if (isNowFocused && buttonState == 0) {
                passwordConditionsBox.setVisible(true);
                passwordConditionsBox.setManaged(true);
            } else if (!isNowFocused && passwordField.getText().isEmpty()) {
                passwordConditionsBox.setVisible(false);
                passwordConditionsBox.setManaged(false);
            }
        });
    }

    private void animateLineColor(Region line, Color startColor, Color targetColor, boolean isPassLine) {
        if (startColor.equals(targetColor)) return;
        ObjectProperty<Color> colorProperty = new SimpleObjectProperty<>(startColor);
        colorProperty.addListener((obs, oldColor, newColor) ->
                line.setBackground(new Background(new BackgroundFill(newColor, new CornerRadii(3), Insets.EMPTY))));
        new Timeline(new KeyFrame(Duration.millis(300), new KeyValue(colorProperty, targetColor))).play();
        if (isPassLine) currentPassColor = targetColor;
        else currentConfirmColor = targetColor;
    }

    private void evaluatePasswordStrength(String password) {
        boolean hasLength  = password.length() >= 8;
        boolean hasUpper   = password.matches(".*[A-Z].*");
        boolean hasLower   = password.matches(".*[a-z].*");
        boolean hasNum     = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[^a-zA-Z0-9].*");

        updateTickLabel(lengthLabel,  hasLength,  "At least 8 characters");
        updateTickLabel(upperLabel,   hasUpper,   "1 Uppercase letter");
        updateTickLabel(lowerLabel,   hasLower,   "1 Lowercase letter");
        updateTickLabel(numberLabel,  hasNum,     "1 Number");
        updateTickLabel(specialLabel, hasSpecial, "1 Special character");

        int strength = (hasLength?1:0)+(hasUpper?1:0)+(hasLower?1:0)+(hasNum?1:0)+(hasSpecial?1:0);
        Color targetColor;
        if (password.isEmpty())   { targetColor = Color.web("#e5e7eb"); isPasswordStrong = false; }
        else if (strength <= 2)   { targetColor = Color.web("#ef4444"); isPasswordStrong = false; }
        else if (strength <= 4)   { targetColor = Color.web("#f59e0b"); isPasswordStrong = false; }
        else                      { targetColor = Color.web("#22c55e"); isPasswordStrong = true;  }
        animateLineColor(passStrengthLine, currentPassColor, targetColor, true);
    }

    private void updateTickLabel(Label lbl, boolean met, String text) {
        lbl.setText((met ? "✓ " : "✗ ") + text);
        lbl.setTextFill(Color.web(met ? "#22c55e" : "#ef4444"));
    }

    private void checkPasswordsMatch() {
        String pass = passwordField.getText(), confirm = confirmPasswordField.getText();
        Color targetColor = confirm.isEmpty() ? Color.web("#e5e7eb") : pass.equals(confirm) ? Color.web("#22c55e") : Color.web("#ef4444");
        animateLineColor(confirmPassStrengthLine, currentConfirmColor, targetColor, false);
    }

    @FXML
    private void handleMainAction() {
        statusLabel.setText("");
        if (buttonState == 0) {
            String name = nameField.getText().trim(), email = emailField.getText().trim();
            if (name.isEmpty() || email.isEmpty() || passwordField.getText().isEmpty() || confirmPasswordField.getText().isEmpty()) {
                showError("Please fill in all fields before sending OTP."); return;
            }
            if (!isPasswordStrong) { showError("Please create a stronger password to continue."); return; }
            if (!passwordField.getText().equals(confirmPasswordField.getText())) { showError("Passwords do not match!"); return; }

            mainActionBtn.setText("Sending...");
            mainActionBtn.setDisable(true);
            generatedOtp = String.format("%06d", (int)(Math.random() * 1000000));

            new Thread(() -> {
                boolean success = EmailUtil.sendOtpEmail(email, generatedOtp);
                Platform.runLater(() -> {
                    mainActionBtn.setDisable(false);
                    if (success) {
                        buttonState = 1;
                        mainActionBtn.setText("Validate OTP");
                        passwordConditionsBox.setVisible(false);
                        passwordConditionsBox.setManaged(false);
                        otpBox.setVisible(true);
                        otpBox.setManaged(true);
                        statusLabel.setTextFill(Color.web("#22c55e"));
                        statusLabel.setText("An OTP has been sent to your email!");
                    } else {
                        mainActionBtn.setText("Send OTP");
                        showError("Failed to send OTP. Check your internet connection.");
                    }
                });
            }).start();

        } else if (buttonState == 1) {
            String userOtp = otpInput.getText().trim();
            if (generatedOtp != null && generatedOtp.equals(userOtp)) {
                buttonState = 2;
                mainActionBtn.setText("Register Account");
                mainActionBtn.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; -fx-font-weight: bold;");
                otpInput.setDisable(true);
                statusLabel.setTextFill(Color.GREEN);
                statusLabel.setText("Email verified successfully! Click Register.");
            } else {
                showError("Invalid or incorrect OTP. Please try again.");
            }

        } else if (buttonState == 2) {
            registerUserInDatabase();
        }
    }

    private void registerUserInDatabase() {
        // 1. We removed user_id from the INSERT block.
        // 2. We added "RETURNING user_id" at the end so Postgres hands the new ID back to us!
        String query = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING user_id";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            // Now we have exactly 3 question marks mapping to these 3 values perfectly
            pstmt.setString(1, nameField.getText().trim());
            pstmt.setString(2, emailField.getText().trim());
            pstmt.setString(3, hashPassword(passwordField.getText()));

            // executeQuery() works perfectly here because of our RETURNING clause
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // 3. Make sure we fetch "user_id" from the result, not "id"
                String newUserId = rs.getString("user_id");

                // Store in session → HealthProfile will pick this up
                UserSession.setUserId(newUserId);
                UserSession.setUsername(nameField.getText().trim());
                UserSession.setNewUser(true);

                statusLabel.setTextFill(javafx.scene.paint.Color.GREEN);
                statusLabel.setText("Account created! Setting up your profile...");

                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException e) { e.printStackTrace(); }
                    javafx.application.Platform.runLater(() ->
                            SceneManager.switchScene("/views/HealthProfile.fxml", "Health Profile")
                    );
                }).start();
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("duplicate key value")) {
                showError("An account with this email already exists.");
                resetToStart();
            } else {
                showError("Database error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) { String hex = Integer.toHexString(0xff & b); if (hex.length() == 1) hexString.append('0'); hexString.append(hex); }
            return hexString.toString();
        } catch (Exception e) { throw new RuntimeException("Failed to hash password", e); }
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

    @FXML
    private void toggleConfirmVisibility() {
        confirmVisible = !confirmVisible;
        if (confirmVisible) {
            confirmPasswordVisibleField.setText(confirmPasswordField.getText());
            confirmPasswordVisibleField.setVisible(true);
            confirmPasswordVisibleField.setManaged(true);
            confirmPasswordField.setVisible(false);
            confirmPasswordField.setManaged(false);
            toggleConfirmBtn.setText("🙈");
        } else {
            confirmPasswordField.setText(confirmPasswordVisibleField.getText());
            confirmPasswordField.setVisible(true);
            confirmPasswordField.setManaged(true);
            confirmPasswordVisibleField.setVisible(false);
            confirmPasswordVisibleField.setManaged(false);
            toggleConfirmBtn.setText("👁");
        }
    }

    @FXML
    private void handleBackToDashboard() {
        // Keep guest session alive, just go back
        UserSession.setGuestReturnToDashboard(false);
        SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard");
    }

    @FXML private void goToLogin() {
        UserSession.clearSession(); // clear guest flag before going to login
        SceneManager.switchScene("/fxml/Login.fxml", "Login");
    }
    @FXML private void goToAbout() { SceneManager.switchScene("/fxml/About.fxml"); }
}
