package com.example.aqidashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.transform.Rotate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DashboardController {

    // ── Backend URL ──────────────────────────────────────────────
    private static final String BACKEND_URL = "http://localhost:8080/api/aqi";

    @FXML private TextField citySearchField;
    @FXML private Label cityLabel;
    @FXML private Label aqiLabel;
    @FXML private Label statusLabel;
    @FXML private Label tempLabel;
    @FXML private Label humidityLabel;
    @FXML private Label windLabel;
    @FXML private Label pm25Label;
    @FXML private Label pm10Label;
    @FXML private Line needle;
    @FXML private VBox mainCard;
    @FXML private ImageView aqiImageView;

    private String currentCity = "Kochi";
    private final ContextMenu suggestionsPopup = new ContextMenu();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<String> cityList = Arrays.asList(
            "Kochi", "Kollam", "Kozhikode", "Kannur",
            "Kottayam", "Thiruvananthapuram", "Delhi", "Mumbai", "Chennai"
    );

    // ── Init ──────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        updateNeedle(0);
        statusLabel.setText("");
        aqiLabel.setText("--");

        // Auto-suggest on typing
        citySearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                suggestionsPopup.hide();
                return;
            }
            List<String> filtered = cityList.stream()
                    .filter(c -> c.toLowerCase().startsWith(newVal.toLowerCase()))
                    .collect(Collectors.toList());

            if (filtered.isEmpty()) {
                suggestionsPopup.hide();
            } else {
                populatePopup(filtered);
                if (!suggestionsPopup.isShowing()) {
                    suggestionsPopup.show(citySearchField, javafx.geometry.Side.BOTTOM, 0, 0);
                }
            }
        });

        // Load default city on startup
        loadAQIData(currentCity);
    }

    // ── Handlers ──────────────────────────────────────────────────
    @FXML
    private void handleCitySearch() {
        String entered = citySearchField.getText();
        if (entered != null && !entered.isEmpty()) {
            currentCity = entered;
            loadAQIData(currentCity);
        }
    }

    @FXML
    private void handleDetectLocation() {
        // TODO: replace with real GPS/IP geolocation later
        currentCity = "Kochi";
        citySearchField.setText(currentCity);
        loadAQIData(currentCity);
    }

    // ── Load AQI from Backend ─────────────────────────────────────
    private void loadAQIData(String city) {
        // Show loading state
        Platform.runLater(() -> {
            aqiLabel.setText("...");
            statusLabel.setText("Loading");
            cityLabel.setText(city);
        });

        // Call backend on a background thread (keeps UI responsive)
        Thread thread = new Thread(() -> {
            try {
                String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
                String url = BACKEND_URL + "?city=" + encodedCity;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() == 200) {
                    JsonNode data = objectMapper.readTree(response.body());

                    int aqi           = data.path("aqi").asInt();
                    String cityName   = data.path("city").asText(city);
                    double pm25       = data.path("pm25").asDouble();
                    double pm10       = data.path("pm10").asDouble();
                    double temp       = data.path("temperature").asDouble();
                    double humidity   = data.path("humidity").asDouble();
                    double wind       = data.path("windSpeed").asDouble();

                    // Update UI on JavaFX thread
                    Platform.runLater(() -> {
                        cityLabel.setText(cityName);
                        aqiLabel.setText(String.valueOf(aqi));
                        pm25Label.setText(String.format("%.1f µg/m³", pm25));
                        pm10Label.setText(String.format("%.1f µg/m³", pm10));
                        tempLabel.setText(String.format("%.1f°C", temp));
                        humidityLabel.setText(String.format("%.0f%%", humidity));
                        windLabel.setText(String.format("%.1f km/h", wind));
                        updateNeedle(aqi);
                        updateRisk(aqi);
                    });

                } else {
                    JsonNode err = objectMapper.readTree(response.body());
                    String msg = err.path("error").asText("Failed to load data");
                    Platform.runLater(() -> showError(msg));
                }

            } catch (Exception e) {
                Platform.runLater(() -> showError("Cannot connect to server. Is Spring Boot running?"));
                e.printStackTrace();
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        aqiLabel.setText("--");
        statusLabel.setText("Error");
        cityLabel.setText(message);
    }

    // ── Suggestions Popup ────────────────────────────────────────
    private void populatePopup(List<String> results) {
        List<MenuItem> items = new ArrayList<>();
        for (String result : results) {
            MenuItem item = new MenuItem(result);
            item.setOnAction(e -> {
                citySearchField.setText(result);
                suggestionsPopup.hide();
                loadAQIData(result);
            });
            items.add(item);
        }
        suggestionsPopup.getItems().clear();
        suggestionsPopup.getItems().addAll(items);
    }

    // ── Needle ───────────────────────────────────────────────────
    private void updateNeedle(int aqi) {
        double angle;
        if (aqi <= 50)        angle = (aqi / 50.0) * 30;
        else if (aqi <= 100)  angle = 30 + ((aqi - 50) / 50.0) * 30;
        else if (aqi <= 150)  angle = 60 + ((aqi - 100) / 50.0) * 30;
        else if (aqi <= 200)  angle = 90 + ((aqi - 150) / 50.0) * 30;
        else if (aqi <= 300)  angle = 120 + ((aqi - 200) / 100.0) * 30;
        else                  angle = 150 + ((Math.min(aqi, 500) - 300) / 200.0) * 30;

        Rotate rotate = new Rotate(angle, 200, 200);
        needle.getTransforms().clear();
        needle.getTransforms().add(rotate);
    }

    // ── Risk Level ───────────────────────────────────────────────
    private void updateRisk(int aqi) {
        String risk, hexColor, gradientStyle, imageName;
        String baseStyle = "-fx-background-radius: 30; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 30, 0, 0, 10); -fx-padding: 30 50;";

        if (aqi <= 50) {
            risk = "Good";           hexColor = "#2ecc71";
            gradientStyle = "linear-gradient(to bottom right, #eafaf1, #d5f5e3)";
            imageName = "good.png";
        } else if (aqi <= 100) {
            risk = "Moderate";       hexColor = "#f1c40f";
            gradientStyle = "linear-gradient(to bottom right, #fef9e7, #f9e79f)";
            imageName = "moderate.png";
        } else if (aqi <= 150) {
            risk = "Poor";           hexColor = "#e67e22";
            gradientStyle = "linear-gradient(to bottom right, #fdf2e9, #fad7a0)";
            imageName = "poor.png";
        } else if (aqi <= 200) {
            risk = "Unhealthy";      hexColor = "#d35400";
            gradientStyle = "linear-gradient(to bottom right, #fbeee6, #edbb99)";
            imageName = "unhealthy.png";
        } else if (aqi <= 300) {
            risk = "Severe";         hexColor = "#8e44ad";
            gradientStyle = "linear-gradient(to bottom right, #f4ecf7, #d7bde2)";
            imageName = "severe.png";
        } else {
            risk = "Hazardous";      hexColor = "#c0392b";
            gradientStyle = "linear-gradient(to bottom right, #fadbd8, #f1948a)";
            imageName = "hazardous.png";
        }

        statusLabel.setText(risk);
        statusLabel.setStyle("-fx-text-fill: " + hexColor + "; -fx-font-weight: bold; -fx-font-size: 24px;");
        aqiLabel.setStyle("-fx-text-fill: " + hexColor + "; -fx-font-weight: 900; -fx-font-size: 55px;");
        mainCard.setStyle("-fx-background-color: " + gradientStyle + "; " + baseStyle);

        try {
            Image image = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/images/" + imageName)
            ));
            aqiImageView.setImage(image);
        } catch (Exception e) {
            System.out.println("Image not found: " + imageName);
        }
    }
}
