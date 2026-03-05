package com.example.test2;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        URL fxmlLocation = getClass().getResource("/main_view.fxml");

        if (fxmlLocation == null) {
            throw new IllegalStateException("Cannot find main_view.fxml. Ensure it is placed directly inside the src/main/resources/ folder.");
        }

        FXMLLoader fxmlLoader = new FXMLLoader(fxmlLocation);
        Scene scene = new Scene(fxmlLoader.load(), 1100, 700);        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}