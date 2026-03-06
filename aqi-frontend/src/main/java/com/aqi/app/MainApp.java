package com.aqi.app;

import javafx.application.Application;
import javafx.stage.Stage;
import com.aqi.utils.SceneManager;

import java.io.File;
import java.nio.file.Paths;

public class MainApp extends Application {

    private static Process backendProcess;
    private static Process mlProcess;

    public static void main(String[] args) throws Exception {
        startBackend();
        startMLServer();
        waitForBackend();
        launch(args);
    }

    private static void startBackend() {
        try {
            String projectRoot = findProjectRoot();
            String jarPath = projectRoot + File.separator + "homepage_backend"
                    + File.separator + "target"
                    + File.separator + "aqi-backend-0.0.1-SNAPSHOT.jar";

            File jarFile = new File(jarPath);
            if (!jarFile.exists()) {
                System.err.println("Backend JAR not found at: " + jarPath);
                return;
            }

            backendProcess = new ProcessBuilder("java", "-jar", jarPath)
                    .redirectErrorStream(true)
                    .start();
            System.out.println("Backend starting...");

        } catch (Exception e) {
            System.err.println("Failed to start backend: " + e.getMessage());
        }
    }

    private static void startMLServer() {
        try {
            String projectRoot = findProjectRoot();
            String serverPath = projectRoot + File.separator + "ml"
                    + File.separator + "server.py";

            File serverFile = new File(serverPath);
            if (!serverFile.exists()) {
                System.err.println("ML server not found at: " + serverPath);
                System.err.println("Prediction will use fallback mode.");
                return;
            }

            System.out.println("Starting ML server from: " + serverPath);

            // Try 'python' first, fall back to 'python3'
            try {
                mlProcess = new ProcessBuilder("python", serverPath)
                        .redirectErrorStream(true)
                        .start();
            } catch (Exception e) {
                mlProcess = new ProcessBuilder("python3", serverPath)
                        .redirectErrorStream(true)
                        .start();
            }

            System.out.println("ML server starting...");

        } catch (Exception e) {
            System.err.println("Failed to start ML server: " + e.getMessage());
            System.err.println("Prediction will use fallback mode.");
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

    private static String findProjectRoot() {
        File dir = new File(System.getProperty("user.dir"));
        while (dir != null) {
            File backendFolder = new File(dir, "homepage_backend");
            if (backendFolder.exists() && backendFolder.isDirectory()) {
                return dir.getAbsolutePath();
            }
            dir = dir.getParentFile();
        }
        return Paths.get(System.getProperty("user.dir")).getParent().toString();
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
        if (mlProcess != null && mlProcess.isAlive()) {
            mlProcess.destroy();
            System.out.println("ML server stopped.");
        }
    }
}