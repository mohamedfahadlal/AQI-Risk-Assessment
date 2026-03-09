package com.aqi.app;

import javafx.application.Application;
import javafx.stage.Stage;
import com.aqi.utils.SceneManager;

import java.io.File;

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
            String projectRoot = System.getProperty("user.dir");

            File jarFile = new File(
                    projectRoot + "\\..\\homepage_backend\\target\\aqi-backend-0.0.1-SNAPSHOT.jar"
            ).getCanonicalFile();

            File propsFile = new File(
                    projectRoot + "\\..\\homepage_backend\\src\\main\\resources\\application.properties"
            ).getCanonicalFile();



            System.out.println("JAR:   " + jarFile.getAbsolutePath());
            System.out.println("Props: " + propsFile.getAbsolutePath());

            if (!jarFile.exists()) { System.err.println("JAR not found!"); return; }
            if (!propsFile.exists()) { System.err.println("application.properties not found!"); return; }

            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-jar", jarFile.getAbsolutePath(),
                    "--spring.config.location=file:" + propsFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);

            backendProcess = pb.start();
            System.out.println("Backend starting... check backend.log for details");

        } catch (Exception e) {
            System.err.println("Failed to start backend: " + e.getMessage());
        }
    }

    private static void startMLServer() {
        try {
            String projectRoot = System.getProperty("user.dir");

            File serverFile = new File(
                    projectRoot + "\\..\\ml\\server.py"
            ).getCanonicalFile();


            System.out.println("ML server: " + serverFile.getAbsolutePath());

            if (!serverFile.exists()) {
                System.err.println("ML server.py not found at: " + serverFile.getAbsolutePath());
                System.err.println("Prediction will use fallback mode.");
                return;
            }

            // Try 'python' first, then 'python3'
            ProcessBuilder pb;
            try {
                pb = new ProcessBuilder("python", serverFile.getAbsolutePath());
                pb.redirectErrorStream(true);

                mlProcess = pb.start();
                System.out.println("ML server starting with 'python'... check ml.log for details");
            } catch (Exception e) {
                pb = new ProcessBuilder("python3", serverFile.getAbsolutePath());
                pb.redirectErrorStream(true);

                mlProcess = pb.start();
                System.out.println("ML server starting with 'python3'... check ml.log for details");
            }

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
                var response = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
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
        if (mlProcess != null && mlProcess.isAlive()) {
            mlProcess.destroy();
            System.out.println("ML server stopped.");
        }
    }
}
