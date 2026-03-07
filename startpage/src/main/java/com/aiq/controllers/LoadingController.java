package com.aiq.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.Text;
import javafx.util.Duration;
import java.net.URL;

public class LoadingController {

    @FXML private AnchorPane rootPane;
    @FXML private ImageView logoView;
    @FXML private Text consoleOutput;
    @FXML private Text loadingBlocks;
    @FXML private MediaView bgVideo;

    private MediaPlayer mediaPlayer;
    private int logIndex = 0;
    private int progress = 0;
    private StringBuilder currentLog = new StringBuilder();

    private final String[] bootLogs = {
            "AiQI CORE v1.0 BOOT SEQUENCE INITIATED...",
            "MOUNTING SENSOR-ARRAY-GATEWAY... OK",
            "CONNECTING TO KOLLAM INDIA SENSOR NODE... [ONLINE]",
            "FETCHING REAL-TIME PM2.5 DATASET... [CACHED]",
            "CALIBRATING NEURAL PREDICTION MODEL... OK",
            "VERIFYING ENCRYPTION KEYS... [SUCCESS]",
            "DASHBOARD UI ALLOCATION...",
            "SYSTEM READY."
    };

    @FXML
    public void initialize() {
        setupVideoBackground();

        consoleOutput.setText("");
        loadingBlocks.setOpacity(0);
        logoView.setOpacity(0);

        playLogoIntro();
    }

    private void setupVideoBackground() {
        try {
            URL videoUrl = getClass().getResource("/com/aiq/videos/bg.mp4");
            if (videoUrl != null) {
                Media media = new Media(videoUrl.toExternalForm());
                mediaPlayer = new MediaPlayer(media);
                bgVideo.setMediaPlayer(mediaPlayer);
                mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                mediaPlayer.setMute(true);
                mediaPlayer.play();
            }
        } catch (Exception e) {
            System.err.println("Video Error: " + e.getMessage());
        }
    }

    private void playLogoIntro() {
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(1.5), logoView);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        fadeIn.setOnFinished(e -> {
            loadingBlocks.setOpacity(1.0);
            startTerminalBootSequence();
        });

        fadeIn.play();
    }

    private void startTerminalBootSequence() {
        Timeline textSpammer = new Timeline(new KeyFrame(Duration.millis(750), e -> {
            if (logIndex < bootLogs.length) {
                currentLog.append("> ").append(bootLogs[logIndex]).append("\n");
                consoleOutput.setText(currentLog.toString());
                logIndex++;
            }
        }));
        textSpammer.setCycleCount(bootLogs.length);
        textSpammer.play();

        Timeline blockLoader = new Timeline(new KeyFrame(Duration.millis(300), e -> {
            progress += 5;
            if (progress <= 100) {
                updateBlockBar(progress);
            }
        }));
        blockLoader.setCycleCount(20);

        blockLoader.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.seconds(1.0), rootPane);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(fadeEvent -> {
                if (mediaPlayer != null) mediaPlayer.stop();
                System.out.println("Transitioning to Dashboard...");
            });
            fadeOut.play();
        });

        blockLoader.play();
    }

    private void updateBlockBar(int percentage) {
        int totalBlocks = 20;
        int filledBlocks = (percentage * totalBlocks) / 100;

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < totalBlocks; i++) {
            if (i < filledBlocks) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        bar.append("] ").append(percentage).append("%");
        loadingBlocks.setText(bar.toString());
    }
}