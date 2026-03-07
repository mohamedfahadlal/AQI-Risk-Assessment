package com.aqi.controllers;

import com.aqi.utils.SceneManager;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public class AboutController {

    @FXML private HBox     topNav;
    @FXML private VBox     heroSection;
    @FXML private GridPane teamGrid;
    @FXML private VBox     missionSection;
    @FXML private Label    cloudIcon;
    @FXML private Button   loginBtn;
    @FXML private Button   signUpBtn;

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            playEntryAnimations();
            wireNavHovers();
            wireCardHovers();
            startCloudFloat();
        });
    }

    // ─────────────────────────────────────────────────────────────
    // ENTRY ANIMATIONS
    // ─────────────────────────────────────────────────────────────

    private void playEntryAnimations() {
        // Nav bar slides down + fades in
        if (topNav != null) {
            topNav.setOpacity(0);
            topNav.setTranslateY(-22);
            FadeTransition f = new FadeTransition(Duration.millis(380), topNav);
            f.setFromValue(0); f.setToValue(1);
            TranslateTransition t = new TranslateTransition(Duration.millis(380), topNav);
            t.setFromY(-22); t.setToY(0);
            t.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(f, t).play();
        }

        // Hero title + subtitle slide up
        if (heroSection != null) {
            heroSection.setOpacity(0);
            heroSection.setTranslateY(28);
            PauseTransition d = new PauseTransition(Duration.millis(180));
            d.setOnFinished(e -> {
                FadeTransition f = new FadeTransition(Duration.millis(500), heroSection);
                f.setFromValue(0); f.setToValue(1);
                TranslateTransition tt = new TranslateTransition(Duration.millis(500), heroSection);
                tt.setFromY(28); tt.setToY(0);
                tt.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(f, tt).play();
            });
            d.play();
        }

        // Team cards cascade in — staggered pop + slide
        if (teamGrid != null) {
            int i = 0;
            for (Node card : teamGrid.getChildren()) {
                card.setOpacity(0);
                card.setTranslateY(36);
                card.setScaleX(0.93);
                card.setScaleY(0.93);
                int delay = 360 + i * 110;
                PauseTransition pd = new PauseTransition(Duration.millis(delay));
                pd.setOnFinished(e -> {
                    FadeTransition f = new FadeTransition(Duration.millis(420), card);
                    f.setFromValue(0); f.setToValue(1);
                    TranslateTransition tt = new TranslateTransition(Duration.millis(420), card);
                    tt.setFromY(36); tt.setToY(0);
                    tt.setInterpolator(Interpolator.EASE_OUT);
                    ScaleTransition st = new ScaleTransition(Duration.millis(420), card);
                    st.setFromX(0.93); st.setFromY(0.93);
                    st.setToX(1.0);   st.setToY(1.0);
                    st.setInterpolator(Interpolator.EASE_OUT);
                    new ParallelTransition(f, tt, st).play();
                });
                pd.play();
                i++;
            }
        }

        // Mission fades up after cards finish
        if (missionSection != null) {
            missionSection.setOpacity(0);
            missionSection.setTranslateY(22);
            PauseTransition pd = new PauseTransition(Duration.millis(860));
            pd.setOnFinished(e -> {
                FadeTransition f = new FadeTransition(Duration.millis(500), missionSection);
                f.setFromValue(0); f.setToValue(1);
                TranslateTransition tt = new TranslateTransition(Duration.millis(500), missionSection);
                tt.setFromY(22); tt.setToY(0);
                tt.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(f, tt).play();
            });
            pd.play();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // NAV BUTTON HOVERS
    // ─────────────────────────────────────────────────────────────

    private void wireNavHovers() {
        if (loginBtn  != null) addButtonHover(loginBtn,  "rgba(14,165,233,0.38)");
        if (signUpBtn != null) addButtonHover(signUpBtn, "rgba(14,165,233,0.55)");
    }

    private void addButtonHover(Button btn, String glowColor) {
        String base = btn.getStyle() != null ? btn.getStyle() : "";
        btn.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(130), btn);
            st.setToX(1.07); st.setToY(1.07);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
            btn.setStyle(base + "-fx-effect: dropshadow(gaussian," + glowColor + ",14,0.45,0,2); -fx-cursor: hand;");
        });
        btn.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(130), btn);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
            btn.setStyle(base);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // TEAM CARD HOVERS
    // ─────────────────────────────────────────────────────────────

    private static final String[] CARD_GLOWS = {
            "rgba(14,165,233,0.50)",    // blue   — card 0
            "rgba(139,92,246,0.50)",    // purple — card 1
            "rgba(16,185,129,0.50)",    // green  — card 2
            "rgba(245,158,11,0.50)"     // amber  — card 3
    };

    private void wireCardHovers() {
        if (teamGrid == null) return;
        int idx = 0;
        for (Node node : teamGrid.getChildren()) {
            if (node instanceof HBox card) {
                String glow    = CARD_GLOWS[idx % CARD_GLOWS.length];
                String base    = card.getStyle() != null ? card.getStyle() : "";
                String hovered = base
                        + "-fx-effect: dropshadow(gaussian," + glow + ",22,0.35,0,6);"
                        + "-fx-border-color: " + glowBorder(idx) + "; -fx-border-width: 1.5; -fx-border-radius: 12;";

                // Find the avatar circle inside this card
                Circle avatar = findCircle(card);

                card.setOnMouseEntered(e -> {
                    // Card lifts
                    ScaleTransition sc = new ScaleTransition(Duration.millis(170), card);
                    sc.setToX(1.025); sc.setToY(1.025);
                    sc.setInterpolator(Interpolator.EASE_OUT);
                    TranslateTransition tr = new TranslateTransition(Duration.millis(170), card);
                    tr.setToY(-5);
                    tr.setInterpolator(Interpolator.EASE_OUT);
                    new ParallelTransition(sc, tr).play();
                    card.setStyle(hovered);

                    // Avatar bounces
                    if (avatar != null) {
                        ScaleTransition as = new ScaleTransition(Duration.millis(200), avatar);
                        as.setToX(1.15); as.setToY(1.15);
                        as.setInterpolator(Interpolator.EASE_OUT);
                        as.play();
                        // Pulse ring
                        playAvatarPulse(avatar, glow);
                    }
                });

                card.setOnMouseExited(e -> {
                    // Card settles
                    ScaleTransition sc = new ScaleTransition(Duration.millis(200), card);
                    sc.setToX(1.0); sc.setToY(1.0);
                    sc.setInterpolator(Interpolator.EASE_OUT);
                    TranslateTransition tr = new TranslateTransition(Duration.millis(200), card);
                    tr.setToY(0);
                    tr.setInterpolator(Interpolator.EASE_OUT);
                    new ParallelTransition(sc, tr).play();
                    card.setStyle(base);

                    // Avatar shrinks back
                    if (avatar != null) {
                        ScaleTransition as = new ScaleTransition(Duration.millis(180), avatar);
                        as.setToX(1.0); as.setToY(1.0);
                        as.setInterpolator(Interpolator.EASE_OUT);
                        as.play();
                    }
                });
                idx++;
            }
        }
    }

    /** Pulses a fading ring around the avatar circle on hover */
    private void playAvatarPulse(Circle avatar, String glowColor) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(400), avatar);
        pulse.setFromX(1.15); pulse.setFromY(1.15);
        pulse.setToX(1.22);   pulse.setToY(1.22);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.setInterpolator(Interpolator.EASE_BOTH);

        String glowStyle = "-fx-effect: dropshadow(gaussian," + glowColor + ",18,0.5,0,0);";
        String prev = avatar.getStyle() != null ? avatar.getStyle() : "";
        avatar.setStyle(prev + glowStyle);
        pulse.setOnFinished(e -> avatar.setStyle(prev));
        pulse.play();
    }

    /** Returns a CSS-compatible border color string matching each card's glow */
    private String glowBorder(int idx) {
        return switch (idx % 4) {
            case 0 -> "rgba(14,165,233,0.55)";
            case 1 -> "rgba(139,92,246,0.55)";
            case 2 -> "rgba(16,185,129,0.55)";
            default -> "rgba(245,158,11,0.55)";
        };
    }

    /** Recursively finds the first Circle child inside a node */
    private Circle findCircle(javafx.scene.Parent parent) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Circle c) return c;
            if (child instanceof javafx.scene.Parent p) {
                Circle found = findCircle(p);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────
    // CLOUD FLOAT ANIMATION
    // ─────────────────────────────────────────────────────────────

    private void startCloudFloat() {
        if (cloudIcon == null) return;
        TranslateTransition float1 = new TranslateTransition(Duration.millis(2200), cloudIcon);
        float1.setFromY(0); float1.setToY(-8);
        float1.setAutoReverse(true);
        float1.setCycleCount(Animation.INDEFINITE);
        float1.setInterpolator(Interpolator.EASE_BOTH);

        FadeTransition fade = new FadeTransition(Duration.millis(2200), cloudIcon);
        fade.setFromValue(0.75); fade.setToValue(1.0);
        fade.setAutoReverse(true);
        fade.setCycleCount(Animation.INDEFINITE);

        new ParallelTransition(float1, fade).play();
    }

    // ─────────────────────────────────────────────────────────────
    // NAVIGATION
    // ─────────────────────────────────────────────────────────────

    @FXML
    private void goToLogin() {
        SceneManager.switchScene("/fxml/Login.fxml", "Login");
    }

    @FXML
    private void goToSignUp() {
        SceneManager.switchScene("/fxml/SignUp.fxml", "Sign Up");
    }
}
