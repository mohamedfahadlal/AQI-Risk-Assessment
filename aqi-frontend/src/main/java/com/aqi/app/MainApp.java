package com.aqi.app;

import javafx.application.Application;
import javafx.stage.Stage;
import com.aqi.utils.SceneManager;

public class MainApp extends Application {

    private static Process backendProcess;

    public static void main(String[] args) throws Exception {
        startBackend();
        // Wait for backend to be ready BEFORE launching JavaFX
        waitForBackend();
        launch(args);
    }

    private static void startBackend() {
        try {
            String jarPath = "C:\\Users\\Fahad\\Desktop\\my works\\Java AP project\\AQI-Risk-Assessment\\homepage_backend\\target\\aqi-backend-0.0.1-SNAPSHOT.jar";

            backendProcess = new ProcessBuilder("java", "-jar", jarPath)
                    .redirectErrorStream(true)
                    .start();

            System.out.println("Backend starting...");

        } catch (Exception e) {
            System.err.println("Failed to start backend: " + e.getMessage());
        }
    }

    private static void waitForBackend() {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

        for (int i = 0; i < 30; i++) {
            try {
                Thread.sleep(1000);
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8080/actuator/health"))
                        .timeout(java.time.Duration.ofSeconds(2))
                        .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    System.out.println("Backend is ready!");
                    return;
                }
            } catch (Exception e) {
                System.out.println("Waiting for backend... attempt " + (i + 1));
            }
        }
        System.err.println("Backend did not start in time. Continuing anyway...");
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        SceneManager.setPrimaryStage(primaryStage);
        SceneManager.switchScene("/fxml/Intro.fxml", "AQI Dashboard");
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (backendProcess != null && backendProcess.isAlive()) {
            backendProcess.destroy();
            System.out.println("Backend stopped.");
        }
    }
}
