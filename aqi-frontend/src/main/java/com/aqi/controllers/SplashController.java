package com.aqi.controllers;

import com.aqi.utils.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;

public class SplashController {

    @FXML private StackPane rootPane;
    @FXML private MediaView  mediaView;
    @FXML private Label      skipLabel;

    private MediaPlayer mediaPlayer;
    private boolean     navigating = false;

    @FXML
    public void initialize() {
        URL videoUrl = resolveVideo();

        if (videoUrl == null) {
            System.err.println("[Splash] intro.mp4 not found — skipping to login.");
            Platform.runLater(this::goToLogin);
            return;
        }

        System.out.println("[Splash] Playing video: " + videoUrl);

        Media media   = new Media(videoUrl.toExternalForm());
        mediaPlayer   = new MediaPlayer(media);
        mediaView.setMediaPlayer(mediaPlayer);

        // ── Bind MediaView size to the root pane so it always fills the window ──
        mediaView.fitWidthProperty().bind(rootPane.widthProperty());
        mediaView.fitHeightProperty().bind(rootPane.heightProperty());
        mediaView.setPreserveRatio(false); // stretch to fill — no black bars

        // ── Loop forever ──────────────────────────────────────────────────────
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);

        // Skip gracefully on error
        mediaPlayer.setOnError(() -> {
            System.err.println("[Splash] MediaPlayer error: " + mediaPlayer.getError());
            Platform.runLater(this::goToLogin);
        });

        rootPane.setStyle("-fx-background-color: black;");

        // ── Click anywhere → login ────────────────────────────────────────────
        rootPane.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> goToLogin());
        rootPane.setCursor(Cursor.HAND);

        // ── Pulse the skip label ──────────────────────────────────────────────
        startPulse();

        mediaPlayer.play();
    }

    private void startPulse() {
        FadeTransition fade = new FadeTransition(Duration.seconds(1.4), skipLabel);
        fade.setFromValue(0.4);
        fade.setToValue(1.0);
        fade.setAutoReverse(true);
        fade.setCycleCount(FadeTransition.INDEFINITE);
        fade.play();
    }

    private URL resolveVideo() {
        URL url = getClass().getResource("/fxml/media/intro.mp4");
        if (url != null) return url;

        File f1 = new File("intro.mp4");
        if (f1.exists()) {
            try { return f1.toURI().toURL(); } catch (Exception ignored) {}
        }

        File f2 = new File("media/intro.mp4");
        if (f2.exists()) {
            try { return f2.toURI().toURL(); } catch (Exception ignored) {}
        }

        return null;
    }

    private void goToLogin() {
        if (navigating) return;
        navigating = true;
        if (mediaPlayer != null) mediaPlayer.dispose();
        SceneManager.switchScene("/fxml/Login.fxml", "AiQI — Login");
    }

    public void dispose() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
    }
}