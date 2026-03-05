package com.example.aqidashboard;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import java.util.Objects;

public class MapController {

    @FXML private WebView mapWebView;

    @FXML
    public void initialize() {
        WebEngine engine = mapWebView.getEngine();
        engine.setJavaScriptEnabled(true);

        // Bind WebView size to parent so it always fills fully
        mapWebView.prefWidthProperty().bind(
            mapWebView.getParent() != null
                ? mapWebView.parentProperty().get().layoutBoundsProperty().map(b -> b.getWidth())
                : mapWebView.prefWidthProperty()
        );

        String mapUrl = Objects.requireNonNull(
                getClass().getResource("/com/example/aqidashboard/map.html")
        ).toExternalForm();

        engine.load(mapUrl);

        // After scene is available, bind size to stage
        mapWebView.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Stage stage = (Stage) newScene.getWindow();
                if (stage != null) {
                    mapWebView.prefWidthProperty().bind(stage.widthProperty());
                    mapWebView.prefHeightProperty().bind(
                        stage.heightProperty().subtract(50) // subtract top bar height
                    );
                }
                // Also bind when window becomes available
                newScene.windowProperty().addListener((obs2, oldWin, newWin) -> {
                    if (newWin instanceof Stage s) {
                        mapWebView.prefWidthProperty().bind(s.widthProperty());
                        mapWebView.prefHeightProperty().bind(s.heightProperty().subtract(50));
                    }
                });
            }
        });
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/aqidashboard/dashboard-view.fxml"));
            Stage stage = (Stage) mapWebView.getScene().getWindow();
            Scene scene = new Scene(loader.load(),
                    stage.getScene().getWidth(),
                    stage.getScene().getHeight());
            stage.setScene(scene);
            stage.setTitle("AQI Dashboard");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
