package com.aqi.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SceneManager {
    private static Stage stage;

    public static void setStage(Stage primaryStage) {
        stage = primaryStage;
    }

    public static void switchScene(String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(SceneManager.class.getResource(fxmlPath));
            Scene scene = new Scene(root);


            scene.getStylesheets().add(SceneManager.class.getResource("/css/styles.css").toExternalForm());

            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load FXML: " + fxmlPath);
        }
    }
}