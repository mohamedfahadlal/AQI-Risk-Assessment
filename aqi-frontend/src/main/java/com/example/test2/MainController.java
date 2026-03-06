package com.example.test2;

import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

public class MainController {

    private static final String BACKEND    = "http://localhost:8080/api";
    private static final String ML_SERVER  = "http://localhost:5000";

    /* ── FXML Controls ─────────────────────────────────────────── */
    @FXML private VBox    rootBox;
    @FXML private HBox    topInputArea;
    @FXML private HBox    mainArea;
    @FXML private Label   headerLabel;
    @FXML private TextField cityField;
    @FXML private Button  predictButton;
    @FXML private Label   symptomsTitle;
    @FXML private CheckBox symptomBreath;
    @FXML private CheckBox symptomCough;
    @FXML private CheckBox symptomChest;
    @FXML private CheckBox symptomIrritation;
    @FXML private CheckBox symptomFatigue;
    @FXML private VBox    aqiCard;
    @FXML private Label   cityLabel;
    @FXML private StackPane aqiCircle;
    @FXML private Label   aqiLabel;
    @FXML private ImageView aqiImage;
    @FXML private Label   aqiStatus;
    @FXML private Label   pm25Label;
    @FXML private Label   pm10Label;
    @FXML private VBox    adviceCard;
    @FXML private Label   adviceTitle;
    @FXML private Label   adviceText;

    private final HttpClient   httpClient   = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegerProperty currentAqiValue = new SimpleIntegerProperty(0);
    private Timeline aqiTimeline;

    // Cached current AQI data for ML features
    private JsonNode lastAqiData = null;

    /* ── Initialize ────────────────────────────────────────────── */
    @FXML
    public void initialize() {
        buildProgrammaticUI();
        aqiLabel.textProperty().bind(currentAqiValue.asString());
        setupAutoComplete();
    }

    /* ── Navigation ────────────────────────────────────────────── */
    @FXML
    private void handleBackToDashboard() {
        SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard");
    }

    /* ── Main Predict Action ───────────────────────────────────── */
    @FXML
    private void searchCityAQI() {
        String city = cityField.getText().trim();
        if (city.isEmpty()) {
            showError("Please enter a city name.");
            return;
        }

        predictButton.setDisable(true);
        predictButton.setText("Predicting...");
        cityLabel.setText("Loading...");
        aqiStatus.setText("...");

        new Thread(() -> {
            try {
                // Step 1: Fetch current real AQI from Spring Boot backend
                String encoded = URLEncoder.encode(city, StandardCharsets.UTF_8);
                HttpRequest aqiReq = HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND + "/aqi?city=" + encoded))
                        .GET().build();
                HttpResponse<String> aqiRes = httpClient.send(
                        aqiReq, HttpResponse.BodyHandlers.ofString());

                if (aqiRes.statusCode() != 200) {
                    Platform.runLater(() -> showError("City not found: " + city));
                    return;
                }

                JsonNode aqiData = objectMapper.readTree(aqiRes.body());
                lastAqiData = aqiData;

                int currentAqi   = aqiData.path("aqi").asInt();
                double pm25      = aqiData.path("pm25").asDouble();
                double pm10      = aqiData.path("pm10").asDouble();
                double no2       = aqiData.path("no2").asDouble();
                double o3        = aqiData.path("o3").asDouble();
                double co        = aqiData.path("co").asDouble();
                double so2       = aqiData.path("so2").asDouble();
                double temp      = aqiData.path("temperature").asDouble();
                double humidity  = aqiData.path("humidity").asDouble();
                double wind      = aqiData.path("windSpeed").asDouble();
                String cityName  = aqiData.path("city").asText(city);

                // Step 2: Call ML server for next-hour prediction
                int predictedAqi = callMLServer(currentAqi, pm25, pm10, no2, o3,
                                                co, so2, temp, humidity, wind);

                // Step 3: Fetch health profile for risk scoring
                JsonNode profile = fetchHealthProfile();

                // Step 4: Calculate risk score
                String riskAdvice = buildRiskAdvice(predictedAqi, profile);

                // Step 5: Update UI
                final int finalPredicted = predictedAqi;
                final String finalCity   = cityName;
                final double finalPm25   = pm25;
                final double finalPm10   = pm10;

                Platform.runLater(() -> {
                    updateUI(finalCity, finalPredicted, finalPm25, finalPm10, riskAdvice);
                    predictButton.setDisable(false);
                    predictButton.setText("PREDICT NOW");
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showError("Error: " + e.getMessage());
                    predictButton.setDisable(false);
                    predictButton.setText("PREDICT NOW");
                });
            }
        }).start();
    }

    /* ── ML Server Call ────────────────────────────────────────── */
    private int callMLServer(int currentAqi, double pm25, double pm10,
                              double no2, double o3, double co, double so2,
                              double temp, double humidity, double wind) {
        try {
            LocalDateTime now = LocalDateTime.now();

            Map<String, Object> payload = new HashMap<>();
            payload.put("current_aqi",       currentAqi);
            payload.put("pm25",              pm25);
            payload.put("pm10",              pm10);
            payload.put("no2",               no2);
            payload.put("nox",               no2);  // approximate
            payload.put("o3",                o3);
            payload.put("co",                co);
            payload.put("so2",               so2);
            payload.put("temperature",       temp);
            payload.put("relativehumidity",  humidity);
            payload.put("wind_speed",        wind);
            payload.put("wind_direction",    180.0);
            payload.put("aqi_lag_1",         currentAqi);
            payload.put("aqi_lag_2",         currentAqi);
            payload.put("hour",              now.getHour());
            payload.put("day_of_week",       now.getDayOfWeek().getValue() - 1);
            payload.put("month",             now.getMonthValue());

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ML_SERVER + "/predict"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> res = httpClient.send(
                    req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                JsonNode result = objectMapper.readTree(res.body());
                return result.path("predicted_aqi").asInt(currentAqi);
            }

        } catch (Exception e) {
            System.out.println("ML server unavailable, using fallback prediction: " + e.getMessage());
        }

        // Fallback: simple trend-based prediction if ML server is down
        return fallbackPredict(currentAqi, pm25, pm10);
    }

    private int fallbackPredict(int currentAqi, double pm25, double pm10) {
        int predicted = currentAqi;
        if (pm25 > 150) predicted += 20;
        else if (pm25 > 80) predicted += 10;
        if (pm10 > 200) predicted += 15;
        else if (pm10 > 100) predicted += 5;
        return Math.min(predicted, 500);
    }

    /* ── Health Profile Fetch ──────────────────────────────────── */
    private JsonNode fetchHealthProfile() {
        try {
            String userId = UserSession.getUserId();
            if (userId == null || userId.isEmpty()) return null;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND + "/health-profile/" + userId))
                    .GET().build();
            HttpResponse<String> res = httpClient.send(
                    req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                return objectMapper.readTree(res.body());
            }
        } catch (Exception e) {
            System.out.println("Could not fetch health profile: " + e.getMessage());
        }
        return null;
    }

    /* ── Risk Score & Advisory ─────────────────────────────────── */
    private String buildRiskAdvice(int predictedAqi, JsonNode profile) {
        int riskScore = 0;

        // AQI contribution (0-50 points)
        if      (predictedAqi <= 50)  riskScore += 5;
        else if (predictedAqi <= 100) riskScore += 15;
        else if (predictedAqi <= 150) riskScore += 25;
        else if (predictedAqi <= 200) riskScore += 35;
        else if (predictedAqi <= 300) riskScore += 45;
        else                          riskScore += 50;

        // Health profile contribution (0-30 points)
        if (profile != null) {
            int age = profile.path("age").asInt(0);
            if (age > 60 || age < 12) riskScore += 10;
            else if (age > 45)        riskScore += 5;

            if (profile.path("is_smoker").asBoolean(false))   riskScore += 10;
            if (profile.path("is_allergic").asBoolean(false)) riskScore += 5;
            if (profile.path("is_pregnant").asBoolean(false)) riskScore += 8;

            JsonNode conditions = profile.path("breathing_conditions");
            if (conditions.isArray() && conditions.size() > 0) riskScore += 10;

            String asthma = profile.path("asthma_breathing").asText("None");
            if (!asthma.equals("None") && !asthma.isEmpty()) riskScore += 8;
        }

        // Symptoms contribution (0-20 points)
        if (symptomBreath.isSelected())    riskScore += 8;
        if (symptomCough.isSelected())     riskScore += 4;
        if (symptomChest.isSelected())     riskScore += 8;
        if (symptomIrritation.isSelected()) riskScore += 3;
        if (symptomFatigue.isSelected())   riskScore += 4;

        riskScore = Math.min(riskScore, 100);

        // Build advisory text
        StringBuilder advice = new StringBuilder();

        // Risk level
        String riskLevel;
        if      (riskScore <= 20) riskLevel = "LOW";
        else if (riskScore <= 40) riskLevel = "MODERATE";
        else if (riskScore <= 60) riskLevel = "HIGH";
        else if (riskScore <= 80) riskLevel = "VERY HIGH";
        else                      riskLevel = "CRITICAL";

        advice.append("RISK SCORE: ").append(riskScore).append("/100 (").append(riskLevel).append(")\n\n");

        // AQI status
        advice.append("PREDICTED AQI (Next Hour):\n");
        if      (predictedAqi <= 50)  advice.append("Good — Air quality is satisfactory.\n\n");
        else if (predictedAqi <= 100) advice.append("Satisfactory — Acceptable for most people.\n\n");
        else if (predictedAqi <= 150) advice.append("Moderate — Sensitive groups may be affected.\n\n");
        else if (predictedAqi <= 200) advice.append("Poor — Everyone may experience health effects.\n\n");
        else if (predictedAqi <= 300) advice.append("Very Poor — Health alert. Avoid outdoor activity.\n\n");
        else                          advice.append("Severe — Emergency conditions. Stay indoors.\n\n");

        // Profile-based advice
        if (profile != null) {
            advice.append("PERSONAL RISK FACTORS:\n");
            int age = profile.path("age").asInt(0);
            if (age > 60) advice.append("• Senior citizen — higher respiratory sensitivity.\n");
            if (age < 12) advice.append("• Child — lungs still developing, extra caution needed.\n");
            if (profile.path("is_smoker").asBoolean(false))
                advice.append("• Smoker — significantly elevated risk, avoid outdoors.\n");
            if (profile.path("is_allergic").asBoolean(false))
                advice.append("• Allergic — carry antihistamines, wear N95 mask.\n");
            if (profile.path("is_pregnant").asBoolean(false))
                advice.append("• Pregnant — limit outdoor exposure, consult doctor.\n");
            String asthma = profile.path("asthma_breathing").asText("None");
            if (!asthma.equals("None") && !asthma.isEmpty())
                advice.append("• Breathing condition detected — keep inhaler ready.\n");
            advice.append("\n");
        }

        // Symptom advice
        boolean anySymptom = symptomBreath.isSelected() || symptomCough.isSelected() ||
                symptomChest.isSelected() || symptomIrritation.isSelected() ||
                symptomFatigue.isSelected();
        if (anySymptom) {
            advice.append("SYMPTOM MANAGEMENT:\n");
            if (symptomBreath.isSelected())
                advice.append(predictedAqi > 150 ? "• Breathlessness: Use fast-acting inhaler. Seek help if severe.\n"
                                                 : "• Breathlessness: Reduce exertion, rest indoors.\n");
            if (symptomCough.isSelected())
                advice.append(predictedAqi > 150 ? "• Cough: Run HEPA purifier. Avoid outdoor exposure.\n"
                                                 : "• Cough: Wear N95 mask outdoors.\n");
            if (symptomChest.isSelected())
                advice.append(predictedAqi > 150 ? "• Chest pain: URGENT — consult a doctor immediately.\n"
                                                 : "• Chest tightness: Rest completely, avoid exertion.\n");
            if (symptomIrritation.isSelected())
                advice.append("• Eye/throat irritation: Wash face, use lubricating eye drops.\n");
            if (symptomFatigue.isSelected())
                advice.append("• Fatigue: Stay hydrated, rest indoors with windows closed.\n");
            advice.append("\n");
        }

        // General precautions
        if (predictedAqi > 100) {
            advice.append("PRECAUTIONS:\n");
            advice.append("• Keep windows and doors closed.\n");
            advice.append("• Use air purifier if available.\n");
            advice.append("• Wear N95 mask if going outdoors.\n");
            advice.append("• Avoid morning outdoor exercise.\n");
            if (predictedAqi > 200)
                advice.append("• EMERGENCY: Minimize all outdoor activity.\n");
        }

        return advice.toString();
    }

    /* ── UI Update ─────────────────────────────────────────────── */
    private void updateUI(String city, int predictedAqi, double pm25, double pm10, String advice) {
        cityLabel.setText(city.toUpperCase());
        pm25Label.setText("PM2.5: " + String.format("%.1f", pm25) + " µg/m³");
        pm10Label.setText("PM10: " + String.format("%.1f", pm10) + " µg/m³");
        adviceText.setText(advice);

        if (aqiTimeline != null) aqiTimeline.stop();
        currentAqiValue.set(0);
        aqiTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1.2), new KeyValue(currentAqiValue, predictedAqi)));
        aqiTimeline.play();

        String imageName;
        Color alertColor;
        if (predictedAqi <= 50)  {
            applyStyles("GOOD",      Color.web("#2ECC71"), "/images/good.png",      predictedAqi);
        } else if (predictedAqi <= 100) {
            applyStyles("SATISFACTORY", Color.web("#A8D700"), "/images/good.png",   predictedAqi);
        } else if (predictedAqi <= 150) {
            applyStyles("MODERATE",  Color.web("#F1C40F"), "/images/moderate.png",  predictedAqi);
        } else if (predictedAqi <= 200) {
            applyStyles("POOR",      Color.web("#E67E22"), "/images/poor.png",      predictedAqi);
        } else if (predictedAqi <= 300) {
            applyStyles("VERY POOR", Color.web("#8E44AD"), "/images/severe.png",    predictedAqi);
        } else {
            applyStyles("SEVERE",    Color.web("#C0392B"), "/images/hazardous.png", predictedAqi);
        }
    }

    private void applyStyles(String status, Color alertColor, String imagePath, int aqi) {
        aqiStatus.setText(status);
        aqiStatus.setTextFill(alertColor);
        aqiLabel.setTextFill(alertColor);
        aqiCircle.setBorder(new Border(new BorderStroke(alertColor,
                BorderStrokeStyle.SOLID, new CornerRadii(120), new BorderWidths(8))));

        try {
            var stream = getClass().getResourceAsStream(imagePath);
            if (stream != null) aqiImage.setImage(new Image(stream));
            else aqiImage.setImage(null);
        } catch (Exception e) {
            aqiImage.setImage(null);
        }
    }

    private void showError(String msg) {
        cityLabel.setText("Error");
        aqiStatus.setText(msg);
        aqiStatus.setTextFill(Color.web("#E74C3C"));
    }

    /* ── Autocomplete ──────────────────────────────────────────── */
    private void setupAutoComplete() {
        ContextMenu suggestionsPopup = new ContextMenu();

        cityField.textProperty().addListener((obs, oldVal, newVal) -> {
            suggestionsPopup.getItems().clear();
            if (newVal == null || newVal.length() < 2) { suggestionsPopup.hide(); return; }

            new Thread(() -> {
                try {
                    String encoded = URLEncoder.encode(newVal, StandardCharsets.UTF_8);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(BACKEND + "/search?q=" + encoded))
                            .GET().build();
                    HttpResponse<String> res = httpClient.send(
                            req, HttpResponse.BodyHandlers.ofString());

                    JsonNode results = objectMapper.readTree(res.body());
                    Platform.runLater(() -> {
                        suggestionsPopup.getItems().clear();
                        if (!results.isArray() || results.size() == 0) { suggestionsPopup.hide(); return; }
                        for (JsonNode r : results) {
                            String display = r.path("display").asText();
                            MenuItem item = new MenuItem(display);
                            item.setOnAction(e -> {
                                cityField.setText(r.path("city").asText(display));
                                suggestionsPopup.hide();
                            });
                            suggestionsPopup.getItems().add(item);
                        }
                        if (!suggestionsPopup.isShowing()
                                && cityField.getScene() != null
                                && cityField.getScene().getWindow() != null) {
                            suggestionsPopup.show(cityField, Side.BOTTOM, 0, 0);
                        }
                    });
                } catch (Exception e) {
                    // fallback: no suggestions
                }
            }).start();
        });

        cityField.focusedProperty().addListener((obs, o, n) -> { if (!n) suggestionsPopup.hide(); });
    }

    /* ── Programmatic UI Styling ───────────────────────────────── */
    private void buildProgrammaticUI() {
        rootBox.setBackground(new Background(new BackgroundFill(
                Color.web("#F0F4F8"), CornerRadii.EMPTY, Insets.EMPTY)));
        topInputArea.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(12), Insets.EMPTY)));
        topInputArea.setEffect(new DropShadow(10, Color.color(0,0,0,0.05)));
        topInputArea.setPadding(new Insets(25));

        headerLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        headerLabel.setTextFill(Color.web("#2C3E50"));

        symptomsTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        symptomsTitle.setTextFill(Color.web("#7F8C8D"));

        Color checkboxColor = Color.web("#34495E");
        symptomBreath.setTextFill(checkboxColor);
        symptomCough.setTextFill(checkboxColor);
        symptomChest.setTextFill(checkboxColor);
        symptomIrritation.setTextFill(checkboxColor);
        symptomFatigue.setTextFill(checkboxColor);

        cityField.setStyle("-fx-background-color: #F8F9FA; -fx-border-color: #D1D5DB; " +
                           "-fx-border-radius: 4; -fx-padding: 8;");

        predictButton.setBackground(new Background(new BackgroundFill(
                Color.web("#3498DB"), new CornerRadii(6), Insets.EMPTY)));
        predictButton.setTextFill(Color.WHITE);
        predictButton.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        predictButton.setPadding(new Insets(12));
        predictButton.setOnMouseEntered(e -> predictButton.setBackground(new Background(
                new BackgroundFill(Color.web("#2980B9"), new CornerRadii(6), Insets.EMPTY))));
        predictButton.setOnMouseExited(e -> predictButton.setBackground(new Background(
                new BackgroundFill(Color.web("#3498DB"), new CornerRadii(6), Insets.EMPTY))));

        aqiCard.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(12), Insets.EMPTY)));
        aqiCard.setEffect(new DropShadow(10, Color.color(0,0,0,0.05)));
        aqiCard.setPadding(new Insets(30));

        cityLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 36));
        cityLabel.setTextFill(Color.web("#2C3E50"));

        aqiLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 72));
        aqiLabel.setTextFill(Color.web("#BDC3C7"));

        aqiStatus.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        aqiStatus.setTextFill(Color.web("#7F8C8D"));

        pm25Label.setFont(Font.font("Segoe UI", 16));
        pm25Label.setTextFill(Color.web("#7F8C8D"));
        pm10Label.setFont(Font.font("Segoe UI", 16));
        pm10Label.setTextFill(Color.web("#7F8C8D"));

        aqiCircle.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(120), Insets.EMPTY)));
        aqiCircle.setBorder(new Border(new BorderStroke(
                Color.web("#E0E0E0"), BorderStrokeStyle.SOLID,
                new CornerRadii(120), new BorderWidths(8))));
        aqiCircle.setEffect(new DropShadow(15, Color.color(0,0,0,0.08)));

        adviceCard.setBackground(new Background(new BackgroundFill(
                Color.web("#2C3E50"), new CornerRadii(12), Insets.EMPTY)));
        adviceCard.setEffect(new DropShadow(15, Color.color(0,0,0,0.1)));
        adviceCard.setPadding(new Insets(30));

        adviceTitle.setFont(Font.font("Georgia", FontWeight.BOLD, 20));
        adviceTitle.setTextFill(Color.web("#3498DB"));

        adviceText.setFont(Font.font("Verdana", FontWeight.NORMAL, 14));
        adviceText.setTextFill(Color.WHITE);
        adviceText.setTextAlignment(TextAlignment.LEFT);
    }
}
