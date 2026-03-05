package com.aqi.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        Parent root = FXMLLoader.load(
                getClass().getResource("/views/HealthProfile.fxml")
        );

        Scene scene = new Scene(root, 900, 650);

        scene.getStylesheets().add(
                getClass().getResource("/styles/theme.css").toExternalForm()
        );

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
