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
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.animation.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

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

    // ── Capsule tab switcher ──────────────────────────────────────
    @FXML private Button tabBtn1, tabBtn2, tabBtn3, tabBtn4, tabBtn5;
    @FXML private VBox tabContent1, tabContent2, tabContent3, tabContent4, tabContent5;
    private Button[] tabBtns;
    private VBox[]   tabContents;

    private static final String TAB_ACTIVE   =
            "-fx-background-radius: 22; -fx-background-color: #1a73e8; -fx-text-fill: white;" +
                    "-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 9 22; -fx-cursor: hand;" +
                    "-fx-effect: dropshadow(gaussian, rgba(26,115,232,0.4), 8, 0, 0, 2);";
    private static final String TAB_INACTIVE =
            "-fx-background-radius: 22; -fx-background-color: transparent; -fx-text-fill: #555;" +
                    "-fx-font-size: 13px; -fx-padding: 9 22; -fx-cursor: hand;";

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
    @FXML private HBox weatherCard;
    @FXML private StackPane weatherCardStack;
    @FXML private ImageView weatherBgImage;
    @FXML private Region weatherCardOverlay;
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
    @FXML private Label wMinLabel, wMaxLabel;

    // ── Tab 2: Forecast ───────────────────────────────────────────
    @FXML private VBox forecastChartContainer;
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
    @FXML private Button locateMeBtn;
    @FXML private Button refreshCitiesBtn;
    @FXML private Button refreshPollutantsBtn;
    @FXML private Button aqiMapBtn;
    @FXML private Button searchBtn;
    @FXML private Button predictBtn;
    @FXML private Button myProfileBtn;
    @FXML private Button logoutBtn;
    @FXML private Button setAlertBtn;
    @FXML private Button exportPdfBtn;
    @FXML private Button sendFeedbackBtn;

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
    private final boolean[] tabVisited = new boolean[5]; // tracks first-open per tab
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
        // Show/hide logout based on guest status — use fx:id fields directly
        if (UserSession.isGuest()) {
            if (logoutBtn    != null) { logoutBtn.setVisible(false);   logoutBtn.setManaged(false); }
            if (myProfileBtn != null) {
                myProfileBtn.setText("Sign Up Free");
                myProfileBtn.setStyle("-fx-background-radius: 20; -fx-background-color: #1a73e8;" +
                        "-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 14; -fx-cursor: hand;");
                myProfileBtn.setOnAction(e -> guardGuest());
            }
        } else {
            // Logged-in user — restore proper state in case session changed
            if (logoutBtn    != null) { logoutBtn.setVisible(true);    logoutBtn.setManaged(true); }
            if (myProfileBtn != null) {
                myProfileBtn.setText("My Profile");
                myProfileBtn.setOnAction(e -> handleViewProfile());
            }
        }
        if (username != null && !username.isEmpty())
            welcomeLabel.setText("Hi, " + username);

        // Nav button hover glow — applied after scene is ready
        Platform.runLater(() -> {
            if (darkModeBtn.getParent() != null) {
                for (javafx.scene.Node n : darkModeBtn.getParent().getChildrenUnmodifiable()) {
                    if (n instanceof Button navBtn) {
                        addButtonHover(navBtn, "rgba(26,115,232,0.32)");
                    }
                }
            }
        });

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

        // Wire capsule tab switcher
        tabBtns     = new Button[]{tabBtn1, tabBtn2, tabBtn3, tabBtn4, tabBtn5};
        tabContents = new VBox[]{tabContent1, tabContent2, tabContent3, tabContent4, tabContent5};

        // Tab button hovers
        for (Button tb : tabBtns) {
            String baseTab = tb.getStyle() != null ? tb.getStyle() : "";
            tb.setOnMouseEntered(e -> {
                if (!tb.getStyle().contains("#1a73e8")) { // not active
                    ScaleTransition st = new ScaleTransition(Duration.millis(120), tb);
                    st.setToX(1.05); st.setToY(1.05); st.play();
                    tb.setStyle(TAB_INACTIVE +
                            " -fx-effect: dropshadow(gaussian, rgba(26,115,232,0.28), 14, 0.3, 0, 2);");
                }
                tb.setCursor(javafx.scene.Cursor.HAND);
            });
            tb.setOnMouseExited(e -> {
                if (!tb.getStyle().contains("#1a73e8")) {
                    ScaleTransition st = new ScaleTransition(Duration.millis(120), tb);
                    st.setToX(1.0); st.setToY(1.0); st.play();
                    tb.setStyle(TAB_INACTIVE);
                }
            });
        }

        // All button hovers — wired after scene is ready
        Platform.runLater(() -> {
            // Navbar
            if (searchBtn     != null) addButtonHover(searchBtn,     "rgba(26,115,232,0.35)");
            if (locateMeBtn   != null) addButtonHover(locateMeBtn,   "rgba(26,115,232,0.30)");
            if (predictBtn    != null) addButtonHover(predictBtn,    "rgba(124,58,237,0.35)");
            if (aqiMapBtn     != null) addButtonHover(aqiMapBtn,     "rgba(30,132,73,0.35)");
            if (myProfileBtn  != null) addButtonHover(myProfileBtn,  "rgba(26,115,232,0.30)");
            if (logoutBtn     != null) addButtonHover(logoutBtn,     "rgba(220,38,38,0.28)");
            if (setAlertBtn   != null) addButtonHover(setAlertBtn,   "rgba(245,158,11,0.35)");
            // Tab content
            if (exportPdfBtn        != null) addButtonHover(exportPdfBtn,        "rgba(26,115,232,0.32)");
            if (sendFeedbackBtn     != null) addButtonHover(sendFeedbackBtn,     "rgba(26,115,232,0.35)");
            if (refreshPollutantsBtn!= null) addButtonHover(refreshPollutantsBtn,"rgba(26,115,232,0.30)");
            if (refreshCitiesBtn    != null) addButtonHover(refreshCitiesBtn,    "rgba(26,115,232,0.30)");
            if (sortCitiesBtn       != null) addButtonHover(sortCitiesBtn,       "rgba(26,115,232,0.30)");
        });

        startAutoRefresh();
        loadAQIData("Kochi", 0, 0);
        loadIndiaCities();
    }

    // ─────────────────────────────────────────────────────────────
    // NAVIGATION
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleOpenPrediction() {
        if (guardGuest()) return;
        SceneManager.switchScene("/com/example/test2/main_view.fxml", "AQI Prediction");
    }

    @FXML private void handleLogout() {
        stopAutoRefresh();
        UserSession.clear();
        SceneManager.switchScene("/fxml/Login.fxml", "Login");
    }

    @FXML private void handleViewProfile() {
        if (guardGuest()) return;
        SceneManager.switchScene("/views/ViewProfile.fxml", "Your Profile");
    }

    /** Redirects guest users to sign-up. Returns true if guest (caller should return). */
    private boolean guardGuest() {
        if (!UserSession.isGuest()) return false;
        UserSession.setGuestReturnToDashboard(true);
        SceneManager.switchScene("/fxml/SignUp.fxml", "Create Account — AiQI");
        return true;
    }

    @FXML private void handleAbout() {
        SceneManager.switchScene("/fxml/About.fxml", "About Us");
    }

    // ─────────────────────────────────────────────────────────────
    // CAPSULE TAB SWITCHER
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleTabSwitch(javafx.event.ActionEvent event) {
        if (tabBtns == null) return;
        Button clicked = (Button) event.getSource();
        for (int i = 0; i < tabBtns.length; i++) {
            if (tabBtns[i] == clicked) {
                animateTabButton(clicked);
                switchToTabAnimated(i);
                return;
            }
        }
    }

    private void switchToTab(int index) {
        if (tabBtns == null || index >= tabBtns.length) return;
        Platform.runLater(() -> switchToTabAnimated(index));
    }

    private void switchToTabAnimated(int newIndex) {
        if (tabBtns == null) return;
        // Find currently visible tab
        VBox outgoing = null;
        for (int i = 0; i < tabContents.length; i++) {
            if (tabContents[i].isVisible()) { outgoing = tabContents[i]; break; }
        }
        final VBox incoming = tabContents[newIndex];
        if (incoming == outgoing) return;

        // Update button styles with a small scale-pop on the active button
        for (int i = 0; i < tabBtns.length; i++) {
            tabBtns[i].setStyle(i == newIndex ? TAB_ACTIVE : TAB_INACTIVE);
        }

        // Fade + translate out
        if (outgoing != null) {
            final VBox out = outgoing;
            FadeTransition fadeOut = new FadeTransition(Duration.millis(120), out);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            TranslateTransition slideOut = new TranslateTransition(Duration.millis(120), out);
            slideOut.setFromX(0);
            slideOut.setToX(-18);
            ParallelTransition exitAnim = new ParallelTransition(fadeOut, slideOut);
            exitAnim.setOnFinished(e -> {
                out.setVisible(false);
                out.setManaged(false);
                out.setTranslateX(0);
                out.setOpacity(1.0);
                // Fade + translate in
                incoming.setOpacity(0);
                incoming.setTranslateX(22);
                incoming.setVisible(true);
                incoming.setManaged(true);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(180), incoming);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                TranslateTransition slideIn = new TranslateTransition(Duration.millis(180), incoming);
                slideIn.setFromX(22);
                slideIn.setToX(0);
                slideIn.setInterpolator(Interpolator.EASE_OUT);
                ParallelTransition enterAnim = new ParallelTransition(fadeIn, slideIn);
                enterAnim.setOnFinished(ef -> onTabFullyVisible(newIndex));
                enterAnim.play();
            });
            exitAnim.play();
        } else {
            incoming.setOpacity(0);
            incoming.setTranslateX(22);
            incoming.setVisible(true);
            incoming.setManaged(true);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), incoming);
            fadeIn.setFromValue(0.0); fadeIn.setToValue(1.0);
            TranslateTransition slideIn = new TranslateTransition(Duration.millis(200), incoming);
            slideIn.setFromX(22); slideIn.setToX(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);
            ParallelTransition enterAnim2 = new ParallelTransition(fadeIn, slideIn);
            enterAnim2.setOnFinished(ef -> onTabFullyVisible(newIndex));
            enterAnim2.play();
        }
    }

    /** Called once the tab slide-in animation completes. Triggers first-open animations. */
    private void onTabFullyVisible(int tabIndex) {
        if (tabVisited[tabIndex]) return;
        tabVisited[tabIndex] = true;
        if (tabIndex == 2 && lastAqiData != null) {
            // Pollutants tab — rebuild cards so sceneProperty fires fresh
            pollutantsGrid.getChildren().clear();
            updatePollutantsTab();
        }
    }

    private void animateTabButton(Button btn) {
        ScaleTransition pop = new ScaleTransition(Duration.millis(80), btn);
        pop.setFromX(1.0); pop.setToX(0.92);
        pop.setFromY(1.0); pop.setToY(0.92);
        pop.setAutoReverse(true);
        pop.setCycleCount(2);
        pop.play();
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
        addButtonHover(weatherToggleBtn, "rgba(26,115,232,0.28)");
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
        addButtonHover(aqiToggleBtn, "rgba(26,115,232,0.28)");
        if (lastAqiData != null) updateWeatherView(lastAqiData);
    }

    // ─────────────────────────────────────────────────────────────
    // DARK MODE
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleDarkMode() {
        isDarkMode = !isDarkMode;
        darkModeBtn.setText(isDarkMode ? "☀  Light" : "🌙  Dark");

        // Button style feedback
        darkModeBtn.setStyle(isDarkMode
                ? "-fx-background-color: #252838; -fx-text-fill: #e2e8f0; -fx-background-radius: 12;" +
                "-fx-border-color: #3a3f55; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 7 16; -fx-cursor: hand;"
                : "-fx-background-color: #f0f4f8; -fx-text-fill: #1a1a2e; -fx-background-radius: 12;" +
                "-fx-border-color: #cbd5e1; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 7 16; -fx-cursor: hand;");

        Platform.runLater(() -> {
            if (darkModeBtn.getScene() == null) return;
            var stylesheets = darkModeBtn.getScene().getStylesheets();
            var url = getClass().getResource("/css/dark-theme.css");
            if (url != null) {
                String darkCss = url.toExternalForm();
                if (isDarkMode) { if (!stylesheets.contains(darkCss)) stylesheets.add(darkCss); }
                else            { stylesheets.remove(darkCss); }
            }
            applyDarkModeInlineStyles(isDarkMode);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // DARK MODE — override inline styles CSS can't reach
    // ─────────────────────────────────────────────────────────────
    private void applyDarkModeInlineStyles(boolean dark) {
        // Colours
        String pageBg      = dark ? "#13151f" : "#f0f4f8";
        String cardBg      = dark ? "#1e2130" : "white";
        String cardBgAlt   = dark ? "#252838" : "#f4f7ff";
        String pillBg      = dark ? "#252838" : "#e2e8f0";
        String tileBg      = dark ? "rgba(255,255,255,0.06)" : "rgba(0,0,0,0.07)";
        String tileText    = dark ? "#e2e8f0"  : "#1a1a2e";
        String labelMuted  = dark ? "#94a3b8"  : "#666";
        String cardShadow  = dark
                ? "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 20, 0, 0, 4);"
                : "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 20, 0, 0, 4);";
        String navBg       = dark ? "#1a1d2e"  : "white";
        String navBorder   = dark ? "-fx-border-color: #2a2d3e; -fx-border-width: 0 0 1 0;" : "";
        String forecastBg  = dark ? "#1e2130"  : "white";
        String tabBarBg    = dark ? "#1a1d2e"  : "#f0f4f8";
        String pillInner   = dark ? "#1e2130"  : "#e2e8f0";
        String separatorC  = dark ? "#2e3347"  : "rgba(0,0,0,0.15)";

        // ── Navbar
        if (darkModeBtn.getParent() != null && darkModeBtn.getParent().getParent() instanceof javafx.scene.Node nav) {
            if (nav instanceof javafx.scene.layout.HBox navbar) {
                navbar.setStyle("-fx-background-color: " + navBg + "; -fx-padding: 14 30; " +
                        "-fx-alignment: CENTER_LEFT; -fx-spacing: 16; " +
                        (dark ? "-fx-border-color: #2a2d3e; -fx-border-width: 0 0 1 0;" : ""));
            }
        }

        // ── Tab switcher bar + pill container
        if (tabBtns != null && tabBtns[0] != null) {
            var pillHBox = tabBtns[0].getParent(); // inner HBox (pill)
            if (pillHBox != null) {
                pillHBox.setStyle("-fx-background-color: " + pillInner + "; -fx-background-radius: 26; " +
                        "-fx-padding: 5; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 8, 0, 0, 2);");
                var tabBar = pillHBox.getParent(); // outer HBox centering it
                if (tabBar instanceof javafx.scene.layout.HBox hb)
                    hb.setStyle("-fx-padding: 14 0 8 0; -fx-background-color: " + tabBarBg + ";");
            }
            // Re-style inactive tab buttons
            for (int i = 0; i < tabBtns.length; i++) {
                if (!tabBtns[i].getStyle().contains("#1a73e8")) {
                    tabBtns[i].setStyle(
                            "-fx-background-radius: 22; -fx-background-color: transparent; " +
                                    "-fx-text-fill: " + (dark ? "#94a3b8" : "#555") + "; " +
                                    "-fx-font-size: 13px; -fx-padding: 9 22; -fx-cursor: hand;");
                }
            }
        }

        // ── All tab content scroll backgrounds
        for (VBox tc : tabContents) {
            if (tc == null) continue;
            // ScrollPane > VBox (the page bg)
            if (tc.getChildren().size() > 0 && tc.getChildren().get(0) instanceof javafx.scene.control.ScrollPane sp) {
                sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
                if (sp.getContent() instanceof javafx.scene.layout.VBox vb) {
                    vb.setStyle("-fx-padding: 20; -fx-background-color: " + pageBg + ";");
                    // Inner white cards
                    styleChildCards(vb, dark, cardBg, cardBgAlt, tileBg, tileText, labelMuted, cardShadow, separatorC);
                }
            }
        }

        // ── AQI/Weather toggle pill bar
        if (aqiToggleBtn != null && aqiToggleBtn.getParent() instanceof javafx.scene.layout.HBox togglePill) {
            togglePill.setStyle("-fx-background-color: " + pillInner + "; -fx-background-radius: 25; " +
                    "-fx-padding: 4; -fx-max-width: 340; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.10), 6, 0, 0, 1);");
            // Inactive toggle button
            weatherToggleBtn.setStyle("-fx-background-radius: 22; -fx-background-color: transparent; " +
                    "-fx-text-fill: " + (dark ? "#94a3b8" : "#666") + "; -fx-padding: 8 36; -fx-cursor: hand;");
        }

        // ── Forecast chart chart-plot background via CSS class — handled by CSS
        // But re-style the container card
        if (forecastChartContainer != null)
            forecastChartContainer.setStyle("-fx-background-color: " + forecastBg + "; -fx-background-radius: 20; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0," + (dark?"0.35":"0.08") + "), 20, 0, 0, 4); -fx-padding: 28 35;");

        // ── Chart text colours (for current live chart)
        if (forecastChart != null) {
            forecastChart.lookupAll(".chart-legend-item").forEach(n ->
                    n.setStyle("-fx-text-fill: " + (dark?"#cbd5e1":"#111") + "; -fx-font-size: 13px; -fx-font-weight: bold;"));
            forecastChart.lookupAll("Text").forEach(n ->
                    n.setStyle("-fx-fill: " + (dark?"#94a3b8":"#111") + "; -fx-font-size: 11px;"));
        }

        // ── Weather card overlay
        if (weatherCardOverlay != null)
            weatherCardOverlay.setStyle("-fx-background-color: " +
                    (dark ? "rgba(10,12,20,0.50)" : "rgba(255,255,255,0.42)") +
                    "; -fx-background-radius: 20;");
    }

    /** Recursively style white card VBoxes and their stat tiles */
    private void styleChildCards(javafx.scene.layout.VBox parent, boolean dark,
                                 String cardBg, String cardBgAlt, String tileBg,
                                 String tileText, String labelMuted, String cardShadow, String separatorC) {

        String cardStyle = "-fx-background-color: " + cardBg + "; -fx-background-radius: 20; " + cardShadow + " -fx-padding: 28 35;";
        String tileStyle = tileBg + "; -fx-padding: 14 24; -fx-background-radius: 16;";
        // "rgba..." bg style for the AQI badge tiles
        String inlineTile = "-fx-background-color: " + tileBg + "; -fx-padding: 14 24; -fx-background-radius: 16;";

        for (javafx.scene.Node child : parent.getChildren()) {
            if (child instanceof javafx.scene.layout.VBox vb) {
                String s = vb.getStyle();
                // Top-level white cards
                if (s != null && (s.contains("white") || s.contains("#ffffff") || s.contains("background-color: white"))) {
                    vb.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 20; " +
                            cardShadow + " -fx-padding: 28 35;");
                }
                // Stat tiles with semi-transparent bg
                if (s != null && s.contains("rgba(0,0,0,0.07)")) {
                    vb.setStyle(inlineTile);
                }
                // f0f4ff mini-tiles in weather grid
                if (s != null && (s.contains("#f0f4ff") || s.contains("#fff8e1") || s.contains("#fff3e0"))) {
                    vb.setStyle("-fx-background-color: " + cardBgAlt + "; -fx-padding: 12 18; -fx-background-radius: 12;");
                }
                styleChildCards(vb, dark, cardBg, cardBgAlt, tileBg, tileText, labelMuted, cardShadow, separatorC);
            }
            if (child instanceof javafx.scene.layout.HBox hb) {
                String s = hb.getStyle();
                // #e4e9f0 sort/filter bar
                if (s != null && (s.contains("#e4e9f0") || s.contains("#e2e8f0"))) {
                    hb.setStyle("-fx-background-color: " + (dark?"#252838":"#e4e9f0") +
                            "; -fx-background-radius: 25; -fx-padding: 4; -fx-max-width: 340; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 1);");
                }
                styleChildCards2(hb, dark, cardBgAlt, tileBg);
            }
            if (child instanceof javafx.scene.control.Separator sep) {
                sep.setStyle("-fx-background-color: " + separatorC + ";");
            }
            if (child instanceof javafx.scene.control.Label lbl) {
                String s = lbl.getStyle();
                if (s != null && (s.contains("#666") || s.contains("#555") || s.contains("#888"))) {
                    lbl.setStyle(s.replaceAll("#666|#555|#888", dark ? "#94a3b8" : "#666"));
                }
                if (s != null && s.contains("#1a1a2e")) {
                    lbl.setStyle(s.replace("#1a1a2e", dark ? "#e2e8f0" : "#1a1a2e"));
                }
            }
        }
    }

    private void styleChildCards2(javafx.scene.layout.HBox parent, boolean dark, String cardBgAlt, String tileBg) {
        for (javafx.scene.Node child : parent.getChildren()) {
            if (child instanceof javafx.scene.layout.VBox vb) {
                String s = vb.getStyle();
                if (s != null && (s.contains("#f0f4ff") || s.contains("#fff8e1") || s.contains("#fff3e0"))) {
                    vb.setStyle("-fx-background-color: " + cardBgAlt + "; -fx-padding: 12 18; -fx-background-radius: 12;");
                }
                if (s != null && s.contains("rgba(0,0,0,0.06)")) {
                    vb.setStyle("-fx-background-color: " + tileBg + "; -fx-padding: 14 24; -fx-background-radius: 16;");
                }
                if (s != null && s.contains("rgba(0,0,0,0.07)")) {
                    vb.setStyle("-fx-background-color: " + tileBg + "; -fx-padding: 14 24; -fx-background-radius: 16;");
                }
            }
            if (child instanceof javafx.scene.layout.HBox hb)
                styleChildCards2(hb, dark, cardBgAlt, tileBg);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // AQI ALERT
    // ─────────────────────────────────────────────────────────────
    @FXML private void handleSetAlert() {
        if (guardGuest()) return;
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
        if (guardGuest()) return;
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
        if (guardGuest()) return;
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
            animateCounter(aqiLabel,    0, aqi,    1,    "%d");
            animateDouble(pm25Label,    0, pm25,   "%.1f µg/m³");
            animateDouble(pm10Label,    0, pm10,   "%.1f µg/m³");
            animateDouble(tempLabel,    0, temp,   "%.1f°C");
            animateDouble(humidityLabel,0, humidity,"%.0f%%");
            animateDouble(windLabel,    0, wind,   "%.1f km/h");
            updateNeedle(aqi);
            updateRisk(aqi);
            updateLastRefreshed();
            updatePollutantsTab();
            applyRiskGradient(aqi);
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
        animateDouble(weatherTempLabel,    0, temp,      "%.0f°C");
        animateDoublePrefix(weatherFeelsLabel, 0, feelsLike, "Feels like %.0f°C");
        animateDouble(wHumidityLabel,      0, humidity,  "%.0f%%");
        animateDouble(wWindSpeedLabel,     0, windSpeed, "%.1f km/h");
        wWindDirLabel.setText(windDir);
        animateDouble(wVisibilityLabel,    0, visibility,"%.1f km");
        animateDouble(wPressureLabel,      0, pressure,  "%.0f hPa");
        animateDouble(wCloudsLabel,        0, clouds,    "%.0f%%");
        wSunriseLabel.setText(sunriseStr);
        wSunsetLabel.setText(sunsetStr);
        animateDouble(wMinLabel,           0, tempMin,   "%.0f°C");
        animateDouble(wMaxLabel,           0, tempMax,   "%.0f°C");

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

        // Set weather background image via ImageView (JavaFX doesn't support SVG/CSS bg)
        String bgFile = getWeatherImageFile(iconCode, desc);
        var bgStream = getClass().getResourceAsStream("/images/" + bgFile);
        if (bgStream != null) {
            try {
                javafx.scene.image.Image bgImg = new javafx.scene.image.Image(bgStream);
                crossfadeWeatherBg(bgImg);
                Platform.runLater(() -> {
                    weatherBgImage.setFitWidth(weatherCardStack.getWidth() > 0
                            ? weatherCardStack.getWidth() : 800);
                    weatherBgImage.setFitHeight(weatherCardStack.getHeight() > 0
                            ? weatherCardStack.getHeight() : 300);
                    weatherCardStack.widthProperty().addListener((obs, o, n) ->
                            weatherBgImage.setFitWidth(n.doubleValue()));
                    weatherCardStack.heightProperty().addListener((obs, o, n) ->
                            weatherBgImage.setFitHeight(n.doubleValue()));
                });
            } catch (Exception e) {
                System.out.println("Could not load weather bg: " + bgFile);
                applyFallbackBg(iconCode);
            }
        } else {
            applyFallbackBg(iconCode);
        }
    }

    private void crossfadeWeatherBg(javafx.scene.image.Image newImg) {
        Platform.runLater(() -> {
            // Fade out old image
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), weatherBgImage);
            fadeOut.setFromValue(weatherBgImage.getOpacity());
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> {
                weatherBgImage.setImage(newImg);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(400), weatherBgImage);
                fadeIn.setFromValue(0); fadeIn.setToValue(1);
                fadeIn.setInterpolator(Interpolator.EASE_IN);
                fadeIn.play();
            });
            fadeOut.play();
        });
    }

    private void applyFallbackBg(String iconCode) {
        String grad = getWeatherFallbackGradient(iconCode);
        Platform.runLater(() -> {
            weatherBgImage.setImage(null);
            weatherCardStack.setStyle("-fx-background-color: " + grad +
                    "; -fx-background-radius: 20; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 20, 0, 0, 4);");
        });
    }

    private String getWeatherImageFile(String iconCode, String desc) {
        if (iconCode == null || iconCode.isEmpty()) return "broken_clouds.png";
        String id = iconCode.substring(0, Math.min(2, iconCode.length()));
        return switch (id) {
            case "01" -> "clear.png";
            case "02", "03" -> "few_clouds.png";
            case "04" -> "broken_clouds.png";
            case "09" -> "drizzle.png";
            case "10" -> "rain.png";
            case "11" -> "thunderstorm.png";
            case "13" -> "snow.png";
            case "50" -> "mist.png";
            default   -> {
                String d = desc.toLowerCase();
                if (d.contains("clear"))       yield "clear.png";
                if (d.contains("cloud"))       yield "broken_clouds.png";
                if (d.contains("rain"))        yield "rain.png";
                if (d.contains("drizzle"))     yield "drizzle.png";
                if (d.contains("thunder"))     yield "thunderstorm.png";
                if (d.contains("snow"))        yield "snow.png";
                if (d.contains("mist") || d.contains("fog") || d.contains("haze")) yield "mist.png";
                yield "broken_clouds.png";
            }
        };
    }

    private String getWeatherFallbackGradient(String iconCode) {
        if (iconCode == null) return "linear-gradient(to bottom right, #e0eafc, #cfdef3)";
        String id = iconCode.substring(0, Math.min(2, iconCode.length()));
        return switch (id) {
            case "01" -> "linear-gradient(to bottom right, #ffecd2, #fcb69f)"; // sunny warm
            case "02", "03" -> "linear-gradient(to bottom right, #d4e9ff, #b8d4f5)"; // partly cloudy
            case "04" -> "linear-gradient(to bottom right, #c9d6df, #eef2f3)"; // overcast
            case "09", "10" -> "linear-gradient(to bottom right, #4b6cb7, #182848)"; // rain dark blue
            case "11" -> "linear-gradient(to bottom right, #373b44, #4286f4)"; // thunderstorm
            case "13" -> "linear-gradient(to bottom right, #e8f4f8, #d7e8f0)"; // snow pale blue
            case "50" -> "linear-gradient(to bottom right, #bdc3c7, #e8ecef)"; // mist grey
            default   -> "linear-gradient(to bottom right, #e0eafc, #cfdef3)";
        };
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

        int step = 1;
        String interval = forecastIntervalBox.getValue();
        if (interval != null) {
            if (interval.contains("6"))  step = 2;
            if (interval.contains("12")) step = 4;
            if (interval.contains("24")) step = 8;
        }
        System.out.println("[Chart] interval=" + interval + " step=" + step);

        // Build filtered data lists
        List<String>  labels  = new ArrayList<>();
        List<Integer> aqiVals = new ArrayList<>();
        List<Double>  tmpVals = new ArrayList<>();
        int count = 0;
        for (JsonNode entry : lastForecastData.path("entries")) {
            if (count % step == 0) {
                labels.add(formatForecastLabel(entry.path("dtTxt").asText()));
                aqiVals.add(entry.path("aqi").asInt());
                tmpVals.add(entry.path("temp").asDouble());
            }
            count++;
        }

        // No thinning — show exactly what the interval selects.
        // 3hr=40pts, 6hr=20pts, 12hr=10pts, 24hr=5pts. Labels are rotated so they fit.

        final List<String>  fLabels  = labels;
        final List<Integer> fAqi     = aqiVals;
        final List<Double>  fTmp     = tmpVals;

        // ALL chart work must happen on FX thread
        Platform.runLater(() -> {
            // Build new series
            XYChart.Series<String, Number> aqiSeries  = new XYChart.Series<>();
            XYChart.Series<String, Number> tempSeries = new XYChart.Series<>();
            aqiSeries.setName("AQI");
            tempSeries.setName("Temp (°C)");
            for (int i = 0; i < fLabels.size(); i++) {
                aqiSeries.getData().add(new XYChart.Data<>(fLabels.get(i), fAqi.get(i)));
                tempSeries.getData().add(new XYChart.Data<>(fLabels.get(i), fTmp.get(i)));
            }

            // Build brand-new chart + axes — eliminates all stale CategoryAxis state
            CategoryAxis newX = new CategoryAxis();
            NumberAxis   newY = new NumberAxis();
            // Rotate more steeply for 3hr (40 labels) so they never overlap
            int labelCount = fLabels.size();
            // Same bottom-to-top angle for all intervals, smaller font for dense 3hr view
            double fontSize = labelCount > 20 ? 9 : labelCount > 10 ? 10 : 11;
            newX.setTickLabelRotation(-40);
            newX.setTickLabelGap(2);
            newX.setStyle("-fx-tick-label-fill: #111; -fx-font-size: " + fontSize + "px;");
            newY.setAutoRanging(true);
            newY.setStyle("-fx-tick-label-fill: #111; -fx-font-size: 11px;");

            LineChart<String, Number> newChart = new LineChart<>(newX, newY);
            newChart.setPrefHeight(labelCount > 20 ? 440 : 380);
            newChart.setAnimated(false);
            newChart.setCreateSymbols(true);
            newChart.setStyle("-fx-background-color: transparent; -fx-padding: 10 0 0 0;");

            // Empty series — points added live by animation below
            XYChart.Series<String, Number> liveAqi  = new XYChart.Series<>();
            XYChart.Series<String, Number> liveTmp  = new XYChart.Series<>();
            liveAqi.setName("AQI");
            liveTmp.setName("Temp (°C)");
            newChart.getData().addAll(liveAqi, liveTmp);

            // Swap with fade
            FadeTransition fadeOut = new FadeTransition(Duration.millis(100), forecastChartContainer);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(ev -> {
                javafx.collections.ObservableList<javafx.scene.Node> kids = forecastChartContainer.getChildren();
                boolean swapped = false;
                for (int i = 0; i < kids.size(); i++) {
                    if (kids.get(i) instanceof LineChart) { kids.set(i, newChart); swapped = true; break; }
                }
                if (!swapped) kids.add(1, newChart);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(180), forecastChartContainer);
                fadeIn.setFromValue(0.0); fadeIn.setToValue(1.0);
                fadeIn.setInterpolator(Interpolator.EASE_IN);
                fadeIn.play();
            });
            fadeOut.play();

            // Update references
            forecastChart = newChart;
            forecastXAxis = newX;
            forecastYAxis = newY;

            // Style line colours + legend once chart is in scene
            Platform.runLater(() -> {
                if (liveAqi.getNode() != null)
                    liveAqi.getNode().setStyle("-fx-stroke: #1a73e8; -fx-stroke-width: 2.5px;");
                if (liveTmp.getNode() != null)
                    liveTmp.getNode().setStyle("-fx-stroke: #f59e0b; -fx-stroke-width: 2.5px;");
                newChart.lookupAll(".chart-legend-item").forEach(n ->
                        n.setStyle("-fx-text-fill: #111; -fx-font-size: 13px; -fx-font-weight: bold;"));
                newChart.lookupAll("Text").forEach(n ->
                        n.setStyle("-fx-fill: #111; -fx-font-size: 11px;"));

                // ── LIVE DRAW ANIMATION ──────────────────────────────────
                // Each tick adds one point to both series so the line
                // literally extends right in front of the user.
                String dotStyleAqi = "-fx-background-color: #1a73e8, white; " +
                        "-fx-background-insets: 0, 2; -fx-background-radius: 5px; " +
                        "-fx-padding: " + (labelCount > 20 ? "5px" : "7px") + ";";
                String dotStyleTmp = "-fx-background-color: #f59e0b, white; " +
                        "-fx-background-insets: 0, 2; -fx-background-radius: 5px; " +
                        "-fx-padding: " + (labelCount > 20 ? "5px" : "7px") + ";";

                // Total draw duration: 900ms spread across all points
                double msPerPoint = Math.max(18, 900.0 / Math.max(fLabels.size(), 1));
                List<KeyFrame> frames = new ArrayList<>();

                for (int pi = 0; pi < fLabels.size(); pi++) {
                    final int idx = pi;
                    frames.add(new KeyFrame(Duration.millis(idx * msPerPoint), kfEv -> {
                        // Add the next point to both series
                        XYChart.Data<String, Number> dAqi = new XYChart.Data<>(fLabels.get(idx), fAqi.get(idx));
                        XYChart.Data<String, Number> dTmp = new XYChart.Data<>(fLabels.get(idx), fTmp.get(idx));
                        liveAqi.getData().add(dAqi);
                        liveTmp.getData().add(dTmp);

                        // Re-apply line stroke (JavaFX resets it on data change)
                        if (liveAqi.getNode() != null)
                            liveAqi.getNode().setStyle("-fx-stroke: #1a73e8; -fx-stroke-width: 2.5px;");
                        if (liveTmp.getNode() != null)
                            liveTmp.getNode().setStyle("-fx-stroke: #f59e0b; -fx-stroke-width: 2.5px;");

                        // Style + pop-in the dot that just appeared
                        Platform.runLater(() -> {
                            if (dAqi.getNode() != null) {
                                dAqi.getNode().setStyle(dotStyleAqi);
                                dAqi.getNode().setScaleX(0); dAqi.getNode().setScaleY(0);
                                ScaleTransition st = new ScaleTransition(Duration.millis(160), dAqi.getNode());
                                st.setFromX(0); st.setToX(1);
                                st.setFromY(0); st.setToY(1);
                                st.setInterpolator(Interpolator.EASE_OUT);
                                st.play();
                            }
                            if (dTmp.getNode() != null) {
                                dTmp.getNode().setStyle(dotStyleTmp);
                                dTmp.getNode().setScaleX(0); dTmp.getNode().setScaleY(0);
                                ScaleTransition st = new ScaleTransition(Duration.millis(160), dTmp.getNode());
                                st.setFromX(0); st.setToX(1);
                                st.setFromY(0); st.setToY(1);
                                st.setInterpolator(Interpolator.EASE_OUT);
                                st.play();
                            }
                        });
                    }));
                }
                Timeline drawTimeline = new Timeline(frames.toArray(new KeyFrame[0]));
                drawTimeline.play();

                // Daily summary cards — must be on FX thread
                dailySummaryBox.getChildren().clear();
                final int[] dayIdx = {0};
                lastForecastData.path("dailySummary").fields().forEachRemaining(e -> {
                    JsonNode d = e.getValue();
                    VBox card = buildDaySummaryCard(
                            e.getKey(),
                            d.path("minAqi").asInt(), d.path("maxAqi").asInt(),
                            d.path("minTemp").asDouble(), d.path("maxTemp").asDouble());
                    card.setOpacity(0);
                    card.setTranslateY(16);
                    dailySummaryBox.getChildren().add(card);
                    int di = dayIdx[0]++;
                    PauseTransition p = new PauseTransition(Duration.millis(di * 80.0 + 300));
                    p.setOnFinished(ev2 -> {
                        FadeTransition f = new FadeTransition(Duration.millis(260), card);
                        f.setFromValue(0); f.setToValue(1);
                        TranslateTransition t2 = new TranslateTransition(Duration.millis(260), card);
                        t2.setFromY(16); t2.setToY(0);
                        t2.setInterpolator(Interpolator.EASE_OUT);
                        new ParallelTransition(f, t2).play();
                    });
                    p.play();
                });
                // Re-apply dark theme to newly drawn chart nodes
                if (isDarkMode) applyDarkModeInlineStyles(true);
            });
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

        String dayGlow = maxAqi <= 50  ? "rgba(46,125,50,0.35)"
                : maxAqi <= 100 ? "rgba(154,125,10,0.35)"
                : maxAqi <= 150 ? "rgba(202,111,30,0.35)"
                : maxAqi <= 200 ? "rgba(160,64,0,0.35)"
                : maxAqi <= 300 ? "rgba(125,60,152,0.35)"
                :                "rgba(146,43,33,0.35)";
        String dayResting = "-fx-background-color: white; -fx-background-radius: 14;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 10, 0, 0, 2);";
        addCardHover(card, dayGlow, dayResting);

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
            pollutantsGrid.add(buildPollutantCard(name, value, limit, pct, i), i % 2, i / 2);
        }
    }

    private VBox buildPollutantCard(String name, double value, double limit, double pct) {
        return buildPollutantCard(name, value, limit, pct, 0);
    }

    private VBox buildPollutantCard(String name, double value, double limit, double pct, int staggerIndex) {
        VBox card = new VBox(8);
        card.setPrefWidth(380);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 10, 0, 0, 2);");

        // Slide-in from below
        card.setOpacity(0);
        card.setTranslateY(18);

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

        ProgressBar bar = new ProgressBar(0); // start at 0, animate to pct
        bar.setPrefWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-accent: " + progressColor(pct) + "; -fx-background-radius: 6;");

        Label whoLbl = new Label(String.format(
                "WHO safe limit: %.0f µg/m³  |  %.0f%% of limit", limit, pct * 100));
        whoLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        card.getChildren().addAll(header, bar, whoLbl);

        // Hover: color-matched glow based on pollution level
        String glowColor = pct <= 0.5
                ? "rgba(46,125,50,0.35)"
                : pct <= 0.8
                ? "rgba(245,158,11,0.38)"
                : "rgba(220,38,38,0.38)";
        String cardResting = "-fx-background-color: white; -fx-background-radius: 14;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 10, 0, 0, 2);";
        addCardHoverLift(card, glowColor, cardResting);

        // Staggered entrance + bar fill triggered once card enters the scene
        final double entranceDelay = staggerIndex * 70.0;
        final double targetPct = pct;
        card.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            PauseTransition wait = new PauseTransition(Duration.millis(entranceDelay + 30));
            wait.setOnFinished(ev -> {
                // Entrance: slide up + fade in
                FadeTransition fade = new FadeTransition(Duration.millis(260), card);
                fade.setFromValue(0); fade.setToValue(1);
                TranslateTransition slide = new TranslateTransition(Duration.millis(260), card);
                slide.setFromY(18); slide.setToY(0);
                slide.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(fade, slide).play();

                // Bar fill driven by SimpleDoubleProperty on FX thread
                javafx.beans.property.SimpleDoubleProperty fillProp =
                        new javafx.beans.property.SimpleDoubleProperty(0.0);
                fillProp.addListener((o2, ov, nv) ->
                        Platform.runLater(() -> bar.setProgress(nv.doubleValue())));
                new Timeline(
                        new KeyFrame(Duration.ZERO,
                                new javafx.animation.KeyValue(fillProp, 0.0)),
                        new KeyFrame(Duration.millis(750),
                                new javafx.animation.KeyValue(fillProp, targetPct, Interpolator.EASE_OUT))
                ).play();
            });
            wait.play();
        });

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

    @FXML private void handleRefreshPollutants() {
        if (lastAqiData == null) return;
        // Brief pulse on the button — no spin
        if (refreshPollutantsBtn != null) {
            ScaleTransition pulse = new ScaleTransition(Duration.millis(120), refreshPollutantsBtn);
            pulse.setToX(0.90); pulse.setToY(0.90);
            pulse.setAutoReverse(true); pulse.setCycleCount(2);
            pulse.play();
        }
        pollutantsGrid.getChildren().clear();
        updatePollutantsTab();
    }

    @FXML private void handleOpenAqiMap() {
        if (guardGuest()) return;
        // Open WAQI live map in browser — works with Desktop API
        try {
            String cityEnc = java.net.URLEncoder.encode(currentCity, "UTF-8");
            java.awt.Desktop.getDesktop().browse(
                    java.net.URI.create("https://waqi.info/#/search/" + cityEnc));
        } catch (Exception e) {
            showInfo("Could not open browser. Visit https://waqi.info to see the live AQI map.");
        }
    }

    @FXML private void handleSortCities() {
        sortWorstFirst = !sortWorstFirst;
        sortCitiesBtn.setText(sortWorstFirst ? "Sort: Worst First" : "Sort: Best First");
        flipAndRenderCities();
    }

    private void flipAndRenderCities() {
        // Phase 1: rotate grid to 90° (cards appear to fold away)
        javafx.beans.property.SimpleDoubleProperty rotY =
                new javafx.beans.property.SimpleDoubleProperty(0);
        javafx.scene.transform.Rotate flipRotate =
                new javafx.scene.transform.Rotate(0,
                        citiesGrid.getBoundsInLocal().getWidth() / 2, 0,
                        0, javafx.scene.transform.Rotate.Y_AXIS);
        citiesGrid.getTransforms().add(flipRotate);
        rotY.addListener((obs, o, n) -> flipRotate.setAngle(n.doubleValue()));

        Timeline foldOut = new Timeline(
                new KeyFrame(Duration.ZERO,        new javafx.animation.KeyValue(rotY, 0.0)),
                new KeyFrame(Duration.millis(200),  new javafx.animation.KeyValue(rotY, 90.0, Interpolator.EASE_IN))
        );
        foldOut.setOnFinished(e -> {
            // Swap content while invisible
            renderCitiesGrid();
            // Phase 2: rotate back from 90° → 0 (cards unfold)
            Timeline foldIn = new Timeline(
                    new KeyFrame(Duration.ZERO,        new javafx.animation.KeyValue(rotY, 90.0)),
                    new KeyFrame(Duration.millis(220),  new javafx.animation.KeyValue(rotY, 0.0, Interpolator.EASE_OUT))
            );
            foldIn.setOnFinished(ev -> citiesGrid.getTransforms().remove(flipRotate));
            foldIn.play();
        });
        foldOut.play();
    }

    private void renderCitiesGrid() {
        if (citiesData.isEmpty()) return;
        List<Map<String, Object>> sorted = new ArrayList<>(citiesData);
        sorted.sort((a, b) -> sortWorstFirst
                ? Integer.compare((int) b.get("aqi"), (int) a.get("aqi"))
                : Integer.compare((int) a.get("aqi"), (int) b.get("aqi")));
        citiesGrid.getChildren().clear();
        for (int i = 0; i < sorted.size(); i++)
            citiesGrid.add(buildCityCardAnimated(sorted.get(i), i), i % 4, i / 4);
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

        // Hover with AQI-matched gradient glow
        String glowRgb = switch (aqi / 50) {
            case 0 -> "rgba(30,132,73,0.38)";
            case 1 -> "rgba(154,125,10,0.38)";
            case 2 -> "rgba(202,111,30,0.38)";
            case 3 -> "rgba(160,64,0,0.38)";
            default -> aqi <= 300 ? "rgba(125,60,152,0.38)" : "rgba(146,43,33,0.38)";
        };
        String cityResting = "-fx-background-color: " + bg + "; -fx-background-radius: 14;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 2);";
        addCardHoverLift(card, glowRgb, cityResting);

        // Click → search that city and switch to AQI tab
        final String clickedCity = city;
        card.setOnMouseClicked(e -> {
            citySearchField.setText(clickedCity);
            selectedLat = 0; selectedLon = 0;
            loadAQIData(clickedCity, 0, 0);
            switchToTab(0); // jump to Tab 1 (AQI view)
        });

        return card;
    }

    private VBox buildCityCardAnimated(Map<String, Object> data, int index) {
        VBox card = buildCityCard(data);
        card.setOpacity(0);
        card.setTranslateY(24);
        PauseTransition pause = new PauseTransition(Duration.millis(index * 70.0));
        pause.setOnFinished(ev -> {
            FadeTransition fade = new FadeTransition(Duration.millis(280), card);
            fade.setFromValue(0); fade.setToValue(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(280), card);
            slide.setFromY(24); slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(fade, slide).play();
        });
        pause.play();
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
    // Single reused Rotate so needle angle is always continuous
    private final Rotate needleRotate = new Rotate(0, 190, 190);
    private Timeline needleSwing = null;

    private double aqiToAngle(int aqi) {
        if      (aqi <= 50)  return (aqi / 50.0) * 30;
        else if (aqi <= 100) return 30  + ((aqi - 50)  / 50.0)  * 30;
        else if (aqi <= 150) return 60  + ((aqi - 100) / 50.0)  * 30;
        else if (aqi <= 200) return 90  + ((aqi - 150) / 50.0)  * 30;
        else if (aqi <= 300) return 120 + ((aqi - 200) / 100.0) * 30;
        else                 return 150 + ((Math.min(aqi, 500) - 300) / 200.0) * 30;
    }

    private void updateNeedle(int aqi) {
        // Attach rotate once
        if (!needle.getTransforms().contains(needleRotate)) {
            needle.getTransforms().clear();
            needle.getTransforms().add(needleRotate);
        }
        double from = needleRotate.getAngle();
        double to   = aqiToAngle(aqi);

        if (needleSwing != null) needleSwing.stop();
        needleSwing = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new javafx.animation.KeyValue(needleRotate.angleProperty(), from)),
                new KeyFrame(Duration.millis(650),
                        new javafx.animation.KeyValue(needleRotate.angleProperty(),
                                to + (to > from ? 5 : -5), Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(900),
                        new javafx.animation.KeyValue(needleRotate.angleProperty(),
                                to, Interpolator.EASE_BOTH))
        );
        needleSwing.play();
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

        final String fHex = hexColor;
        final String fGrad = gradientStyle;
        final String fRisk = risk;
        Platform.runLater(() -> {
            // Status label bounce
            statusLabel.setText(fRisk);
            statusLabel.setStyle("-fx-text-fill: " + fHex +
                    "; -fx-font-weight: 900; -fx-font-size: 30px;");
            statusLabel.setScaleX(0.7); statusLabel.setScaleY(0.7);
            ScaleTransition bounce = new ScaleTransition(Duration.millis(300), statusLabel);
            bounce.setFromX(0.7); bounce.setToX(1.1);
            bounce.setFromY(0.7); bounce.setToY(1.1);
            bounce.setAutoReverse(true); bounce.setCycleCount(2);
            bounce.setInterpolator(Interpolator.EASE_OUT);
            bounce.play();

            aqiLabel.setStyle("-fx-text-fill: " + fHex +
                    "; -fx-font-weight: 900; -fx-font-size: 52px;");

            // Crossfade: fade card out, swap style, fade back in
            FadeTransition fadeOut = new FadeTransition(Duration.millis(180), mainCard);
            fadeOut.setFromValue(1.0); fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> {
                mainCard.setStyle("-fx-background-color: " + fGrad + "; " + baseStyle);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(300), mainCard);
                fadeIn.setFromValue(0.0); fadeIn.setToValue(1.0);
                fadeIn.setInterpolator(Interpolator.EASE_IN);
                fadeIn.play();
            });
            fadeOut.play();

            String darkStyle = "-fx-text-fill: #1a1a2e; -fx-font-weight: 900;";
            cityLabel.setStyle(darkStyle + " -fx-font-size: 28px;");
            pm25Label.setStyle(darkStyle + " -fx-font-size: 28px;");
            pm10Label.setStyle(darkStyle + " -fx-font-size: 28px;");
            tempLabel.setStyle(darkStyle  + " -fx-font-size: 26px;");
            humidityLabel.setStyle(darkStyle + " -fx-font-size: 26px;");
            windLabel.setStyle(darkStyle + " -fx-font-size: 26px;");
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
    // RISK GRADIENT — Forecast chart + Pollutants background
    // ─────────────────────────────────────────────────────────────
    private void applyRiskGradient(int aqi) {
        String bg1, bg2, chartBg;
        if (aqi <= 50) {
            bg1 = "#e8f8f0"; bg2 = "#d0f0e0";
            chartBg = "linear-gradient(to bottom, #f0fdf4, #dcfce7)";
        } else if (aqi <= 100) {
            bg1 = "#fefce8"; bg2 = "#fef08a";
            chartBg = "linear-gradient(to bottom, #fefce8, #fef9c3)";
        } else if (aqi <= 150) {
            bg1 = "#fff7ed"; bg2 = "#fed7aa";
            chartBg = "linear-gradient(to bottom, #fff7ed, #ffedd5)";
        } else if (aqi <= 200) {
            bg1 = "#fff1f0"; bg2 = "#fecaca";
            chartBg = "linear-gradient(to bottom, #fff1f0, #fee2e2)";
        } else if (aqi <= 300) {
            bg1 = "#faf5ff"; bg2 = "#e9d5ff";
            chartBg = "linear-gradient(to bottom, #faf5ff, #f3e8ff)";
        } else {
            bg1 = "#fff0f0"; bg2 = "#fca5a5";
            chartBg = "linear-gradient(to bottom, #fff0f0, #fee2e2)";
        }
        String panelStyle = "-fx-background-color: linear-gradient(to bottom right, " + bg1 + ", " + bg2 + "); " +
                "-fx-background-radius: 20; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 20, 0, 0, 4); -fx-padding: 28 35;";
        Platform.runLater(() -> {
            // Apply to forecast chart container (parent VBox inside tab2)
            if (forecastChart != null && forecastChart.getParent() instanceof VBox chartPanel) {
                chartPanel.setStyle(panelStyle);
            }
            // Apply to pollutants grid container
            if (pollutantsGrid != null && pollutantsGrid.getParent() instanceof StackPane sp
                    && sp.getParent() instanceof VBox pollPanel) {
                pollPanel.setStyle(panelStyle);
            }
        });
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
    // ─────────────────────────────────────────────────────────────
    // ANIMATION HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Animate an integer counter on a Label, e.g. AQI 0 → 142 */
    private void animateCounter(Label label, int from, int to, int decimals, String fmt) {
        Timeline t = new Timeline();
        javafx.beans.property.SimpleIntegerProperty prop =
                new javafx.beans.property.SimpleIntegerProperty(from);
        prop.addListener((obs, o, n) -> label.setText(String.format(fmt, n.intValue())));
        t.getKeyFrames().addAll(
                new KeyFrame(Duration.ZERO,        new javafx.animation.KeyValue(prop, from)),
                new KeyFrame(Duration.millis(700), new javafx.animation.KeyValue(prop, to, Interpolator.EASE_OUT))
        );
        t.play();
    }

    /** Animate a double value on a Label with a format string */
    private void animateDouble(Label label, double from, double to, String fmt) {
        javafx.beans.property.SimpleDoubleProperty prop =
                new javafx.beans.property.SimpleDoubleProperty(from);
        prop.addListener((obs, o, n) -> label.setText(String.format(fmt, n.doubleValue())));
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,        new javafx.animation.KeyValue(prop, from)),
                new KeyFrame(Duration.millis(700), new javafx.animation.KeyValue(prop, to, Interpolator.EASE_OUT))
        );
        t.play();
    }

    /** Animate a double value where the format string wraps the number (e.g. "Feels like 24°C") */
    private void animateDoublePrefix(Label label, double from, double to, String fmt) {
        animateDouble(label, from, to, fmt);
    }


    // ─────────────────────────────────────────────────────────────
    // HOVER ANIMATION HELPERS
    // ─────────────────────────────────────────────────────────────

    /**
     * Adds a dynamic gradient glow hover to any node (card, button, etc).
     * glowColor: e.g. "rgba(26,115,232,0.35)" for blue glow
     * baseStyle: the node's resting style (restored on exit)
     */
    private void addCardHover(javafx.scene.Node node, String glowColor, String restingStyle) {
        node.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(180), node);
            st.setToX(1.035); st.setToY(1.035);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
            node.setStyle(restingStyle +
                    " -fx-effect: dropshadow(gaussian, " + glowColor + ", 28, 0.4, 0, 4);");
            node.setCursor(javafx.scene.Cursor.HAND);
        });
        node.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(180), node);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
            node.setStyle(restingStyle);
        });
    }

    /** Card hover with a lift (translateY) effect instead of scale */
    private void addCardHoverLift(javafx.scene.Node node, String glowColor, String restingStyle) {
        node.setOnMouseEntered(e -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(160), node);
            tt.setToY(-5); tt.setInterpolator(Interpolator.EASE_OUT); tt.play();
            node.setStyle(restingStyle +
                    " -fx-effect: dropshadow(gaussian, " + glowColor + ", 32, 0.45, 0, 8);");
            node.setCursor(javafx.scene.Cursor.HAND);
        });
        node.setOnMouseExited(e -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(160), node);
            tt.setToY(0); tt.setInterpolator(Interpolator.EASE_OUT); tt.play();
            node.setStyle(restingStyle);
        });
    }

    /** Button hover: scale + dynamic glow */
    private void addButtonHover(javafx.scene.control.Button btn, String glowColor) {
        String base = btn.getStyle() != null ? btn.getStyle() : "";
        btn.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(130), btn);
            st.setToX(1.06); st.setToY(1.06);
            st.setInterpolator(Interpolator.EASE_OUT); st.play();
            btn.setStyle(base + " -fx-effect: dropshadow(gaussian, " + glowColor + ", 18, 0.5, 0, 2);");
            btn.setCursor(javafx.scene.Cursor.HAND);
        });
        btn.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(130), btn);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT); st.play();
            btn.setStyle(base);
        });
    }


}
