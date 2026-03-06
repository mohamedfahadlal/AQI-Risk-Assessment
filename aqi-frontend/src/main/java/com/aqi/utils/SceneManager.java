package com.aqi.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Centralized scene switcher for the entire application.
 * All navigation goes through here so the Stage is always reused.
 */
public class SceneManager {

    private static Stage primaryStage;

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Switch to a new scene by FXML resource path.
     * Path should start with "/" e.g. "/fxml/Login.fxml" or "/views/HealthProfile.fxml"
     * Window size is preserved between navigations.
     */
    public static void switchScene(String fxmlPath) {
        try {
            URL resource = SceneManager.class.getResource(fxmlPath);
            if (resource == null) {
                System.err.println("FXML not found: " + fxmlPath);
                return;
            }
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();

            double width  = primaryStage.getScene() != null ? primaryStage.getScene().getWidth()  : 900;
            double height = primaryStage.getScene() != null ? primaryStage.getScene().getHeight() : 650;

            Scene scene = new Scene(root, width, height);

            // Apply global stylesheet if it exists
            URL css = SceneManager.class.getResource("/css/styles.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            // Also try theme.css from HealthProfile module
            URL theme = SceneManager.class.getResource("/styles/theme.css");
            if (theme != null) {
                scene.getStylesheets().add(theme.toExternalForm());
            }

            primaryStage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Overload: switch scene and also update the window title.
     */
    public static void switchScene(String fxmlPath, String title) {
        switchScene(fxmlPath);
        primaryStage.setTitle(title);
    }
}
