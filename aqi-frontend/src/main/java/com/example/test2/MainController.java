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
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
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

    private static final String BACKEND   = "http://localhost:8080/api";
    private static final String ML_SERVER = "http://localhost:5000";

    @FXML private VBox      rootBox;
    @FXML private HBox      topInputArea;
    @FXML private HBox      mainArea;
    @FXML private Label     headerLabel;
    @FXML private TextField cityField;
    @FXML private ComboBox<String> modelSelector;
    @FXML private Button    predictButton;
    @FXML private Label     symptomsTitle;
    @FXML private CheckBox  symptomBreath;
    @FXML private CheckBox  symptomCough;
    @FXML private CheckBox  symptomChest;
    @FXML private CheckBox  symptomIrritation;
    @FXML private CheckBox  symptomFatigue;
    @FXML private VBox      aqiCard;
    @FXML private Label     cityLabel;
    @FXML private StackPane aqiCircle;
    @FXML private Label     aqiLabel;
    @FXML private ImageView aqiImage;
    @FXML private Label     aqiStatus;
    @FXML private Label     modelUsedLabel;
    @FXML private Label     pm25Label;
    @FXML private Label     pm10Label;
    @FXML private VBox      adviceCard;
    @FXML private Label     adviceTitle;
    @FXML private Label     adviceText;

    // ── NEW: risk score display labels injected programmatically ──
    private Label riskScoreValueLabel;
    private Label riskScoreTitleLabel;
    private Label riskLevelLabel;
    private StackPane riskScoreCircle;

    private final HttpClient   httpClient   = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegerProperty currentAqiValue  = new SimpleIntegerProperty(0);
    private final IntegerProperty currentRiskValue = new SimpleIntegerProperty(0);
    private Timeline aqiTimeline;
    private Timeline riskTimeline;

    @FXML
    public void initialize() {
        buildProgrammaticUI();
        aqiLabel.textProperty().bind(currentAqiValue.asString());
        modelSelector.getItems().addAll("XGBoost", "Random Forest", "LightGBM");
        modelSelector.getSelectionModel().selectFirst();
        setupAutoComplete();
        Platform.runLater(this::wrapInScrollPane);
    }

    private void wrapInScrollPane() {
        Scene scene = rootBox.getScene();
        if (scene == null) return;
        ScrollPane scrollPane = new ScrollPane(rootBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: #F0F4F8; -fx-background: #F0F4F8;");
        scrollPane.skinProperty().addListener((obs, o, n) -> {
            if (n != null) scrollPane.lookup(".viewport")
                                     .setStyle("-fx-background-color: #F0F4F8;");
        });
        scene.setRoot(scrollPane);
    }

    @FXML
    private void handleBackToDashboard() {
        SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard");
    }

    private String getSelectedModel() {
        String selected = modelSelector.getValue();
        if (selected == null) return "xgboost";
        return switch (selected) {
            case "Random Forest" -> "randomforest";
            case "LightGBM"      -> "lightgbm";
            default              -> "xgboost";
        };
    }

    @FXML
    private void searchCityAQI() {
        String city = cityField.getText().trim();
        if (city.isEmpty()) { showError("Please enter a city name."); return; }

        predictButton.setDisable(true);
        predictButton.setText("Predicting...");
        cityLabel.setText("Loading...");
        aqiStatus.setText("...");
        modelUsedLabel.setText("");

        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(city, StandardCharsets.UTF_8);
                HttpRequest aqiReq = HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND + "/aqi?city=" + encoded))
                        .GET().build();
                HttpResponse<String> aqiRes = httpClient.send(
                        aqiReq, HttpResponse.BodyHandlers.ofString());

                if (aqiRes.statusCode() != 200) {
                    Platform.runLater(() -> showError("City not found: " + city));
                    resetButton();
                    return;
                }

                JsonNode aqiData     = objectMapper.readTree(aqiRes.body());
                int    currentAqi    = aqiData.path("aqi").asInt();
                double pm25          = aqiData.path("pm25").asDouble();
                double pm10          = aqiData.path("pm10").asDouble();
                double no2           = aqiData.path("no2").asDouble();
                double o3            = aqiData.path("o3").asDouble();
                double co            = aqiData.path("co").asDouble();
                double so2           = aqiData.path("so2").asDouble();
                double temp          = aqiData.path("temperature").asDouble();
                double humidity      = aqiData.path("humidity").asDouble();
                double wind          = aqiData.path("windSpeed").asDouble();
                double lat           = aqiData.path("lat").asDouble(10.0);
                double lon           = aqiData.path("lon").asDouble(76.0);
                double windDir       = aqiData.path("windDirection").asDouble(180.0);
                String cityName      = aqiData.path("city").asText(city);

                int[] result      = callMLServer(getSelectedModel(), currentAqi,
                        pm25, pm10, no2, o3, co, so2, temp, humidity, wind, windDir, lat, lon);
                int predictedAqi  = result[0];
                boolean usedML    = result[1] == 1;

                JsonNode profile  = fetchHealthProfile();

                // ── Compute risk score BEFORE building advice ──
                int riskScore     = computeRiskScore(predictedAqi, profile);
                String advice     = buildAdviceFromRisk(predictedAqi, riskScore, profile);

                final String modelLabel = usedML
                        ? "Predicted by: " + modelSelector.getValue()
                        : "Predicted by: Fallback formula (ML server unavailable)";

                Platform.runLater(() -> {
                    updateUI(cityName, predictedAqi, riskScore, pm25, pm10, advice, modelLabel);
                    resetButton();
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> { showError("Error: " + e.getMessage()); resetButton(); });
            }
        }).start();
    }

    private void resetButton() {
        predictButton.setDisable(false);
        predictButton.setText("PREDICT NOW");
    }

    // ── Compute risk score (extracted so UI can use it) ──────────
    private int computeRiskScore(int predictedAqi, JsonNode profile) {
        int score = 0;
        if      (predictedAqi <= 50)  score += 5;
        else if (predictedAqi <= 100) score += 15;
        else if (predictedAqi <= 150) score += 25;
        else if (predictedAqi <= 200) score += 35;
        else if (predictedAqi <= 300) score += 45;
        else                          score += 50;

        if (profile != null) {
            int age = profile.path("age").asInt(0);
            if (age > 60 || age < 12)                         score += 10;
            else if (age > 45)                                score += 5;
            if (profile.path("is_smoker").asBoolean(false))   score += 10;
            if (profile.path("is_allergic").asBoolean(false)) score += 5;
            if (profile.path("is_pregnant").asBoolean(false)) score += 8;
            JsonNode cond = profile.path("breathing_conditions");
            if (cond.isArray() && cond.size() > 0)            score += 10;
            String asthma = profile.path("asthma_breathing").asText("None");
            if (!asthma.equals("None") && !asthma.isEmpty())  score += 8;
        }

        if (symptomBreath.isSelected())     score += 8;
        if (symptomCough.isSelected())      score += 4;
        if (symptomChest.isSelected())      score += 8;
        if (symptomIrritation.isSelected()) score += 3;
        if (symptomFatigue.isSelected())    score += 4;

        return Math.min(score, 100);
    }

    // ── Color + image + label driven by RISK SCORE ───────────────
    private Color riskColor(int riskScore) {
        if      (riskScore <= 20) return Color.web("#2ECC71"); // green  — LOW
        else if (riskScore <= 40) return Color.web("#A8D700"); // lime   — MODERATE
        else if (riskScore <= 60) return Color.web("#F1C40F"); // yellow — HIGH
        else if (riskScore <= 80) return Color.web("#E67E22"); // orange — VERY HIGH
        else                      return Color.web("#C0392B"); // red    — CRITICAL
    }

    private String riskImage(int riskScore) {
        if      (riskScore <= 20) return "/images/good.png";
        else if (riskScore <= 40) return "/images/good.png";
        else if (riskScore <= 60) return "/images/moderate.png";
        else if (riskScore <= 80) return "/images/poor.png";
        else                      return "/images/hazardous.png";
    }

    private String riskLevelText(int riskScore) {
        if      (riskScore <= 20) return "LOW RISK";
        else if (riskScore <= 40) return "MODERATE RISK";
        else if (riskScore <= 60) return "HIGH RISK";
        else if (riskScore <= 80) return "VERY HIGH RISK";
        else                      return "CRITICAL RISK";
    }

    /* ── UI Update ─────────────────────────────────────────────── */
    private void updateUI(String city, int predictedAqi, int riskScore,
                          double pm25, double pm10, String advice, String modelLabel) {
        cityLabel.setText(city.toUpperCase());
        pm25Label.setText("PM2.5: " + String.format("%.1f", pm25) + " µg/m³");
        pm10Label.setText("PM10: "  + String.format("%.1f", pm10) + " µg/m³");
        adviceText.setText(advice);
        modelUsedLabel.setText(modelLabel);

        // ── Animate AQI counter ──
        if (aqiTimeline != null) aqiTimeline.stop();
        currentAqiValue.set(0);
        aqiTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1.2), new KeyValue(currentAqiValue, predictedAqi)));
        aqiTimeline.play();

        // ── Animate risk score counter ──
        if (riskTimeline != null) riskTimeline.stop();
        currentRiskValue.set(0);
        riskTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1.2), new KeyValue(currentRiskValue, riskScore)));
        riskTimeline.play();

        Color rColor = riskColor(riskScore);

        // ── AQI circle: still uses AQI-based color for the AQI reading ──
        Color aqiColor;
        String aqiStatusText;
        if      (predictedAqi <= 50)  { aqiColor = Color.web("#2ECC71"); aqiStatusText = "GOOD"; }
        else if (predictedAqi <= 100) { aqiColor = Color.web("#A8D700"); aqiStatusText = "SATISFACTORY"; }
        else if (predictedAqi <= 150) { aqiColor = Color.web("#F1C40F"); aqiStatusText = "MODERATE"; }
        else if (predictedAqi <= 200) { aqiColor = Color.web("#E67E22"); aqiStatusText = "POOR"; }
        else if (predictedAqi <= 300) { aqiColor = Color.web("#8E44AD"); aqiStatusText = "VERY POOR"; }
        else                          { aqiColor = Color.web("#C0392B"); aqiStatusText = "SEVERE"; }

        aqiLabel.setTextFill(aqiColor);
        aqiStatus.setText(aqiStatusText);
        aqiStatus.setTextFill(aqiColor);
        aqiCircle.setBorder(new Border(new BorderStroke(aqiColor,
                BorderStrokeStyle.SOLID, new CornerRadii(120), new BorderWidths(8))));

        // ── Risk circle: driven by RISK SCORE ──
        riskScoreValueLabel.setTextFill(rColor);
        riskLevelLabel.setText(riskLevelText(riskScore));
        riskLevelLabel.setTextFill(rColor);
        riskScoreCircle.setBorder(new Border(new BorderStroke(rColor,
                BorderStrokeStyle.SOLID, new CornerRadii(100), new BorderWidths(6))));

        // ── Person image: driven by RISK SCORE ──
        try {
            var stream = getClass().getResourceAsStream(riskImage(riskScore));
            if (stream != null) aqiImage.setImage(new Image(stream));
            else aqiImage.setImage(null);
        } catch (Exception e) { aqiImage.setImage(null); }
    }

    /* ── ML Server ─────────────────────────────────────────────── */
    private int[] callMLServer(String modelName, int currentAqi,
                               double pm25, double pm10, double no2, double o3,
                               double co, double so2, double temp, double humidity,
                               double wind, double windDirection, double lat, double lon) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> payload = new HashMap<>();
            payload.put("model",            modelName);
            payload.put("current_aqi",      currentAqi);
            payload.put("lat",              lat);
            payload.put("lon",              lon);
            payload.put("pm25",             pm25);
            payload.put("pm10",             pm10);
            payload.put("no2",              no2);
            payload.put("o3",               o3);
            payload.put("co",               co);
            payload.put("so2",              so2);
            payload.put("temperature",      temp);
            payload.put("relativehumidity", humidity);
            payload.put("wind_speed",       wind);
            payload.put("wind_direction",   windDirection);
            payload.put("aqi_lag_1",        currentAqi);
            payload.put("aqi_lag_2",        currentAqi);
            payload.put("hour",             now.getHour());
            payload.put("day_of_week",      now.getDayOfWeek().getValue() - 1);
            payload.put("month",            now.getMonthValue());

            String json = objectMapper.writeValueAsString(payload);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ML_SERVER + "/predict"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                JsonNode result = objectMapper.readTree(res.body());
                return new int[]{result.path("predicted_aqi").asInt(currentAqi), 1};
            }
        } catch (Exception e) {
            System.out.println("ML fallback: " + e.getMessage());
        }
        return new int[]{fallbackPredict(currentAqi, pm25, pm10), 0};
    }

    private int fallbackPredict(int currentAqi, double pm25, double pm10) {
        int p = currentAqi;
        if (pm25 > 150) p += 20; else if (pm25 > 80) p += 10;
        if (pm10 > 200) p += 15; else if (pm10 > 100) p += 5;
        return Math.min(p, 500);
    }

    /* ── Health Profile ────────────────────────────────────────── */
    private JsonNode fetchHealthProfile() {
        try {
            String userId = UserSession.getUserId();
            if (userId == null || userId.isEmpty()) return null;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND + "/health-profile/" + userId))
                    .GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) return objectMapper.readTree(res.body());
        } catch (Exception e) {
            System.out.println("Could not fetch health profile: " + e.getMessage());
        }
        return null;
    }

    /* ── Build advice text (risk score already computed) ────────── */
    private String buildAdviceFromRisk(int predictedAqi, int riskScore, JsonNode profile) {
        String riskLevel;
        if      (riskScore <= 20) riskLevel = "LOW";
        else if (riskScore <= 40) riskLevel = "MODERATE";
        else if (riskScore <= 60) riskLevel = "HIGH";
        else if (riskScore <= 80) riskLevel = "VERY HIGH";
        else                      riskLevel = "CRITICAL";

        StringBuilder advice = new StringBuilder();
        advice.append("PREDICTED AQI (Next Hour):\n");
        if      (predictedAqi <= 50)  advice.append("Good — Air quality is satisfactory.\n\n");
        else if (predictedAqi <= 100) advice.append("Satisfactory — Acceptable for most people.\n\n");
        else if (predictedAqi <= 150) advice.append("Moderate — Sensitive groups may be affected.\n\n");
        else if (predictedAqi <= 200) advice.append("Poor — Everyone may experience health effects.\n\n");
        else if (predictedAqi <= 300) advice.append("Very Poor — Avoid outdoor activity.\n\n");
        else                          advice.append("Severe — Emergency conditions. Stay indoors.\n\n");

        if (profile != null) {
            advice.append("PERSONAL RISK FACTORS:\n");
            int age = profile.path("age").asInt(0);
            if (age > 60) advice.append("• Senior citizen — higher respiratory sensitivity.\n");
            if (age < 12) advice.append("• Child — extra caution needed.\n");
            if (profile.path("is_smoker").asBoolean(false))
                advice.append("• Smoker — significantly elevated risk.\n");
            if (profile.path("is_allergic").asBoolean(false))
                advice.append("• Allergic — carry antihistamines, wear N95.\n");
            if (profile.path("is_pregnant").asBoolean(false))
                advice.append("• Pregnant — limit outdoor exposure.\n");
            String asthma = profile.path("asthma_breathing").asText("None");
            if (!asthma.equals("None") && !asthma.isEmpty())
                advice.append("• Breathing condition — keep inhaler ready.\n");
            advice.append("\n");
        }

        boolean anySymptom = symptomBreath.isSelected() || symptomCough.isSelected() ||
                symptomChest.isSelected() || symptomIrritation.isSelected() ||
                symptomFatigue.isSelected();
        if (anySymptom) {
            advice.append("SYMPTOM MANAGEMENT:\n");
            if (symptomBreath.isSelected())
                advice.append(predictedAqi > 150 ? "• Breathlessness: Use inhaler. Seek help if severe.\n"
                        : "• Breathlessness: Reduce exertion.\n");
            if (symptomCough.isSelected())
                advice.append(predictedAqi > 150 ? "• Cough: Run HEPA purifier.\n"
                        : "• Cough: Wear N95 mask outdoors.\n");
            if (symptomChest.isSelected())
                advice.append(predictedAqi > 150 ? "• Chest pain: Consult a doctor immediately.\n"
                        : "• Chest tightness: Rest, avoid exertion.\n");
            if (symptomIrritation.isSelected())
                advice.append("• Eye/throat irritation: Wash face, use eye drops.\n");
            if (symptomFatigue.isSelected())
                advice.append("• Fatigue: Stay hydrated, rest indoors.\n");
            advice.append("\n");
        }

        if (predictedAqi > 100) {
            advice.append("PRECAUTIONS:\n");
            advice.append("• Keep windows closed.\n");
            advice.append("• Use air purifier if available.\n");
            advice.append("• Wear N95 mask outdoors.\n");
            advice.append("• Avoid morning outdoor exercise.\n");
            if (predictedAqi > 200)
                advice.append("• EMERGENCY: Minimize all outdoor activity.\n");
        }
        return advice.toString();
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
                    HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
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
                } catch (Exception e) { /* no suggestions */ }
            }).start();
        });
        cityField.focusedProperty().addListener((obs, o, n) -> { if (!n) suggestionsPopup.hide(); });
    }

    /* ── Programmatic UI Styling ───────────────────────────────── */
    private void buildProgrammaticUI() {
        rootBox.setBackground(new Background(new BackgroundFill(
                Color.web("#F0F4F8"), CornerRadii.EMPTY, Insets.EMPTY)));

        // ── Top input card ──
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
        modelSelector.setStyle("-fx-background-color: #F8F9FA; -fx-border-color: #D1D5DB; " +
                "-fx-border-radius: 4;");

        predictButton.setBackground(new Background(new BackgroundFill(
                Color.web("#3498DB"), new CornerRadii(6), Insets.EMPTY)));
        predictButton.setTextFill(Color.WHITE);
        predictButton.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        predictButton.setPadding(new Insets(12));
        predictButton.setOnMouseEntered(e -> predictButton.setBackground(new Background(
                new BackgroundFill(Color.web("#2980B9"), new CornerRadii(6), Insets.EMPTY))));
        predictButton.setOnMouseExited(e -> predictButton.setBackground(new Background(
                new BackgroundFill(Color.web("#3498DB"), new CornerRadii(6), Insets.EMPTY))));

        // ── AQI card ──
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

        // ── BUILD RISK SCORE CIRCLE and inject into aqiCard ──
        buildRiskScoreWidget();

        // ── Advice card ──
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

    /**
     * Builds the risk score circle widget and inserts it into aqiCard
     * right after the AQI circle, side by side.
     *
     * Layout added to aqiCard:
     *   HBox [ AQI circle | spacer | Risk circle ]
     *   HBox [ AQI status label  | Risk level label ]
     */
    private void buildRiskScoreWidget() {
        // ── Risk score circle ──
        riskScoreCircle = new StackPane();
        riskScoreCircle.setPrefSize(180, 180);
        riskScoreCircle.setMaxSize(180, 180);
        riskScoreCircle.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(90), Insets.EMPTY)));
        riskScoreCircle.setBorder(new Border(new BorderStroke(
                Color.web("#E0E0E0"), BorderStrokeStyle.SOLID,
                new CornerRadii(90), new BorderWidths(6))));
        riskScoreCircle.setEffect(new DropShadow(15, Color.color(0,0,0,0.08)));

        VBox riskInner = new VBox(0);
        riskInner.setAlignment(Pos.CENTER);

        riskScoreTitleLabel = new Label("RISK");
        riskScoreTitleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        riskScoreTitleLabel.setTextFill(Color.web("#8e9bb0"));

        riskScoreValueLabel = new Label("0");
        riskScoreValueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 52));
        riskScoreValueLabel.setTextFill(Color.web("#BDC3C7"));
        currentRiskValue.addListener((obs, o, n) ->
                riskScoreValueLabel.setText(String.valueOf(n.intValue())));

        Label riskOutOf = new Label("/100");
        riskOutOf.setFont(Font.font("Segoe UI", 13));
        riskOutOf.setTextFill(Color.web("#8e9bb0"));

        riskInner.getChildren().addAll(riskScoreTitleLabel, riskScoreValueLabel, riskOutOf);
        riskScoreCircle.getChildren().add(riskInner);

        riskLevelLabel = new Label("AWAITING");
        riskLevelLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        riskLevelLabel.setTextFill(Color.web("#7F8C8D"));

        // ── Find where aqiCircle is in aqiCard and insert new layout ──
        // Remove aqiCircle and aqiStatus from their current positions,
        // wrap them with the risk widgets side by side.
        aqiCard.getChildren().remove(aqiCircle);
        aqiCard.getChildren().remove(aqiStatus);

        // Side-by-side: [AQI circle]  [Risk circle]
        HBox circlesRow = new HBox(40);
        circlesRow.setAlignment(Pos.CENTER);

        VBox aqiCircleBox = new VBox(8);
        aqiCircleBox.setAlignment(Pos.CENTER);
        Label aqiCircleTitle = new Label("PREDICTED AQI");
        aqiCircleTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        aqiCircleTitle.setTextFill(Color.web("#8e9bb0"));
        aqiCircleBox.getChildren().addAll(aqiCircleTitle, aqiCircle, aqiStatus);

        VBox riskCircleBox = new VBox(8);
        riskCircleBox.setAlignment(Pos.CENTER);
        Label riskCircleTitle = new Label("HEALTH RISK");
        riskCircleTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        riskCircleTitle.setTextFill(Color.web("#8e9bb0"));
        riskCircleBox.getChildren().addAll(riskCircleTitle, riskScoreCircle, riskLevelLabel);

        circlesRow.getChildren().addAll(aqiCircleBox, riskCircleBox);

        // Insert the circles row after cityLabel (index 1)
        int insertIdx = aqiCard.getChildren().indexOf(cityLabel) + 1;
        aqiCard.getChildren().add(insertIdx, circlesRow);
    }
}
