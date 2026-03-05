package com.aqi;

import com.aqi.utils.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        SceneManager.setStage(primaryStage);
        primaryStage.setTitle("AQI Health Advisory System");
        SceneManager.switchScene("/fxml/Login.fxml");
        primaryStage.setTitle("AQI Real-time Dashboard");
        primaryStage.setWidth(1000);
        primaryStage.setHeight(650);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
