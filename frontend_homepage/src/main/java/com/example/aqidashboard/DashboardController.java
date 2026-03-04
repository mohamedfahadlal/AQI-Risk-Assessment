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
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class DashboardController {

    private static final String BACKEND = "http://localhost:8080/api";

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

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContextMenu suggestionsPopup = new ContextMenu();

    // Stores lat/lon from selected suggestion for direct coord lookup
    private double selectedLat = 0, selectedLon = 0;

    @FXML
    public void initialize() {
        updateNeedle(0);
        statusLabel.setText("");
        aqiLabel.setText("--");

        // Trigger search suggestions as user types
        citySearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            selectedLat = 0;
            selectedLon = 0;
            if (newVal == null || newVal.length() < 2) {
                suggestionsPopup.hide();
                return;
            }
            fetchSuggestions(newVal);
        });

        loadAQIData("Kochi", 0, 0);
    }

    // ── Fetch autocomplete suggestions ───────────────────────────
    private void fetchSuggestions(String query) {
        Thread thread = new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String url = BACKEND + "/search?q=" + encoded;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                JsonNode results = objectMapper.readTree(response.body());

                Platform.runLater(() -> {
                    suggestionsPopup.getItems().clear();
                    if (!results.isArray() || results.size() == 0) {
                        suggestionsPopup.hide();
                        return;
                    }

                    for (JsonNode r : results) {
                        String display = r.path("display").asText();
                        String city    = r.path("city").asText();
                        double lat     = r.path("lat").asDouble();
                        double lon     = r.path("lon").asDouble();

                        MenuItem item = new MenuItem(display);
                        item.setStyle("-fx-font-size: 13px;");
                        item.setOnAction(e -> {
                            citySearchField.setText(display);
                            suggestionsPopup.hide();
                            selectedLat = lat;
                            selectedLon = lon;
                            loadAQIData(city, lat, lon);
                        });
                        suggestionsPopup.getItems().add(item);
                    }

                    if (!suggestionsPopup.isShowing()) {
                        suggestionsPopup.show(citySearchField,
                                javafx.geometry.Side.BOTTOM, 0, 0);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    // ── Search button ─────────────────────────────────────────────
    @FXML
    private void handleCitySearch() {
        String text = citySearchField.getText();
        if (text == null || text.isEmpty()) return;

        if (selectedLat != 0 && selectedLon != 0) {
            // Use coords from suggestion selection
            loadAQIData(text, selectedLat, selectedLon);
        } else {
            // Plain city name search
            loadAQIData(text, 0, 0);
        }
    }

    // ── Locate Me button ──────────────────────────────────────────
    @FXML
    private void handleDetectLocation() {
        Platform.runLater(() -> {
            aqiLabel.setText("...");
            statusLabel.setText("Locating...");
        });

        Thread thread = new Thread(() -> {
            try {
                // IP geolocation (free, no key)
                HttpRequest geoRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://ipapi.co/json/")).GET().build();
                HttpResponse<String> geoResponse = httpClient.send(
                        geoRequest, HttpResponse.BodyHandlers.ofString());

                JsonNode geo = objectMapper.readTree(geoResponse.body());
                double lat   = geo.path("latitude").asDouble();
                double lon   = geo.path("longitude").asDouble();
                String city  = geo.path("city").asText("Your Location");

                Platform.runLater(() -> citySearchField.setText("📍 " + city));

                // Call locate endpoint
                String url = BACKEND + "/aqi/locate?lat=" + lat + "&lon=" + lon;
                HttpRequest aqiRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url)).GET().build();
                HttpResponse<String> aqiResponse = httpClient.send(
                        aqiRequest, HttpResponse.BodyHandlers.ofString());

                if (aqiResponse.statusCode() == 200) {
                    updateUIFromResponse(aqiResponse.body());
                }

            } catch (Exception e) {
                Platform.runLater(() -> showError("Could not detect location"));
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    // ── Load AQI data ─────────────────────────────────────────────
    private void loadAQIData(String city, double lat, double lon) {
        Platform.runLater(() -> {
            aqiLabel.setText("...");
            statusLabel.setText("Loading");
        });

        Thread thread = new Thread(() -> {
            try {
                String url;
                if (lat != 0 && lon != 0) {
                    url = BACKEND + "/aqi/locate?lat=" + lat + "&lon=" + lon;
                } else {
                    url = BACKEND + "/aqi?city=" + URLEncoder.encode(city, StandardCharsets.UTF_8);
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    updateUIFromResponse(response.body());
                } else {
                    JsonNode err = objectMapper.readTree(response.body());
                    Platform.runLater(() -> showError(err.path("error").asText("Failed")));
                }

            } catch (Exception e) {
                Platform.runLater(() -> showError("Cannot connect to server"));
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    // ── Update UI from JSON response ──────────────────────────────
    private void updateUIFromResponse(String body) throws Exception {
        JsonNode data     = objectMapper.readTree(body);
        int aqi           = data.path("aqi").asInt();
        String cityName   = data.path("city").asText("--");
        double pm25       = data.path("pm25").asDouble();
        double pm10       = data.path("pm10").asDouble();
        double temp       = data.path("temperature").asDouble();
        double humidity   = data.path("humidity").asDouble();
        double wind       = data.path("windSpeed").asDouble();

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
    }

    private void showError(String message) {
        aqiLabel.setText("--");
        statusLabel.setText("Error");
        cityLabel.setText(message);
    }

    // ── Needle ────────────────────────────────────────────────────
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

    // ── Risk Level ────────────────────────────────────────────────
    private void updateRisk(int aqi) {
        String risk, hexColor, gradientStyle, imageName;
        String baseStyle = "-fx-background-radius: 30; -fx-effect: dropshadow(gaussian,"
                + " rgba(0,0,0,0.15), 30, 0, 0, 10); -fx-padding: 30 50;";

        if (aqi <= 50) {
            risk = "Good";       hexColor = "#2ecc71";
            gradientStyle = "linear-gradient(to bottom right, #eafaf1, #d5f5e3)";
            imageName = "good.png";
        } else if (aqi <= 100) {
            risk = "Moderate";   hexColor = "#f1c40f";
            gradientStyle = "linear-gradient(to bottom right, #fef9e7, #f9e79f)";
            imageName = "moderate.png";
        } else if (aqi <= 150) {
            risk = "Poor";       hexColor = "#e67e22";
            gradientStyle = "linear-gradient(to bottom right, #fdf2e9, #fad7a0)";
            imageName = "poor.png";
        } else if (aqi <= 200) {
            risk = "Unhealthy";  hexColor = "#d35400";
            gradientStyle = "linear-gradient(to bottom right, #fbeee6, #edbb99)";
            imageName = "unhealthy.png";
        } else if (aqi <= 300) {
            risk = "Severe";     hexColor = "#8e44ad";
            gradientStyle = "linear-gradient(to bottom right, #f4ecf7, #d7bde2)";
            imageName = "severe.png";
        } else {
            risk = "Hazardous";  hexColor = "#c0392b";
            gradientStyle = "linear-gradient(to bottom right, #fadbd8, #f1948a)";
            imageName = "hazardous.png";
        }

        statusLabel.setText(risk);
        statusLabel.setStyle("-fx-text-fill: " + hexColor + "; -fx-font-weight: bold; -fx-font-size: 24px;");
        aqiLabel.setStyle("-fx-text-fill: " + hexColor + "; -fx-font-weight: 900; -fx-font-size: 55px;");
        mainCard.setStyle("-fx-background-color: " + gradientStyle + "; " + baseStyle);

        try {
            Image image = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/images/" + imageName)));
            aqiImageView.setImage(image);
        } catch (Exception e) {
            System.out.println("Image not found: " + imageName);
        }
    }
}