package com.aqi.app;

import com.aqi.utils.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Single entry point for the entire AQI Risk Assessment application.
 * Launches from the Intro screen and navigates through:
 * Intro → Login/SignUp → HealthProfile (new users) → Dashboard → Prediction / ViewProfile
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        SceneManager.setPrimaryStage(primaryStage);
        primaryStage.setTitle("AQI Risk Assessment");
        primaryStage.setResizable(true);

        // Start at Intro screen
        SceneManager.switchScene("/fxml/Intro.fxml");

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
