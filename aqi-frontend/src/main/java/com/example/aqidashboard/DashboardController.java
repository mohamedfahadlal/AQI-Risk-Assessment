package com.example.aqidashboard;

import com.aqi.utils.EmailUtil;
import com.aqi.utils.SceneManager;
import com.aqi.utils.UserSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import javafx.scene.transform.Rotate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public class DashboardController {

    private static final String BACKEND = "http://localhost:8080/api";

    // ── Navbar ────────────────────────────────────────────────────
    @FXML private TextField citySearchField;
    @FXML private Label welcomeLabel;
    @FXML private Label lastRefreshedLabel;
    @FXML private Label autoRefreshLabel;
    @FXML private Label alertStatusLabel;
    @FXML private Button darkModeBtn;

    // ── Tab 1: AQI View ───────────────────────────────────────────
    @FXML private VBox aqiView;
    @FXML private VBox weatherView;
    @FXML private Button aqiToggleBtn;
    @FXML private Button weatherToggleBtn;
    @FXML private HBox mainCard;
    @FXML private Line needle;
    @FXML private Label aqiLabel;
    @FXML private Label statusLabel;
    @FXML private Label cityLabel;
    @FXML private Label pm25Label;
    @FXML private Label pm10Label;
    @FXML private Label tempLabel;
    @FXML private Label humidityLabel;
    @FXML private Label windLabel;
    @FXML private ImageView aqiImageView;

    // ── Tab 1: Weather View ───────────────────────────────────────
    @FXML private ImageView weatherIconView;
    @FXML private Label weatherDescLabel;
    @FXML private Label weatherCityLabel;
    @FXML private Label weatherTempLabel;
    @FXML private Label weatherFeelsLabel;
    @FXML private Label wHumidityLabel;
    @FXML private Label wWindSpeedLabel;
    @FXML private Label wWindDirLabel;
    @FXML private Label wVisibilityLabel;
    @FXML private Label wPressureLabel;
    @FXML private Label wCloudsLabel;
    @FXML private Label wSunriseLabel;
    @FXML private Label wSunsetLabel;
    @FXML private Label wMinMaxLabel;

    // ── Tab 2: Forecast ───────────────────────────────────────────
    @FXML private LineChart<String, Number> forecastChart;
    @FXML private CategoryAxis forecastXAxis;
    @FXML private NumberAxis forecastYAxis;
    @FXML private ComboBox<String> forecastIntervalBox;
    @FXML private HBox dailySummaryBox;

    // ── Tab 3: Pollutants ─────────────────────────────────────────
    @FXML private GridPane pollutantsGrid;

    // ── Tab 4: India Cities ───────────────────────────────────────
    @FXML private GridPane citiesGrid;
    @FXML private Button sortCitiesBtn;

    // ── Tab 5: Feedback ───────────────────────────────────────────
    @FXML private TextField feedbackName;
    @FXML private TextField feedbackEmail;
    @FXML private TextArea feedbackMessage;
    @FXML private Label feedbackStatusLabel;

    // ── State ─────────────────────────────────────────────────────
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContextMenu suggestionsPopup = new ContextMenu();
    private final List<Map<String, Object>> citiesData = Collections.synchronizedList(new ArrayList<>());

    private double selectedLat = 0, selectedLon = 0;
    private String currentCity = "Kochi";
    private JsonNode lastAqiData = null;
    private JsonNode lastForecastData = null;
    private boolean sortWorstFirst = true;
    private boolean isDarkMode = false;
    private int alertThreshold = -1;
    private int refreshCountdown = 900;
    private ScheduledExecutorService scheduler;

    // ── WHO limits µg/m³ ─────────────────────────────────────────
    private static final Map<String, Double> WHO = new LinkedHashMap<>();
    static {
        WHO.put("pm25", 15.0);
        WHO.put("pm10", 45.0);
        WHO.put("no2",  25.0);
        WHO.put("o3",  100.0);
        WHO.put("so2",  40.0);
        WHO.put("co", 4000.0);
        WHO.put("nh3",  25.0);
        WHO.put("no",   25.0);
    }

    // ─────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        updateNeedle(0);
        statusLabel.setText("");
        aqiLabel.setText("--");

        String username = UserSession.getUsername();
        if (username != null && !username.isEmpty())
            welcomeLabel.setText("Hi, " + username);

        forecastIntervalBox.setItems(FXCollections.observableArrayList(
                "Every 3 hrs", "Every 6 hrs", "Every 12 hrs", "Every 24 hrs"));
        forecastIntervalBox.setValue("Every 3 hrs");
        forecastIntervalBox.setOnAction(e -> redrawForecastChart());

        citySearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            selectedLat = 0;
            selectedLon = 0;
            if (newVal == null || newVal.length() < 2) {
                suggestionsPopup.hide();
                return;
            }
            fetchSuggestions(newVal);
        });

        startAutoRefresh();
        loadAQIData("Kochi", 0, 0);
        loadIndiaCities();
    }

    // ─────────────────────────────────────────────────────────────
    // NAVIGATION
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleOpenPrediction() {
        SceneManager.switchScene("/com/example/test2/main_view.fxml", "AQI Prediction");
    }

    @FXML private void handleLogout() {
        stopAutoRefresh();
        UserSession.clear();
        SceneManager.switchScene("/fxml/Login.fxml", "Login");
    }

    @FXML private void handleViewProfile() {
        SceneManager.switchScene("/views/ViewProfile.fxml", "Your Profile");
    }

    @FXML private void handleAbout() {
        SceneManager.switchScene("/fxml/About.fxml", "About Us");
    }

    // ─────────────────────────────────────────────────────────────
    // TAB 1: TOGGLE AQI / WEATHER
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleAqiToggle() {
        aqiView.setVisible(true);     aqiView.setManaged(true);
        weatherView.setVisible(false); weatherView.setManaged(false);
        aqiToggleBtn.setStyle(
                "-fx-background-radius: 20 0 0 20; -fx-background-color: #1a73e8;" +
                        "-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 28;");
        weatherToggleBtn.setStyle(
                "-fx-background-radius: 0 20 20 0; -fx-background-color: #e8eaed;" +
                        "-fx-text-fill: #555; -fx-padding: 8 28;");
    }

    @FXML private void handleWeatherToggle() {
        aqiView.setVisible(false);   aqiView.setManaged(false);
        weatherView.setVisible(true); weatherView.setManaged(true);
        weatherToggleBtn.setStyle(
                "-fx-background-radius: 0 20 20 0; -fx-background-color: #1a73e8;" +
                        "-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 28;");
        aqiToggleBtn.setStyle(
                "-fx-background-radius: 20 0 0 20; -fx-background-color: #e8eaed;" +
                        "-fx-text-fill: #555; -fx-padding: 8 28;");
        if (lastAqiData != null) updateWeatherView(lastAqiData);
    }

    // ─────────────────────────────────────────────────────────────
    // DARK MODE
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleDarkMode() {
        isDarkMode = !isDarkMode;
        darkModeBtn.setText(isDarkMode ? "☀ Light Mode" : "🌙 Dark Mode");
        Platform.runLater(() -> {
            if (darkModeBtn.getScene() == null) return;
            var stylesheets = darkModeBtn.getScene().getStylesheets();
            var url = getClass().getResource("/css/dark-theme.css");
            if (url != null) {
                String darkCss = url.toExternalForm();
                if (isDarkMode) { if (!stylesheets.contains(darkCss)) stylesheets.add(darkCss); }
                else            { stylesheets.remove(darkCss); }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // AQI ALERT
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleSetAlert() {
        TextInputDialog dialog = new TextInputDialog(
                alertThreshold > 0 ? String.valueOf(alertThreshold) : "100");
        dialog.setTitle("Set AQI Alert");
        dialog.setHeaderText("AQI Threshold Alert");
        dialog.setContentText("Alert me when AQI exceeds:");
        dialog.showAndWait().ifPresent(val -> {
            try {
                alertThreshold = Integer.parseInt(val.trim());
                alertStatusLabel.setText("Alert: AQI > " + alertThreshold);
                if (lastAqiData != null) checkAlert(lastAqiData.path("aqi").asInt());
            } catch (NumberFormatException e) {
                alertStatusLabel.setText("Invalid number");
            }
        });
    }

    private void checkAlert(int aqi) {
        if (alertThreshold > 0 && aqi > alertThreshold) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("AQI Alert — AiQI");
                alert.setHeaderText("⚠ Air Quality Alert for " + currentCity);
                alert.setContentText("AQI has reached " + aqi + " — " + getAqiLevel(aqi)
                        + "\nConsider limiting outdoor activities.");
                alert.show();
            });
        }
    }

    // ─────────────────────────────────────────────────────────────
    // EXPORT PDF
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleExportPdf() {
        if (lastAqiData == null) {
            showInfo("No data to export. Please search a city first.");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                PdfExportService.exportReport(lastAqiData, currentCity);
                Platform.runLater(() -> showInfo("Report saved to your Downloads folder!"));
            } catch (Exception e) {
                Platform.runLater(() -> showInfo("Export failed: " + e.getMessage()));
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ─────────────────────────────────────────────────────────────
    // SEARCH & LOCATION
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleCitySearch() {
        String text = citySearchField.getText();
        if (text == null || text.isEmpty()) return;
        loadAQIData(text, selectedLat, selectedLon);
    }

    @FXML private void handleDetectLocation() {
        Platform.runLater(() -> {
            aqiLabel.setText("...");
            statusLabel.setText("Locating...");
        });
        Thread thread = new Thread(() -> {
            try {
                HttpRequest geoReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://ip-api.com/json/")).GET().build();
                HttpResponse<String> geoRes = httpClient.send(geoReq, HttpResponse.BodyHandlers.ofString());
                JsonNode geo = objectMapper.readTree(geoRes.body());
                double lat   = geo.path("lat").asDouble();
                double lon   = geo.path("lon").asDouble();
                String city  = geo.path("city").asText("Your Location");
                Platform.runLater(() -> citySearchField.setText(city));
                selectedLat = lat;
                selectedLon = lon;
                currentCity = city;

                HttpRequest aqiReq = HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND + "/aqi/locate?lat=" + lat + "&lon=" + lon))
                        .GET().build();
                HttpResponse<String> aqiRes = httpClient.send(aqiReq, HttpResponse.BodyHandlers.ofString());
                if (aqiRes.statusCode() == 200) {
                    updateUIFromResponse(aqiRes.body());
                    loadForecastData(city, lat, lon);
                }
            } catch (Exception e) {
                Platform.runLater(() -> showError("Could not detect location"));
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void fetchSuggestions(String query) {
        Thread thread = new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND + "/search?q=" + encoded)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
                        MenuItem item  = new MenuItem(display);
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
                    if (!suggestionsPopup.isShowing() && citySearchField.getScene() != null)
                        suggestionsPopup.show(citySearchField, javafx.geometry.Side.BOTTOM, 0, 0);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    // ─────────────────────────────────────────────────────────────
    // LOAD AQI DATA
    // ─────────────────────────────────────────────────────────────
    private void loadAQIData(String city, double lat, double lon) {
        Platform.runLater(() -> {
            aqiLabel.setText("...");
            statusLabel.setText("Loading...");
        });
        Thread thread = new Thread(() -> {
            try {
                String url = (lat != 0 && lon != 0)
                        ? BACKEND + "/aqi/locate?lat=" + lat + "&lon=" + lon
                        : BACKEND + "/aqi?city=" + URLEncoder.encode(city, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    currentCity = city;
                    updateUIFromResponse(response.body());
                    loadForecastData(city, lat, lon);
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

    private void updateUIFromResponse(String body) throws Exception {
        JsonNode data = objectMapper.readTree(body);
        lastAqiData   = data;

        int    aqi      = data.path("aqi").asInt();
        String cityName = data.path("city").asText("--");
        double pm25     = data.path("pm25").asDouble();
        double pm10     = data.path("pm10").asDouble();
        double temp     = data.path("temperature").asDouble();
        double humidity = data.path("humidity").asDouble();
        double wind     = data.path("windSpeed").asDouble();

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
            updateLastRefreshed();
            updatePollutantsTab();
            checkAlert(aqi);
            if (weatherView.isVisible()) updateWeatherView(data);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // TAB 1: WEATHER VIEW
    // ─────────────────────────────────────────────────────────────
    private void updateWeatherView(JsonNode data) {
        String desc      = data.path("description").asText("--");
        String iconCode  = data.path("icon").asText("");
        double temp      = data.path("temperature").asDouble();
        double feelsLike = data.path("feelsLike").asDouble();
        double humidity  = data.path("humidity").asDouble();
        double windSpeed = data.path("windSpeed").asDouble();
        String windDir   = data.path("windDir").asText("--");
        double visibility= data.path("visibility").asDouble();
        double pressure  = data.path("pressure").asDouble();
        double clouds    = data.path("clouds").asDouble();
        long   sunrise   = data.path("sunrise").asLong();
        long   sunset    = data.path("sunset").asLong();
        double tempMin   = data.path("tempMin").asDouble();
        double tempMax   = data.path("tempMax").asDouble();
        String cityName  = data.path("city").asText("--");

        String descCap = desc.isEmpty() ? "--"
                : Character.toUpperCase(desc.charAt(0)) + desc.substring(1);
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a");
        String sunriseStr = sunrise > 0 ? sdf.format(new Date(sunrise * 1000)) : "--";
        String sunsetStr  = sunset  > 0 ? sdf.format(new Date(sunset  * 1000)) : "--";

        weatherDescLabel.setText(descCap);
        weatherCityLabel.setText(cityName);
        weatherTempLabel.setText(String.format("%.0f°C", temp));
        weatherFeelsLabel.setText(String.format("Feels like %.0f°C", feelsLike));
        wHumidityLabel.setText(String.format("%.0f%%", humidity));
        wWindSpeedLabel.setText(String.format("%.1f km/h", windSpeed));
        wWindDirLabel.setText(windDir);
        wVisibilityLabel.setText(String.format("%.1f km", visibility));
        wPressureLabel.setText(String.format("%.0f hPa", pressure));
        wCloudsLabel.setText(String.format("%.0f%%", clouds));
        wSunriseLabel.setText(sunriseStr);
        wSunsetLabel.setText(sunsetStr);
        wMinMaxLabel.setText(String.format("%.0f°C / %.0f°C", tempMin, tempMax));

        // Load OWM weather icon from URL
        if (!iconCode.isEmpty()) {
            try {
                Image icon = new Image(
                        "https://openweathermap.org/img/wn/" + iconCode + "@2x.png", true);
                weatherIconView.setImage(icon);
            } catch (Exception e) {
                System.out.println("Could not load weather icon: " + iconCode);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // TAB 2: FORECAST
    // ─────────────────────────────────────────────────────────────
    private void loadForecastData(String city, double lat, double lon) {
        Thread t = new Thread(() -> {
            try {
                String url = (lat != 0 && lon != 0)
                        ? BACKEND + "/forecast/locate?lat=" + lat + "&lon=" + lon
                        : BACKEND + "/forecast?city=" + URLEncoder.encode(city, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    lastForecastData = objectMapper.readTree(response.body());
                    Platform.runLater(this::redrawForecastChart);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void redrawForecastChart() {
        if (lastForecastData == null) return;
        JsonNode entries = lastForecastData.path("entries");

        int step = 1;
        String interval = forecastIntervalBox.getValue();
        if (interval != null) {
            if (interval.contains("6"))  step = 2;
            if (interval.contains("12")) step = 4;
            if (interval.contains("24")) step = 8;
        }

        XYChart.Series<String, Number> aqiSeries  = new XYChart.Series<>();
        XYChart.Series<String, Number> tempSeries = new XYChart.Series<>();
        aqiSeries.setName("AQI");
        tempSeries.setName("Temp (°C)");

        int count = 0;
        for (JsonNode entry : entries) {
            if (count % step != 0) { count++; continue; }
            String label = formatForecastLabel(entry.path("dtTxt").asText());
            aqiSeries.getData().add(new XYChart.Data<>(label, entry.path("aqi").asInt()));
            tempSeries.getData().add(new XYChart.Data<>(label, entry.path("temp").asDouble()));
            count++;
        }

        forecastChart.getData().clear();
        forecastChart.getData().addAll(aqiSeries, tempSeries);

        // Style lines after rendering
        Platform.runLater(() -> {
            if (aqiSeries.getNode() != null)
                aqiSeries.getNode().setStyle("-fx-stroke: #1a73e8; -fx-stroke-width: 2.5px;");
            if (tempSeries.getNode() != null)
                tempSeries.getNode().setStyle("-fx-stroke: #f59e0b; -fx-stroke-width: 2.5px;");
        });

        // Daily summary cards
        dailySummaryBox.getChildren().clear();
        lastForecastData.path("dailySummary").fields().forEachRemaining(e -> {
            JsonNode d = e.getValue();
            dailySummaryBox.getChildren().add(buildDaySummaryCard(
                    e.getKey(),
                    d.path("minAqi").asInt(), d.path("maxAqi").asInt(),
                    d.path("minTemp").asDouble(), d.path("maxTemp").asDouble()));
        });
    }

    private String formatForecastLabel(String dtTxt) {
        try {
            String[] parts = dtTxt.split(" ");
            int hour  = Integer.parseInt(parts[1].substring(0, 2));
            String amPm = hour < 12 ? "AM" : "PM";
            int h = hour % 12;
            if (h == 0) h = 12;
            LocalDate date = LocalDate.parse(parts[0]);
            String day = date.getDayOfWeek().name().substring(0, 3);
            return day + " " + h + amPm;
        } catch (Exception e) {
            return dtTxt.length() >= 13 ? dtTxt.substring(5, 13) : dtTxt;
        }
    }

    private VBox buildDaySummaryCard(String day, int minAqi, int maxAqi,
                                     double minTemp, double maxTemp) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(140);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 10, 0, 0, 2);");

        Label dayLbl;
        try {
            LocalDate d = LocalDate.parse(day);
            dayLbl = new Label(d.getDayOfWeek().name().substring(0, 3) + " " + d.getDayOfMonth());
        } catch (Exception e) {
            dayLbl = new Label(day);
        }
        dayLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #333;");

        Label aqiRange  = new Label("AQI " + minAqi + " – " + maxAqi);
        aqiRange.setStyle("-fx-font-size: 12px; -fx-text-fill: " + aqiColor(maxAqi) +
                "; -fx-font-weight: bold;");

        Label tempRange = new Label(String.format("%.0f – %.0f°C", minTemp, maxTemp));
        tempRange.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        card.getChildren().addAll(dayLbl, aqiRange, tempRange);
        return card;
    }

    // ─────────────────────────────────────────────────────────────
    // TAB 3: POLLUTANTS
    // ─────────────────────────────────────────────────────────────
    private void updatePollutantsTab() {
        if (lastAqiData == null) return;
        pollutantsGrid.getChildren().clear();

        String[][] pollutants = {
                {"PM2.5", "pm25"}, {"PM10",  "pm10"},
                {"NO2",   "no2"},  {"O3",    "o3"},
                {"SO2",   "so2"},  {"CO",    "co"},
                {"NH3",   "nh3"},  {"NO",    "no"}
        };

        for (int i = 0; i < pollutants.length; i++) {
            String name  = pollutants[i][0];
            String key   = pollutants[i][1];
            double value = lastAqiData.path(key).asDouble();
            double limit = WHO.getOrDefault(key, 100.0);
            double pct   = Math.min(value / limit, 1.0);
            pollutantsGrid.add(buildPollutantCard(name, value, limit, pct), i % 2, i / 2);
        }
    }

    private VBox buildPollutantCard(String name, double value, double limit, double pct) {
        VBox card = new VBox(8);
        card.setPrefWidth(380);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 10, 0, 0, 2);");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #333;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label valueLbl = new Label(String.format("%.2f µg/m³", value));
        valueLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " +
                progressColor(pct) + ";");
        header.getChildren().addAll(nameLbl, spacer, valueLbl);

        ProgressBar bar = new ProgressBar(pct);
        bar.setPrefWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-accent: " + progressColor(pct) + "; -fx-background-radius: 6;");

        Label whoLbl = new Label(String.format(
                "WHO safe limit: %.0f µg/m³  |  %.0f%% of limit", limit, pct * 100));
        whoLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        card.getChildren().addAll(header, bar, whoLbl);
        return card;
    }

    private String progressColor(double pct) {
        if (pct <= 0.5) return "#2e7d32";
        if (pct <= 0.8) return "#f59e0b";
        return "#dc2626";
    }

    // ─────────────────────────────────────────────────────────────
    // TAB 4: INDIA CITIES
    // ─────────────────────────────────────────────────────────────
    private void loadIndiaCities() {
        Platform.runLater(() -> {
            citiesGrid.getChildren().clear();
            citiesData.clear();
            String[] cities = {"Delhi","Mumbai","Kolkata","Chennai",
                    "Bengaluru","Hyderabad","Ahmedabad","Kochi"};
            for (int i = 0; i < cities.length; i++)
                citiesGrid.add(buildCityCardLoading(cities[i]), i % 4, i / 4);
        });

        Thread t = new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND + "/cities/india")).GET().build();
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode arr = objectMapper.readTree(response.body());
                    citiesData.clear();
                    for (JsonNode node : arr) {
                        Map<String, Object> city = new LinkedHashMap<>();
                        city.put("city", node.path("city").asText());
                        city.put("aqi",  node.path("aqi").asInt());
                        city.put("pm25", node.path("pm25").asDouble());
                        city.put("temp", node.path("temp").asDouble());
                        citiesData.add(city);
                    }
                    Platform.runLater(this::renderCitiesGrid);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML private void handleRefreshCities() { loadIndiaCities(); }

    @FXML private void handleSortCities() {
        sortWorstFirst = !sortWorstFirst;
        sortCitiesBtn.setText(sortWorstFirst ? "Sort: Worst First" : "Sort: Best First");
        renderCitiesGrid();
    }

    private void renderCitiesGrid() {
        if (citiesData.isEmpty()) return;
        List<Map<String, Object>> sorted = new ArrayList<>(citiesData);
        sorted.sort((a, b) -> sortWorstFirst
                ? Integer.compare((int) b.get("aqi"), (int) a.get("aqi"))
                : Integer.compare((int) a.get("aqi"), (int) b.get("aqi")));
        citiesGrid.getChildren().clear();
        for (int i = 0; i < sorted.size(); i++)
            citiesGrid.add(buildCityCard(sorted.get(i)), i % 4, i / 4);
    }

    private VBox buildCityCardLoading(String cityName) {
        VBox card = new VBox(8);
        card.setPrefWidth(200);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setStyle("-fx-background-color: #f1f3f4; -fx-background-radius: 14;");
        Label name = new Label(cityName);
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #555;");
        Label loading = new Label("Loading...");
        loading.setStyle("-fx-font-size: 12px; -fx-text-fill: #aaa;");
        card.getChildren().addAll(name, loading);
        return card;
    }

    private VBox buildCityCard(Map<String, Object> data) {
        int    aqi  = (int)    data.get("aqi");
        String city = (String) data.get("city");
        double pm25 = (double) data.getOrDefault("pm25", 0.0);
        double temp = (double) data.getOrDefault("temp", 0.0);
        String fg   = aqiColor(aqi);
        String bg   = aqiBgColor(aqi);

        VBox card = new VBox(8);
        card.setPrefWidth(200);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 14;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 2);");

        Label nameLbl  = new Label(city);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #333;");
        Label aqiLbl   = new Label("AQI " + aqi);
        aqiLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: 900; -fx-text-fill: " + fg + ";");
        Label levelLbl = new Label(getAqiLevel(aqi));
        levelLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + fg + ";");
        Label pm25Lbl  = new Label(String.format("PM2.5: %.1f µg/m³", pm25));
        pm25Lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        Label tempLbl  = new Label(String.format("Temp: %.0f°C", temp));
        tempLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

        card.getChildren().addAll(nameLbl, aqiLbl, levelLbl, pm25Lbl, tempLbl);
        return card;
    }

    // ─────────────────────────────────────────────────────────────
    // TAB 5: FEEDBACK
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleSendFeedback() {
        String name    = feedbackName.getText().trim();
        String email   = feedbackEmail.getText().trim();
        String message = feedbackMessage.getText().trim();

        if (name.isEmpty() || message.isEmpty()) {
            feedbackStatusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
            feedbackStatusLabel.setText("Please enter your name and message.");
            return;
        }
        feedbackStatusLabel.setStyle("-fx-text-fill: #1a73e8; -fx-font-size: 12px;");
        feedbackStatusLabel.setText("Sending...");

        Thread t = new Thread(() -> {
            try {
                String subject = "AiQI Feedback from " + name;
                String body    = "From: " + name + "\nEmail: " + email + "\n\nMessage:\n" + message;
                // sendOtpEmail reused for general email sending
                EmailUtil.sendOtpEmail("team@aiqi.com", body);
                Platform.runLater(() -> {
                    feedbackStatusLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 12px;");
                    feedbackStatusLabel.setText("Feedback sent! Thank you.");
                    feedbackName.clear();
                    feedbackEmail.clear();
                    feedbackMessage.clear();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    feedbackStatusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
                    feedbackStatusLabel.setText("Failed to send. Please email us directly.");
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ─────────────────────────────────────────────────────────────
    // AUTO REFRESH
    // ─────────────────────────────────────────────────────────────
    private void startAutoRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            refreshCountdown--;
            int mins = refreshCountdown / 60;
            int secs = refreshCountdown % 60;
            Platform.runLater(() ->
                    autoRefreshLabel.setText(String.format("Auto-refresh in: %02d:%02d", mins, secs)));
            if (refreshCountdown <= 0) {
                refreshCountdown = 900;
                loadAQIData(currentCity, selectedLat, selectedLon);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown())
            scheduler.shutdownNow();
    }

    private void updateLastRefreshed() {
        String time = new SimpleDateFormat("h:mm a").format(new Date());
        Platform.runLater(() -> {
            lastRefreshedLabel.setText("Last refreshed: " + time);
            refreshCountdown = 900;
        });
    }

    // ─────────────────────────────────────────────────────────────
    // GAUGE & RISK
    // ─────────────────────────────────────────────────────────────
    private void updateNeedle(int aqi) {
        double angle;
        if      (aqi <= 50)  angle = (aqi / 50.0) * 30;
        else if (aqi <= 100) angle = 30  + ((aqi - 50)  / 50.0)  * 30;
        else if (aqi <= 150) angle = 60  + ((aqi - 100) / 50.0)  * 30;
        else if (aqi <= 200) angle = 90  + ((aqi - 150) / 50.0)  * 30;
        else if (aqi <= 300) angle = 120 + ((aqi - 200) / 100.0) * 30;
        else                 angle = 150 + ((Math.min(aqi, 500) - 300) / 200.0) * 30;
        Rotate rotate = new Rotate(angle, 190, 190);
        needle.getTransforms().clear();
        needle.getTransforms().add(rotate);
    }

    private void updateRisk(int aqi) {
        String risk, hexColor, gradientStyle, imageName;
        String baseStyle = "-fx-background-radius: 20;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 20, 0, 0, 4);" +
                "-fx-padding: 30 40;";

        if (aqi <= 50) {
            risk = "Good";      hexColor = "#2ecc71";
            gradientStyle = "linear-gradient(to bottom right, #eafaf1, #d5f5e3)";
            imageName = "good.png";
        } else if (aqi <= 100) {
            risk = "Moderate";  hexColor = "#f1c40f";
            gradientStyle = "linear-gradient(to bottom right, #fef9e7, #f9e79f)";
            imageName = "moderate.png";
        } else if (aqi <= 150) {
            risk = "Poor";      hexColor = "#e67e22";
            gradientStyle = "linear-gradient(to bottom right, #fdf2e9, #fad7a0)";
            imageName = "poor.png";
        } else if (aqi <= 200) {
            risk = "Unhealthy"; hexColor = "#d35400";
            gradientStyle = "linear-gradient(to bottom right, #fbeee6, #edbb99)";
            imageName = "unhealthy.png";
        } else if (aqi <= 300) {
            risk = "Severe";    hexColor = "#8e44ad";
            gradientStyle = "linear-gradient(to bottom right, #f4ecf7, #d7bde2)";
            imageName = "severe.png";
        } else {
            risk = "Hazardous"; hexColor = "#c0392b";
            gradientStyle = "linear-gradient(to bottom right, #fadbd8, #f1948a)";
            imageName = "hazardous.png";
        }

        Platform.runLater(() -> {
            statusLabel.setText(risk);
            statusLabel.setStyle("-fx-text-fill: " + hexColor +
                    "; -fx-font-weight: bold; -fx-font-size: 22px;");
            aqiLabel.setStyle("-fx-text-fill: " + hexColor +
                    "; -fx-font-weight: 900; -fx-font-size: 52px;");
            mainCard.setStyle("-fx-background-color: " + gradientStyle + "; " + baseStyle);

            // Force dark text for readability on light gradient cards
            String darkText = "-fx-text-fill: #2d3436;";
            cityLabel.setStyle(darkText + " -fx-font-weight: bold; -fx-font-size: 18px;");
            pm25Label.setStyle(darkText);
            pm10Label.setStyle(darkText);
            tempLabel.setStyle(darkText);
            humidityLabel.setStyle(darkText);
            windLabel.setStyle(darkText);
        });

        try {
            Image image = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/images/" + imageName)));
            aqiImageView.setImage(image);
        } catch (Exception e) {
            System.out.println("Image not found: " + imageName);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────
    private String getAqiLevel(int aqi) {
        if (aqi <= 50)  return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Poor";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Severe";
        return "Hazardous";
    }

    private String aqiColor(int aqi) {
        if (aqi <= 50)  return "#1e8449";
        if (aqi <= 100) return "#9a7d0a";
        if (aqi <= 150) return "#ca6f1e";
        if (aqi <= 200) return "#a04000";
        if (aqi <= 300) return "#7d3c98";
        return "#922b21";
    }

    private String aqiBgColor(int aqi) {
        if (aqi <= 50)  return "#eafaf1";
        if (aqi <= 100) return "#fef9e7";
        if (aqi <= 150) return "#fdf2e9";
        if (aqi <= 200) return "#fbeee6";
        if (aqi <= 300) return "#f4ecf7";
        return "#fadbd8";
    }

    private void showError(String message) {
        aqiLabel.setText("--");
        statusLabel.setText("Error");
        cityLabel.setText(message);
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("AiQI");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }
}
