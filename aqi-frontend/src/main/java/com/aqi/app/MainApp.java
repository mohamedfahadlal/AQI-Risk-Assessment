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

    private static Process backendProcess;

    public static void main(String[] args) throws Exception {
        startBackend();
        launch(args);
    }

    private static void startBackend() {
        try {
            // Path to your backend JAR (build it first with mvn package)
            String jarPath = System.getProperty("user.dir") + "\\..\\homepage_backend\\target\\aqi-backend-0.0.1-SNAPSHOT.jar";

            backendProcess = new ProcessBuilder("java", "-jar", jarPath)
                    .redirectErrorStream(true)
                    .start();

            System.out.println("Backend starting...");

            // Wait for backend to be ready (poll until it responds)
            waitForBackend();

        } catch (Exception e) {
            System.err.println("Failed to start backend: " + e.getMessage());
        }
    }

    private static void waitForBackend() {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:8080/actuator/health"))
                .build();

        for (int i = 0; i < 30; i++) { // try for 30 seconds
            try {
                Thread.sleep(1000);
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    System.out.println("Backend is ready!");
                    return;
                }
            } catch (Exception e) {
                System.out.println("Waiting for backend... attempt " + (i + 1));
            }
        }
    }

    // Shut down backend when app closes
    @Override
    public void stop() {
        if (backendProcess != null && backendProcess.isAlive()) {
            backendProcess.destroy();
            System.out.println("Backend stopped.");
        }
    }
}
