package com.aqi.controllers;

import com.aqi.utils.DatabaseConnection;
import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label         statusLabel;
    @FXML private CheckBox      rememberMeBox;
    @FXML private Button        loginBtn;
    @FXML private Button        guestBtn;
    @FXML private Hyperlink     forgotBtn;
    @FXML private Button        aboutNavBtn;
    @FXML private Button        signUpNavBtn;
    @FXML private VBox          loginCard;

    private static final java.util.prefs.Preferences PREFS =
            java.util.prefs.Preferences.userNodeForPackage(LoginController.class);

    @FXML
    public void initialize() {
        String saved = PREFS.get("remembered_email", "");
        if (!saved.isEmpty()) {
            emailField.setText(saved);
            if (rememberMeBox != null) rememberMeBox.setSelected(true);
        }
        Platform.runLater(() -> {
            // Card pop-in
            if (loginCard != null) {
                loginCard.setOpacity(0);
                loginCard.setScaleX(0.88);
                loginCard.setScaleY(0.88);
                loginCard.setTranslateY(24);
                FadeTransition f = new FadeTransition(Duration.millis(380), loginCard);
                f.setFromValue(0); f.setToValue(1);
                ScaleTransition s = new ScaleTransition(Duration.millis(380), loginCard);
                s.setFromX(0.88); s.setToX(1.0);
                s.setFromY(0.88); s.setToY(1.0);
                s.setInterpolator(Interpolator.EASE_OUT);
                TranslateTransition t = new TranslateTransition(Duration.millis(380), loginCard);
                t.setFromY(24); t.setToY(0);
                t.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(f, s, t).play();
            }
            // Button hovers
            addHover(loginBtn,    "#1a73e8", "rgba(26,115,232,0.38)");
            addHover(guestBtn,    "#555",    "rgba(100,100,100,0.22)");
            addHover(forgotBtn,   "#1a73e8", "rgba(26,115,232,0.22)");
            addHover(aboutNavBtn, "#555",    "rgba(100,100,100,0.18)");
            addHover(signUpNavBtn,"#555",    "rgba(100,100,100,0.18)");
        });
    }

    private void addHover(javafx.scene.control.Labeled btn, String textColor, String glowColor) {
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
    }

    @FXML
    private void handleLogin() {
        String email    = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both email and password.");
            return;
        }

        // Disable button + show loading state while DB call runs on background thread
        if (loginBtn != null) { loginBtn.setDisable(true); loginBtn.setText("Logging in..."); }
        statusLabel.setText("");

        Thread t = new Thread(() -> {
            String query = "SELECT user_id, username, password_hash FROM users WHERE email = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {

                pstmt.setString(1, email);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    String savedHash = rs.getString("password_hash");
                    String userId    = rs.getString("user_id");
                    String username  = rs.getString("username");

                    if (sha256(password).equals(savedHash)) {
                        UserSession.setUserId(userId);
                        UserSession.setUsername(username);

                        if (rememberMeBox != null && rememberMeBox.isSelected())
                            PREFS.put("remembered_email", email);
                        else PREFS.remove("remembered_email");

                        boolean needsProfile = !hasHealthProfile(userId);
                        Platform.runLater(() -> {
                            if (needsProfile) {
                                SceneManager.switchScene("/views/HealthProfile.fxml", "Health Profile");
                            } else {
                                playExitThenSwitch("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard");
                            }
                        });
                    } else {
                        Platform.runLater(() -> {
                            statusLabel.setText("Invalid email or password.");
                            resetLoginBtn();
                        });
                    }
                } else {
                    Platform.runLater(() -> {
                        statusLabel.setText("Invalid email or password.");
                        resetLoginBtn();
                    });
                }

            } catch (SQLException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Database error: " + e.getMessage());
                    resetLoginBtn();
                });
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void resetLoginBtn() {
        if (loginBtn != null) { loginBtn.setDisable(false); loginBtn.setText("Login to Dashboard"); }
    }

    /** Fade + scale out the card, then switch scene. */
    private void playExitThenSwitch(String fxml, String title) {
        if (loginCard != null) {
            FadeTransition exitFade = new FadeTransition(Duration.millis(240), loginCard);
            exitFade.setFromValue(1.0); exitFade.setToValue(0.0);
            ScaleTransition exitScale = new ScaleTransition(Duration.millis(240), loginCard);
            exitScale.setToX(0.90); exitScale.setToY(0.90);
            exitScale.setInterpolator(Interpolator.EASE_IN);
            ParallelTransition exit = new ParallelTransition(exitFade, exitScale);
            exit.setOnFinished(ev -> SceneManager.switchScene(fxml, title));
            exit.play();
        } else {
            SceneManager.switchScene(fxml, title);
        }
    }

    private boolean hasHealthProfile(String userId) {
        String query = "SELECT 1 FROM health_profiles WHERE user_id = ?::uuid";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, userId);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    @FXML
    private void handleForgotPassword() {
        statusLabel.setText("Please contact support to reset your password.");
    }

    // ── Guest login ───────────────────────────────────────────────
    @FXML
    private void handleGuestLogin() {
        UserSession.loginAsGuest();
        playExitThenSwitch("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard — Guest");
    }

    @FXML
    private void goToSignUp() {
        SceneManager.switchScene("/fxml/SignUp.fxml", "Sign Up");
    }

    @FXML
    private void goToAbout()  {
        SceneManager.switchScene("/fxml/About.fxml", "About Us");
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 error", e);
        }
    }
}