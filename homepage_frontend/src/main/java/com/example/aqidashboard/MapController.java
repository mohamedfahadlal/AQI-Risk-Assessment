package com.example.aqidashboard;

import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import java.util.Objects;

public class MapController {

    @FXML private WebView mapWebView;

    @FXML
    public void initialize() {
        WebEngine engine = mapWebView.getEngine();

        String mapUrl = Objects.requireNonNull(
                getClass().getResource("/com/example/aqidashboard/map.html")
        ).toExternalForm();

        engine.load(mapUrl);

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                System.out.println("Map loaded successfully");
            } else if (newState == Worker.State.FAILED) {
                System.out.println("Map failed to load");
            }
        });
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/aqidashboard/dashboard-view.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) mapWebView.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("AQI Dashboard");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}