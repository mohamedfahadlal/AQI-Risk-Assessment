package com.example.test2;

import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
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

    // ── FXML ──────────────────────────────────────────────────────
    @FXML private VBox           rootBox;
    @FXML private HBox           topInputArea;
    @FXML private HBox           mainArea;
    @FXML private Label          headerLabel;
    @FXML private TextField      cityField;
    @FXML private ComboBox<String> modelSelector;
    @FXML private Button         predictButton;
    @FXML private Label          symptomsTitle;
    @FXML private CheckBox       symptomBreath;
    @FXML private CheckBox       symptomCough;
    @FXML private CheckBox       symptomChest;
    @FXML private CheckBox       symptomIrritation;
    @FXML private CheckBox       symptomFatigue;
    @FXML private VBox           aqiCard;
    @FXML private Label          cityLabel;
    @FXML private StackPane      aqiCircle;
    @FXML private Label          aqiLabel;
    @FXML private ImageView      aqiImage;
    @FXML private Label          aqiStatus;
    @FXML private Label          modelUsedLabel;
    @FXML private Label          pm25Label;
    @FXML private Label          pm10Label;
    @FXML private VBox           adviceCard;
    @FXML private Label          adviceTitle;
    @FXML private Label          adviceText;

    // ── Dynamic widgets ───────────────────────────────────────────
    private Label  predictedAqiLabel;
    private Label  riskSubLabel;
    private Label  riskOutOf;
    private Arc    progressArc;
    private Button backBtn;          // elevated back button (injected into rootBox)

    // Symptom tile containers (for active-state toggling)
    private VBox tileBreath, tileCough, tileChest, tileIrritation, tileFatigue;

    // Direct reference to the VBox that holds advisory content (avoids getParent() cast crash)
    private VBox adviceContentArea;

    private final HttpClient   http   = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final IntegerProperty currentRiskValue = new SimpleIntegerProperty(0);
    private Timeline riskTimeline;

    // ═══════════════════════════════════════════════════════════════
    @FXML
    public void initialize() {
        buildUI();
        // Bind animated counter to label
        currentRiskValue.addListener((obs, o, n) -> {
            aqiLabel.setText(String.valueOf(n.intValue()));
            double pct = n.intValue() / 100.0;
            progressArc.setLength(-(270 * pct));
        });
        modelSelector.getItems().addAll("XGBoost", "Random Forest", "LightGBM");
        modelSelector.getSelectionModel().selectFirst();
        setupAutoComplete();
        Platform.runLater(this::wrapInScrollPane);
    }

    private void wrapInScrollPane() {
        Scene scene = rootBox.getScene();
        if (scene == null) return;
        ScrollPane sp = new ScrollPane(rootBox);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setStyle("-fx-background-color: #F0F4F8; -fx-background: #F0F4F8;");
        sp.skinProperty().addListener((obs, o, n) -> {
            if (n != null) sp.lookup(".viewport").setStyle("-fx-background-color: #F0F4F8;");
        });
        scene.setRoot(sp);
    }

    @FXML
    private void handleBackToDashboard() {
        SceneManager.switchScene("/com/example/aqidashboard/dashboard-view.fxml", "Dashboard");
    }

    private String getSelectedModel() {
        String s = modelSelector.getValue();
        if (s == null) return "xgboost";
        return switch (s) {
            case "Random Forest" -> "randomforest";
            case "LightGBM"      -> "lightgbm";
            default              -> "xgboost";
        };
    }

    // ── Predict ───────────────────────────────────────────────────
    @FXML
    private void searchCityAQI() {
        String city = cityField.getText().trim();
        if (city.isEmpty()) { showError("Please enter a city name."); return; }
        predictButton.setDisable(true);
        predictButton.setText("Predicting...");
        cityLabel.setText("Loading...");
        aqiStatus.setText("");

        new Thread(() -> {
            try {
                String enc = URLEncoder.encode(city, StandardCharsets.UTF_8);
                HttpResponse<String> r = http.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(BACKEND + "/aqi?city=" + enc)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) {
                    Platform.runLater(() -> showError("City not found: " + city));
                    resetButton(); return;
                }
                JsonNode d      = mapper.readTree(r.body());
                int  curAqi     = d.path("aqi").asInt();
                double pm25     = d.path("pm25").asDouble();
                double pm10     = d.path("pm10").asDouble();
                double no2      = d.path("no2").asDouble();
                double o3       = d.path("o3").asDouble();
                double co       = d.path("co").asDouble();
                double so2      = d.path("so2").asDouble();
                double temp     = d.path("temperature").asDouble();
                double hum      = d.path("humidity").asDouble();
                double wind     = d.path("windSpeed").asDouble();
                double lat      = d.path("lat").asDouble(10.0);
                double lon      = d.path("lon").asDouble(76.0);
                double windDir  = d.path("windDirection").asDouble(180.0);
                String cityName = d.path("city").asText(city);

                int[] ml       = callML(getSelectedModel(), curAqi,
                        pm25, pm10, no2, o3, co, so2, temp, hum, wind, windDir, lat, lon);
                int predAqi    = ml[0];
                boolean usedML = ml[1] == 1;

                JsonNode profile = fetchProfile();
                int riskScore    = computeRiskScore(predAqi, profile);
                String advice    = buildAdvice(predAqi, riskScore, profile);
                String modelLbl  = usedML ? modelSelector.getValue() : "Fallback formula";

                Platform.runLater(() -> {
                    updateUI(cityName, predAqi, riskScore, pm25, pm10, advice, modelLbl);
                    resetButton();
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> { showError(e.getMessage()); resetButton(); });
            }
        }).start();
    }

    private void resetButton() {
        predictButton.setDisable(false);
        predictButton.setText("PREDICT NOW");
    }

    // ── Risk score (WHO/CPCB accurate) ───────────────────────────
    private int computeRiskScore(int aqi, JsonNode profile) {
        double base;
        if      (aqi <= 50)  base = 5  + (aqi / 50.0) * 5;
        else if (aqi <= 100) base = 10 + ((aqi - 50)  / 50.0) * 15;
        else if (aqi <= 150) base = 25 + ((aqi - 100) / 50.0) * 20;
        else if (aqi <= 200) base = 45 + ((aqi - 150) / 50.0) * 15;
        else if (aqi <= 300) base = 60 + ((aqi - 200) / 100.0) * 20;
        else                 base = 80 + ((aqi - 300) / 200.0) * 20;

        double m = 1.0;
        if (profile != null) {
            int age = profile.path("age").asInt(30);
            if      (age < 5)  m += 0.40;
            else if (age < 12) m += 0.25;
            else if (age > 70) m += 0.35;
            else if (age > 60) m += 0.20;
            else if (age > 45) m += 0.08;
            if (profile.path("is_smoker").asBoolean(false))   m += 0.30;
            if (profile.path("is_allergic").asBoolean(false)) m += 0.15;
            if (profile.path("is_pregnant").asBoolean(false)) m += 0.20;
            JsonNode cond = profile.path("breathing_conditions");
            if (cond.isArray() && cond.size() > 0)            m += 0.25;
            String asthma = profile.path("asthma_breathing").asText("None");
            if (!asthma.equals("None") && !asthma.isEmpty())  m += 0.20;
            m = Math.min(m, 2.5);
        }
        double score = base * m;
        double af = Math.min(aqi / 100.0, 3.0);
        if (symptomBreath.isSelected())     score += 8 * af;
        if (symptomChest.isSelected())      score += 8 * af;
        if (symptomCough.isSelected())      score += 4 * af;
        if (symptomFatigue.isSelected())    score += 3 * af;
        if (symptomIrritation.isSelected()) score += 2 * af;
        return (int) Math.min(Math.round(score), 100);
    }

    // ── Palette helpers ───────────────────────────────────────────
    private Color riskColor(int r) {
        if      (r <= 20) return Color.web("#16A34A");
        else if (r <= 40) return Color.web("#D97706");
        else if (r <= 60) return Color.web("#EA580C");
        else if (r <= 80) return Color.web("#7C3AED");
        else              return Color.web("#DC2626");
    }

    private String riskGradient(int r) {
        if      (r <= 20) return "linear-gradient(to bottom, #DCFCE7 0%, #FFFFFF 65%)";
        else if (r <= 40) return "linear-gradient(to bottom, #FEF3C7 0%, #FFFFFF 65%)";
        else if (r <= 60) return "linear-gradient(to bottom, #FFEDD5 0%, #FFFFFF 65%)";
        else if (r <= 80) return "linear-gradient(to bottom, #EDE9FE 0%, #FFFFFF 65%)";
        else              return "linear-gradient(to bottom, #FEE2E2 0%, #FFFFFF 65%)";
    }

    private String riskLevelText(int r) {
        if      (r <= 20) return "LOW RISK";
        else if (r <= 40) return "MODERATE RISK";
        else if (r <= 60) return "HIGH RISK";
        else if (r <= 80) return "VERY HIGH RISK";
        else              return "CRITICAL RISK";
    }

    private String aqiText(int aqi) {
        if      (aqi <= 50)  return "Good";
        else if (aqi <= 100) return "Satisfactory";
        else if (aqi <= 150) return "Moderate";
        else if (aqi <= 200) return "Poor";
        else if (aqi <= 300) return "Very Poor";
        else                 return "Severe";
    }

    private String riskImage(int r) {
        if      (r <= 20) return "/images/good.png";
        else if (r <= 40) return "/images/moderate.png";
        else if (r <= 60) return "/images/poor.png";
        else if (r <= 80) return "/images/severe.png";
        else              return "/images/hazardous.png";
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }

    // ── Update UI ─────────────────────────────────────────────────
    private void updateUI(String city, int predAqi, int riskScore,
                          double pm25, double pm10, String advice, String modelLbl) {
        Color rc     = riskColor(riskScore);
        String rcHex = toHex(rc);

        // City label
        cityLabel.setText(city.toUpperCase());

        // Card gradient
        aqiCard.setStyle(
                "-fx-background-color: " + riskGradient(riskScore) + ";" +
                        "-fx-background-radius: 18; -fx-border-radius: 18;"
        );
        aqiCard.setEffect(new DropShadow(30, 0, 6,
                Color.color(rc.getRed(), rc.getGreen(), rc.getBlue(), 0.20)));

        // Arc color
        progressArc.setStroke(rc);

        // Risk number color
        aqiLabel.setStyle(
                "-fx-font-size: 82px; -fx-font-weight: 900; -fx-font-family: 'Segoe UI';"
        );
        aqiLabel.setTextFill(rc);

        // Risk level
        aqiStatus.setText(riskLevelText(riskScore));
        aqiStatus.setTextFill(rc);

        // Predicted AQI
        predictedAqiLabel.setText("AQI  " + predAqi + "  —  " + aqiText(predAqi));
        predictedAqiLabel.setStyle(
                "-fx-font-size: 30px; -fx-font-weight: 700; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #1E293B;"
        );

        // Pollutants
        pm25Label.setText("PM2.5   " + String.format("%.1f", pm25) + " µg/m³");
        pm10Label.setText("PM10   "  + String.format("%.1f", pm10) + " µg/m³");

        // Model
        modelUsedLabel.setText("Predicted by   " + modelLbl);

        // Advisory top accent bar + gradient bg
        adviceCard.setStyle(
                "/* gradient metallic bg */" +
                        "-fx-background-color: linear-gradient(to bottom right, #1E293B, #0F172A);" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: " + rcHex + " transparent transparent transparent;" +
                        "-fx-border-width: 4 0 0 0;" +
                        "-fx-border-radius: 18 18 0 0;"
        );
        adviceCard.setEffect(new DropShadow(24, 0, 6,
                Color.color(rc.getRed(), rc.getGreen(), rc.getBlue(), 0.20)));

        // Advisory title styled
        adviceTitle.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #FFFFFF; -fx-letter-spacing: 2.5px;"
        );

        // Build advisory content
        rebuildAdviceContent(predAqi, riskScore, advice, rc, rcHex);

        // Animate counter + arc
        if (riskTimeline != null) riskTimeline.stop();
        currentRiskValue.set(0);
        progressArc.setLength(0);
        riskTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1.4),
                        new KeyValue(currentRiskValue, riskScore)));
        riskTimeline.play();

        // Boy image
        try {
            var s = getClass().getResourceAsStream(riskImage(riskScore));
            if (s != null) aqiImage.setImage(new Image(s));
        } catch (Exception ignored) {}
    }

    /** Replaces the plain adviceText label with rich VBox content */
    private void rebuildAdviceContent(int aqi, int riskScore, String rawAdvice,
                                      Color rc, String rcHex) {
        VBox content = new VBox(10);
        content.setPadding(new Insets(0, 0, 0, 0));

        // Risk badge
        String rl   = riskLevelText(riskScore);
        Label badge = new Label("  " + riskScore + "/100  " + rl + "  ");
        badge.setStyle(
                "-fx-background-color: " + rcHex + ";" +
                        "-fx-background-radius: 6;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';" +
                        "-fx-padding: 4 10 4 10;"
        );
        badge.setEffect(new DropShadow(8, 0, 2,
                Color.color(rc.getRed(), rc.getGreen(), rc.getBlue(), 0.40)));
        HBox badgeRow = new HBox(badge);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(badgeRow);

        // Predicted AQI section
        addAdviceSection(content, "PREDICTED AQI (NEXT HOUR)", new String[][]{
                {aqi <= 100 ? "✔" : aqi <= 200 ? "⚠" : "✖", aqiText(aqi) +
                        (aqi <= 50  ? " — Air quality satisfactory." :
                                aqi <= 100 ? " — Acceptable for most people." :
                                        aqi <= 150 ? " — Sensitive groups may be affected." :
                                                aqi <= 200 ? " — Health effects for everyone." :
                                                        aqi <= 300 ? " — Avoid outdoor activity." :
                                                                " — Emergency. Stay indoors.")}
        }, "#CBD5E1");

        // Precautions if needed
        if (aqi > 100) {
            String[][] prec = {
                    {"✔", "Keep windows and doors closed."},
                    {"✔", "Use air purifier if available."},
                    {"✔", "Wear N95 mask when outdoors."},
                    {"✔", "Avoid morning outdoor exercise."}
            };
            if (aqi > 200) {
                prec = Arrays.copyOf(prec, prec.length + 1);
                prec[prec.length - 1] = new String[]{"✖", "EMERGENCY: Minimize all outdoor exposure."};
            }
            addAdviceSection(content, "PRECAUTIONS", prec, "#94A3B8");
        }

        // Personal risk section if advice mentions it
        if (rawAdvice.contains("PERSONAL RISK")) {
            List<String[]> rows = new ArrayList<>();
            if (rawAdvice.contains("Senior"))   rows.add(new String[]{"⚠", "Senior — higher respiratory sensitivity."});
            if (rawAdvice.contains("Child"))     rows.add(new String[]{"⚠", "Child — extra caution needed."});
            if (rawAdvice.contains("Smoker"))    rows.add(new String[]{"✖", "Smoker — significantly elevated risk."});
            if (rawAdvice.contains("Allergic"))  rows.add(new String[]{"⚠", "Allergic — carry antihistamines + N95."});
            if (rawAdvice.contains("Pregnant"))  rows.add(new String[]{"⚠", "Pregnant — limit outdoor exposure."});
            if (rawAdvice.contains("Breathing")) rows.add(new String[]{"✖", "Breathing condition — keep inhaler ready."});
            if (!rows.isEmpty())
                addAdviceSection(content, "PERSONAL RISK FACTORS", rows.toArray(new String[0][]), "#94A3B8");
        }

        // Use adviceContentArea directly — avoids ScrollPane parent cast crash
        adviceContentArea.getChildren().setAll(content.getChildren());
    }

    private void addAdviceSection(VBox parent, String title, String[][] rows, String textColor) {
        Label sectionTitle = new Label(title);
        sectionTitle.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #475569; -fx-letter-spacing: 1.5px;"
        );
        parent.getChildren().add(sectionTitle);

        for (String[] row : rows) {
            String icon = row[0];
            String text = row[1];
            Color iconColor = icon.equals("✖") ? Color.web("#F87171")
                    : icon.equals("⚠") ? Color.web("#FBBF24")
                    : Color.web("#4ADE80");

            Label iconLbl = new Label(icon);
            iconLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
            iconLbl.setTextFill(iconColor);
            iconLbl.setMinWidth(22);

            Label textLbl = new Label(text);
            textLbl.setStyle(
                    "-fx-font-size: 13px; -fx-font-family: 'Segoe UI'; -fx-text-fill: " + textColor + ";"
            );
            textLbl.setWrapText(true);

            HBox row1 = new HBox(8, iconLbl, textLbl);
            row1.setAlignment(Pos.TOP_LEFT);
            parent.getChildren().add(row1);
        }
        // spacer
        Region spacer = new Region();
        spacer.setPrefHeight(4);
        parent.getChildren().add(spacer);
    }

    private void showError(String msg) {
        cityLabel.setText("—");
        aqiStatus.setText(msg);
        aqiStatus.setTextFill(Color.web("#DC2626"));
    }

    // ── ML ────────────────────────────────────────────────────────
    private int[] callML(String model, int curAqi,
                         double pm25, double pm10, double no2, double o3,
                         double co, double so2, double temp, double hum,
                         double wind, double windDir, double lat, double lon) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> p = new HashMap<>();
            p.put("model", model);      p.put("current_aqi", curAqi);
            p.put("lat", lat);          p.put("lon", lon);
            p.put("pm25", pm25);        p.put("pm10", pm10);
            p.put("no2", no2);          p.put("o3", o3);
            p.put("co", co);            p.put("so2", so2);
            p.put("temperature", temp); p.put("relativehumidity", hum);
            p.put("wind_speed", wind);  p.put("wind_direction", windDir);
            p.put("aqi_lag_1", curAqi); p.put("aqi_lag_2", curAqi);
            p.put("hour", now.getHour());
            p.put("day_of_week", now.getDayOfWeek().getValue() - 1);
            p.put("month", now.getMonthValue());
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder().uri(URI.create(ML_SERVER + "/predict"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(p)))
                            .timeout(java.time.Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200)
                return new int[]{mapper.readTree(res.body()).path("predicted_aqi").asInt(curAqi), 1};
        } catch (Exception e) { System.out.println("ML fallback: " + e.getMessage()); }
        return new int[]{fallback(curAqi, pm25, pm10), 0};
    }

    private int fallback(int a, double pm25, double pm10) {
        int p = a;
        if (pm25 > 150) p += 20; else if (pm25 > 80) p += 10;
        if (pm10 > 200) p += 15; else if (pm10 > 100) p += 5;
        return Math.min(p, 500);
    }

    private JsonNode fetchProfile() {
        try {
            String uid = UserSession.getUserId();
            if (uid == null || uid.isEmpty()) return null;
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(BACKEND + "/health-profile/" + uid)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) return mapper.readTree(res.body());
        } catch (Exception e) { System.out.println("Profile: " + e.getMessage()); }
        return null;
    }

    private String buildAdvice(int aqi, int risk, JsonNode profile) {
        String rl = risk <= 20 ? "LOW" : risk <= 40 ? "MODERATE"
                : risk <= 60 ? "HIGH" : risk <= 80 ? "VERY HIGH" : "CRITICAL";
        StringBuilder sb = new StringBuilder();
        sb.append("RISK SCORE  ").append(risk).append(" / 100  (").append(rl).append(")\n\n");
        if (aqi <= 50)       sb.append("PREDICTED: Good\n");
        else if (aqi <= 100) sb.append("PREDICTED: Satisfactory\n");
        else if (aqi <= 150) sb.append("PREDICTED: Moderate\n");
        else if (aqi <= 200) sb.append("PREDICTED: Poor\n");
        else if (aqi <= 300) sb.append("PREDICTED: Very Poor\n");
        else                 sb.append("PREDICTED: Severe\n");
        if (profile != null) {
            sb.append("\nPERSONAL RISK\n");
            int age = profile.path("age").asInt(0);
            if (age > 60) sb.append("Senior\n");
            if (age < 12) sb.append("Child\n");
            if (profile.path("is_smoker").asBoolean(false))   sb.append("Smoker\n");
            if (profile.path("is_allergic").asBoolean(false)) sb.append("Allergic\n");
            if (profile.path("is_pregnant").asBoolean(false)) sb.append("Pregnant\n");
            String asthma = profile.path("asthma_breathing").asText("None");
            if (!asthma.equals("None") && !asthma.isEmpty())  sb.append("Breathing\n");
        }
        return sb.toString();
    }

    // ── Autocomplete ──────────────────────────────────────────────
    private void setupAutoComplete() {
        ContextMenu popup = new ContextMenu();
        cityField.textProperty().addListener((obs, o, newVal) -> {
            popup.getItems().clear();
            if (newVal == null || newVal.length() < 2) { popup.hide(); return; }
            new Thread(() -> {
                try {
                    String enc = URLEncoder.encode(newVal, StandardCharsets.UTF_8);
                    HttpResponse<String> res = http.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(BACKEND + "/search?q=" + enc)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    JsonNode results = mapper.readTree(res.body());
                    Platform.runLater(() -> {
                        popup.getItems().clear();
                        if (!results.isArray() || results.size() == 0) { popup.hide(); return; }
                        for (JsonNode r : results) {
                            String disp = r.path("display").asText();
                            MenuItem item = new MenuItem(disp);
                            item.setOnAction(e -> {
                                cityField.setText(r.path("city").asText(disp)); popup.hide();
                            });
                            popup.getItems().add(item);
                        }
                        if (!popup.isShowing() && cityField.getScene() != null)
                            popup.show(cityField, Side.BOTTOM, 0, 0);
                    });
                } catch (Exception ignored) {}
            }).start();
        });
        cityField.focusedProperty().addListener((obs, o, n) -> { if (!n) popup.hide(); });
    }

    // ═══════════════════════════════════════════════════════════════
    //  buildUI  –  entire layout built programmatically
    // ═══════════════════════════════════════════════════════════════
    private void buildUI() {

        // Page background
        rootBox.setBackground(new Background(new BackgroundFill(
                Color.web("#F0F4F8"), CornerRadii.EMPTY, Insets.EMPTY)));
        rootBox.setSpacing(20);
        rootBox.setPadding(new Insets(24, 28, 28, 28));

        // ── ELEVATED BACK BUTTON ──────────────────────────────────
        // "← Back to Dashboard" with chevron circle + hover micro-interaction
        HBox chevronCircle = makeChevronCircle();
        Label backText = new Label("Back to Dashboard");
        backText.setStyle(
                "-fx-font-size: 13px; -fx-font-family: 'Segoe UI'; -fx-text-fill: #6366F1;"
        );
        HBox backRow = new HBox(10, chevronCircle, backText);
        backRow.setAlignment(Pos.CENTER_LEFT);
        backRow.setStyle("-fx-cursor: hand;");
        backRow.setOnMouseEntered(e -> {
            backText.setStyle("-fx-font-size: 13px; -fx-font-family: 'Segoe UI'; " +
                    "-fx-text-fill: #4F46E5; -fx-font-weight: bold;");
            animateChevronLeft(chevronCircle, true);
        });
        backRow.setOnMouseExited(e -> {
            backText.setStyle("-fx-font-size: 13px; -fx-font-family: 'Segoe UI'; -fx-text-fill: #6366F1;");
            animateChevronLeft(chevronCircle, false);
        });
        backRow.setOnMouseClicked(e -> handleBackToDashboard());

        // Replace any existing back button in rootBox[0] with our new one
        // (FXML usually has a hyperlink or button at position 0)
        if (!rootBox.getChildren().isEmpty()) {
            rootBox.getChildren().set(0, backRow);
        } else {
            rootBox.getChildren().add(0, backRow);
        }

        // ── TOP INPUT CARD — glassmorphism ────────────────────────
        topInputArea.setStyle(
                "-fx-background-color: rgba(255,255,255,0.94);" +
                        "-fx-background-radius: 18; -fx-border-color: #E2E8F0;" +
                        "-fx-border-width: 1; -fx-border-radius: 18;"
        );
        topInputArea.setEffect(new DropShadow(22, 0, 5, Color.color(0,0,0,0.06)));
        topInputArea.setPadding(new Insets(28));

        headerLabel.setStyle(
                "-fx-font-size: 22px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #0F172A;"
        );

        // Input fields
        cityField.setStyle(
                "-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0;" +
                        "-fx-border-radius: 8; -fx-background-radius: 8;" +
                        "-fx-padding: 10 14 10 14; -fx-font-size: 14px; -fx-text-fill: #1E293B;" +
                        "-fx-prompt-text-fill: #94A3B8;"
        );
        modelSelector.setStyle(
                "-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0;" +
                        "-fx-border-radius: 8; -fx-background-radius: 8; -fx-font-size: 14px;"
        );

        // Predict button — indigo
        String btnBase =
                "-fx-background-color: #6366F1; -fx-background-radius: 10;" +
                        "-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;" +
                        "-fx-cursor: hand; -fx-padding: 13 0 13 0;" +
                        "-fx-effect: dropshadow(gaussian, rgba(99,102,241,0.40), 12, 0, 0, 5);";
        String btnHov =
                "-fx-background-color: #4F46E5; -fx-background-radius: 10;" +
                        "-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;" +
                        "-fx-cursor: hand; -fx-padding: 13 0 13 0;" +
                        "-fx-effect: dropshadow(gaussian, rgba(79,70,229,0.55), 16, 0, 0, 6);";
        predictButton.setStyle(btnBase);
        predictButton.setOnMouseEntered(e -> predictButton.setStyle(btnHov));
        predictButton.setOnMouseExited(e  -> predictButton.setStyle(btnBase));

        // ── SYMPTOM TILES (replacing old chips) ───────────────────
        symptomsTitle.setStyle(
                "-fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #64748B; -fx-letter-spacing: 1px;"
        );

        tileBreath     = makeSymptomTile(symptomBreath,    "🫁", "Shortness\nof Breath");
        tileCough      = makeSymptomTile(symptomCough,     "🤧", "Cough /\nWheezing");
        tileChest      = makeSymptomTile(symptomChest,     "💔", "Chest\nTightness");
        tileIrritation = makeSymptomTile(symptomIrritation,"👁", "Eye / Throat\nIrritation");
        tileFatigue    = makeSymptomTile(symptomFatigue,   "😴", "Headache /\nFatigue");

        // Find symptom row in topInputArea and replace children
        // The FXML symptoms section is in a child container — we rebuild it
        rebuildSymptomSection();

        // ── RESULT CARD ───────────────────────────────────────────
        aqiCard.setStyle(
                "-fx-background-color: white; -fx-background-radius: 18; -fx-border-radius: 18;");
        aqiCard.setEffect(new DropShadow(18, 0, 5, Color.color(0,0,0,0.07)));
        aqiCard.setPadding(new Insets(36));
        aqiCard.setAlignment(Pos.CENTER);
        aqiCard.setSpacing(14);

        // City label — wider letter spacing
        cityLabel.setStyle(
                "-fx-font-size: 30px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #0F172A; -fx-letter-spacing: 4px;"
        );
        cityLabel.setMaxWidth(Double.MAX_VALUE);
        cityLabel.setAlignment(Pos.CENTER);

        // ── Arc progress ring ──
        double R  = 108;
        double SW = 10;

        Circle track = new Circle(R);
        track.setFill(Color.TRANSPARENT);
        track.setStroke(Color.web("#E2E8F0"));
        track.setStrokeWidth(SW);

        progressArc = new Arc(0, 0, R, R, 225, 0);
        progressArc.setType(ArcType.OPEN);
        progressArc.setFill(Color.TRANSPARENT);
        progressArc.setStroke(Color.web("#6366F1"));
        progressArc.setStrokeWidth(SW + 1);
        progressArc.setStrokeLineCap(StrokeLineCap.ROUND);

        Circle inner = new Circle(R - SW / 2.0);
        inner.setFill(Color.WHITE);

        riskSubLabel = new Label("RISK SCORE");
        riskSubLabel.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #94A3B8; -fx-letter-spacing: 2px;"
        );
        aqiLabel.setText("—");
        aqiLabel.setStyle(
                "-fx-font-size: 82px; -fx-font-weight: 900; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #CBD5E1;"
        );
        riskOutOf = new Label("/ 100");
        riskOutOf.setStyle(
                "-fx-font-size: 13px; -fx-text-fill: #94A3B8; -fx-font-family: 'Segoe UI';"
        );

        VBox ringContent = new VBox(-4);
        ringContent.setAlignment(Pos.CENTER);
        ringContent.getChildren().addAll(riskSubLabel, aqiLabel, riskOutOf);

        StackPane ring = new StackPane(new Group(track, progressArc), inner, ringContent);
        ring.setPrefSize(R * 2 + 22, R * 2 + 22);
        ring.setMaxSize(R * 2 + 22, R * 2 + 22);
        ring.setAlignment(Pos.CENTER);
        ring.setEffect(new DropShadow(20, 0, 4, Color.color(0,0,0,0.09)));

        aqiCircle.getChildren().clear();
        aqiCircle.getChildren().add(ring);
        aqiCircle.setPrefSize(R * 2 + 22, R * 2 + 22);
        aqiCircle.setMaxSize(R * 2 + 22, R * 2 + 22);
        aqiCircle.setBackground(Background.EMPTY);

        // Risk level text
        aqiStatus.setStyle(
                "-fx-font-size: 17px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #94A3B8; -fx-letter-spacing: 1.5px;"
        );

        // Predicted AQI large label
        predictedAqiLabel = new Label("—");
        predictedAqiLabel.setStyle(
                "-fx-font-size: 30px; -fx-font-weight: 700; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #1E293B;"
        );

        // Pollutant pills
        String pillStyle =
                "-fx-font-size: 13px; -fx-font-family: 'Segoe UI'; -fx-text-fill: #64748B;" +
                        "-fx-background-color: #F1F5F9; -fx-background-radius: 8; -fx-padding: 5 14 5 14;";
        pm25Label.setStyle(pillStyle);
        pm10Label.setStyle(pillStyle);

        HBox pmRow = new HBox(12, pm25Label, pm10Label);
        pmRow.setAlignment(Pos.CENTER);

        modelUsedLabel.setStyle(
                "-fx-font-size: 11px; -fx-text-fill: #94A3B8;" +
                        "-fx-font-style: italic; -fx-font-family: 'Segoe UI';"
        );

        // Left column: ring + labels
        VBox leftCol = new VBox(12);
        leftCol.setAlignment(Pos.CENTER);
        leftCol.getChildren().addAll(aqiCircle, aqiStatus, predictedAqiLabel, pmRow, modelUsedLabel);

        // Boy image — larger, right side
        aqiImage.setFitHeight(215);
        aqiImage.setFitWidth(215);
        aqiImage.setPreserveRatio(true);

        HBox centerRow = new HBox(55, leftCol, aqiImage);
        centerRow.setAlignment(Pos.CENTER);

        aqiCard.getChildren().clear();
        aqiCard.getChildren().addAll(cityLabel, centerRow);

        // ── ADVISORY CARD — metallic gradient ────────────────────
        adviceCard.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #1E293B 0%, #0F172A 100%);" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-color: #6366F1 transparent transparent transparent;" +
                        "-fx-border-width: 4 0 0 0;" +
                        "-fx-border-radius: 18 18 0 0;"
        );
        adviceCard.setEffect(new DropShadow(22, 0, 5, Color.color(0,0,0,0.18)));
        adviceCard.setPadding(new Insets(28));
        adviceCard.setSpacing(12);

        adviceTitle.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #FFFFFF; -fx-letter-spacing: 2.5px;"
        );
        // adviceContentArea is our stable container for dynamic advisory content.
        // Using adviceText.getParent() after wrapInScrollPane() causes a ClassCastException
        // because the parent becomes a ScrollPaneSkin internal node, not a VBox.
        adviceContentArea = new VBox(8);
        adviceContentArea.setFillWidth(true);
        Label placeholder = new Label("Run a prediction to see your personalised advisory.");
        placeholder.setStyle(
                "-fx-font-size: 13px; -fx-font-family: 'Segoe UI';" +
                        "-fx-text-fill: #475569; -fx-font-style: italic;"
        );
        adviceContentArea.getChildren().add(placeholder);
        // Remove the FXML adviceText label and replace with our content area
        adviceCard.getChildren().remove(adviceText);
        if (!adviceCard.getChildren().contains(adviceContentArea)) {
            adviceCard.getChildren().add(adviceContentArea);
        }
    }

    // ── Back button helpers ───────────────────────────────────────
    private HBox makeChevronCircle() {
        Label chevron = new Label("‹");
        chevron.setStyle(
                "-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #6366F1;" +
                        "-fx-font-family: 'Segoe UI';"
        );
        StackPane circle = new StackPane(chevron);
        circle.setPrefSize(32, 32);
        circle.setMaxSize(32, 32);
        circle.setStyle(
                "-fx-background-color: rgba(99,102,241,0.10);" +
                        "-fx-background-radius: 16;"
        );
        HBox box = new HBox(circle);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void animateChevronLeft(HBox chevronContainer, boolean enter) {
        StackPane circle = (StackPane) chevronContainer.getChildren().get(0);
        TranslateTransition tt = new TranslateTransition(Duration.millis(160), circle);
        tt.setToX(enter ? -3 : 0);
        tt.play();
        circle.setStyle(
                (enter
                        ? "-fx-background-color: rgba(99,102,241,0.20);"
                        : "-fx-background-color: rgba(99,102,241,0.10);") +
                        "-fx-background-radius: 16;"
        );
    }

    // ── Symptom tile builder ──────────────────────────────────────
    private VBox makeSymptomTile(CheckBox cb, String icon, String label) {
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 26px;");

        Label nameLbl = new Label(label);
        nameLbl.setStyle(
                "-fx-font-size: 12px; -fx-font-family: 'Segoe UI'; -fx-text-fill: #475569;" +
                        "-fx-text-alignment: center; -fx-alignment: center;"
        );
        nameLbl.setTextAlignment(TextAlignment.CENTER);
        nameLbl.setWrapText(true);
        nameLbl.setMaxWidth(90);

        VBox tile = new VBox(8, iconLbl, nameLbl);
        tile.setAlignment(Pos.CENTER);
        tile.setPrefSize(100, 90);
        tile.setMaxSize(100, 90);
        tile.setStyle(tileStyleInactive());
        tile.setOnMouseClicked(e -> {
            cb.setSelected(!cb.isSelected());
            tile.setStyle(cb.isSelected() ? tileStyleActive() : tileStyleInactive());
        });
        tile.setOnMouseEntered(e -> {
            if (!cb.isSelected())
                tile.setStyle(tileStyleHover());
        });
        tile.setOnMouseExited(e -> {
            if (!cb.isSelected())
                tile.setStyle(tileStyleInactive());
        });
        cb.selectedProperty().addListener((obs, o, n) ->
                tile.setStyle(n ? tileStyleActive() : tileStyleInactive()));
        return tile;
    }

    private String tileStyleInactive() {
        return "-fx-background-color: #F8FAFC; -fx-background-radius: 14;" +
                "-fx-border-color: #E2E8F0; -fx-border-radius: 14; -fx-border-width: 1.5;" +
                "-fx-cursor: hand;";
    }
    private String tileStyleHover() {
        return "-fx-background-color: #EEF2FF; -fx-background-radius: 14;" +
                "-fx-border-color: #A5B4FC; -fx-border-radius: 14; -fx-border-width: 1.5;" +
                "-fx-cursor: hand;" +
                "-fx-effect: dropshadow(gaussian, rgba(99,102,241,0.15), 8, 0, 0, 2);";
    }
    private String tileStyleActive() {
        return "-fx-background-color: #EEF2FF; -fx-background-radius: 14;" +
                "-fx-border-color: #6366F1; -fx-border-radius: 14; -fx-border-width: 2;" +
                "-fx-cursor: hand;" +
                "-fx-effect: dropshadow(gaussian, rgba(99,102,241,0.30), 12, 0, 0, 3);";
    }

    /** Replaces the symptom HBox inside topInputArea with rich tiles */
    private void rebuildSymptomSection() {
        // Find the right-side VBox in topInputArea (child index 1 typically)
        for (javafx.scene.Node node : topInputArea.getChildren()) {
            if (node instanceof VBox vbox) {
                // Look for the symptoms container (has symptomsTitle as a child)
                for (javafx.scene.Node inner : new ArrayList<>(vbox.getChildren())) {
                    if (inner instanceof HBox hbox) {
                        // Check if it contains checkboxes (old symptom row)
                        boolean hasCheckbox = hbox.getChildren().stream()
                                .anyMatch(n -> n instanceof CheckBox);
                        if (hasCheckbox) {
                            int idx = vbox.getChildren().indexOf(hbox);
                            // Replace with tiles row + hint text
                            HBox tilesRow = new HBox(12,
                                    tileBreath, tileCough, tileChest,
                                    tileIrritation, tileFatigue);
                            tilesRow.setAlignment(Pos.CENTER_LEFT);

                            Label hint = new Label(
                                    "Select all symptoms that apply to refine your medical advisory.");
                            hint.setStyle(
                                    "-fx-font-size: 11px; -fx-font-family: 'Segoe UI';" +
                                            "-fx-text-fill: #94A3B8; -fx-font-style: italic;"
                            );

                            VBox sympSection = new VBox(10);
                            sympSection.getChildren().addAll(tilesRow, hint);

                            vbox.getChildren().set(idx, sympSection);
                            return;
                        }
                    }
                }
            }
        }
        // Fallback: just append tiles at the end of topInputArea
        HBox tilesRow = new HBox(12,
                tileBreath, tileCough, tileChest, tileIrritation, tileFatigue);
        tilesRow.setAlignment(Pos.CENTER_LEFT);
        topInputArea.getChildren().add(tilesRow);
    }
}