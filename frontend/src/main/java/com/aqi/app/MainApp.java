package com.aqi.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // Load FXML
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/views/HealthProfile.fxml")
        );

        Parent root = loader.load();

        // Create Scene
        Scene scene = new Scene(root, 900, 650);

        // Load CSS Theme
        scene.getStylesheets().add(
                getClass().getResource("/styles/theme.css").toExternalForm()
        );

        // Stage Configuration
        stage.setTitle("AQI Health Advisory System");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
