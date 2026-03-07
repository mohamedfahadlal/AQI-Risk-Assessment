package com.aiq;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // This path must match your folder structure exactly
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aiq/fxml/LoadingScreen.fxml"));

        if (loader.getLocation() == null) {
            throw new RuntimeException("Error: Could not find LoadingScreen.fxml. Check your file path!");
        }

        Scene scene = new Scene(loader.load());
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}