package com.example;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class AQIDashboard extends Application {

    @Override
    public void start(Stage primaryStage) {
        // --- Root Layout ---
        StackPane root = new StackPane();
        root.setId("root-pane");

        // --- 1. Background (Simulating the Dark Map) ---
        Pane mapBackground = new Pane();
        mapBackground.setId("map-background");
        // In a real app, use WebView with Google Maps or Leaflet here
        Label mapPlaceholder = new Label("Map View Area");
        mapPlaceholder.setStyle("-fx-text-fill: #555; -fx-font-size: 30;");
        mapPlaceholder.layoutXProperty().bind(root.widthProperty().divide(2).subtract(100));
        mapPlaceholder.layoutYProperty().bind(root.heightProperty().divide(2).subtract(200));
        mapBackground.getChildren().add(mapPlaceholder);

        // --- 2. Top Navigation Bar ---
        HBox topBar = createTopBar();

        // --- 3. Main Content Card ---
        VBox contentCard = createContentCard();

        // Layout Positioning
        VBox mainContainer = new VBox(20);
        mainContainer.getChildren().addAll(topBar, contentCard);
        mainContainer.setPadding(new Insets(0, 0, 40, 0));
        VBox.setVgrow(contentCard, Priority.ALWAYS);
        mainContainer.setAlignment(Pos.TOP_CENTER);

        // Add layers to root
        root.getChildren().addAll(mapBackground, mainContainer);

        // --- Scene Setup ---
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

        primaryStage.setTitle("Real-time AQI Dashboard");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(20);
        topBar.setId("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(15, 30, 15, 30));

        // Logo
        Label logo = new Label("AQI");
        logo.setId("logo-text");

        // Search Bar
        TextField searchField = new TextField();
        searchField.setPromptText("Search any Location, City...");
        searchField.setPrefWidth(300);
        searchField.setId("search-field");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Menu Items
        Label navRanking = new Label("Ranking ⌵");
        Label navProducts = new Label("Products ⌵");
        Label navResources = new Label("Resources ⌵");
        navRanking.getStyleClass().add("nav-item");
        navProducts.getStyleClass().add("nav-item");
        navResources.getStyleClass().add("nav-item");

        // Tools
        Label region = new Label("🇮🇳 English-IN ⌵");
        region.getStyleClass().add("nav-item");

        Button loginBtn = new Button("Login ↗");
        loginBtn.setId("login-btn");

        topBar.getChildren().addAll(logo, searchField, spacer, navRanking, navProducts, navResources, region, loginBtn);
        return topBar;
    }

    private VBox createContentCard() {
        VBox card = new VBox();
        card.setId("main-card");
        card.setMaxWidth(1000);
        card.setMaxHeight(500);

        // --- Card Header (Tabs) ---
        HBox tabs = new HBox(10);
        Button tabAqi = new Button("💨 AQI");
        tabAqi.setId("tab-active");
        Button tabWeather = new Button("☀ Weather");
        tabWeather.setId("tab-inactive");
        tabs.getChildren().addAll(tabAqi, tabWeather);

        // --- Location Info ---
        HBox locationHeader = new HBox();
        locationHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(5);
        Label title = new Label("Real-time Air Quality Index (AQI)");
        title.setId("card-title");
        Label locationName = new Label("Kochi, Kerala, India");
        locationName.setId("location-name");
        Label timestamp = new Label("Last Updated: 2026-02-16 07:31:00 (Local Time)");
        timestamp.setId("timestamp");
        titleBox.getChildren().addAll(title, locationName, timestamp);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button locateBtn = new Button("⌖ Locate me");
        locateBtn.setId("locate-btn");

        locationHeader.getChildren().addAll(titleBox, spacer, locateBtn);

        // --- Dashboard Content Split (Left: AQI, Right: Weather) ---
        HBox dashboardData = new HBox(40);
        dashboardData.setPadding(new Insets(30, 0, 0, 0));

        // LEFT SIDE: AQI
        VBox leftSection = new VBox(15);
        leftSection.setPrefWidth(600);

        HBox aqiValueBox = new HBox(20);
        aqiValueBox.setAlignment(Pos.CENTER_LEFT);

        Label liveIndicator = new Label("● Live AQI");
        liveIndicator.setStyle("-fx-text-fill: #ff5252; -fx-font-weight: bold;");

        // The Big Number
        Label aqiNum = new Label("181");
        aqiNum.setId("aqi-number");
        Label aqiUS = new Label("AQI (US)");
        aqiUS.setStyle("-fx-text-fill: #888;");

        // The Status Box
        VBox statusBox = new VBox(5);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setId("status-box-unhealthy");
        Label statusLabel = new Label("Air Quality is");
        statusLabel.setStyle("-fx-text-fill: #ddd; -fx-font-size: 10px;");
        Label statusValue = new Label("Unhealthy");
        statusValue.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");
        statusBox.getChildren().addAll(statusLabel, statusValue);

        aqiValueBox.getChildren().addAll(aqiNum, aqiUS, statusBox);

        // PM details
        HBox pmBox = new HBox(30);
        Label pm25 = new Label("PM2.5 : 105 µg/m³");
        Label pm10 = new Label("PM10 : 122 µg/m³");
        pm25.getStyleClass().add("pm-text");
        pm10.getStyleClass().add("pm-text");
        pmBox.getChildren().addAll(pm25, pm10);

        // The Gradient Slider Bar
        VBox sliderContainer = createGradientSlider();

        leftSection.getChildren().addAll(liveIndicator, aqiValueBox, pmBox, sliderContainer);

        // RIGHT SIDE: WEATHER
        VBox rightSection = new VBox(10);
        rightSection.setId("weather-panel");
        rightSection.setPrefWidth(300);

        HBox weatherMain = new HBox(20);
        weatherMain.setAlignment(Pos.CENTER_LEFT);
        Label weatherIcon = new Label("🌫"); // Unicode Mist/Fog
        weatherIcon.setStyle("-fx-font-size: 40px; -fx-text-fill: #aecbfa;");
        Label temp = new Label("24°C");
        temp.setId("temp-text");
        Label condition = new Label("Mist");
        condition.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");
        weatherMain.getChildren().addAll(weatherIcon, temp, condition);

        Separator sep = new Separator();
        sep.setOpacity(0.3);

        HBox weatherDetails = new HBox(20);
        VBox w1 = createWeatherItem("Humidity", "89 %", "💧");
        VBox w2 = createWeatherItem("Wind", "7 km/h", "🌬");
        VBox w3 = createWeatherItem("UV Index", "0", "☀");
        weatherDetails.getChildren().addAll(w1, w2, w3);

        rightSection.getChildren().addAll(weatherMain, sep, weatherDetails);

        dashboardData.getChildren().addAll(leftSection, rightSection);

        card.getChildren().addAll(tabs, locationHeader, dashboardData);
        return card;
    }

    private VBox createGradientSlider() {
        VBox container = new VBox(5);

        // Gradient Bar
        Rectangle bar = new Rectangle(550, 6);
        bar.setArcHeight(6);
        bar.setArcWidth(6);

        Stop[] stops = new Stop[] {
                new Stop(0, Color.web("#00e400")), // Good
                new Stop(0.2, Color.web("#ffff00")), // Moderate
                new Stop(0.4, Color.web("#ff7e00")), // Unhealthy for Sens
                new Stop(0.6, Color.web("#ff0000")), // Unhealthy
                new Stop(0.8, Color.web("#8f3f97")), // Very Unhealthy
                new Stop(1.0, Color.web("#7e0023"))  // Hazardous
        };
        LinearGradient gradient = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
        bar.setFill(gradient);

        // Labels
        HBox labels = new HBox();
        labels.setPrefWidth(550);
        String[] texts = {"Good", "Moderate", "Poor", "Unhealthy", "Severe", "Hazardous"};
        for(String t : texts) {
            Label l = new Label(t);
            l.setStyle("-fx-text-fill: #aaa; -fx-font-size: 10px;");
            l.setPrefWidth(90);
            l.setAlignment(Pos.CENTER);
            labels.getChildren().add(l);
        }

        // Current Indicator (The white dot)
        // Calculating position for 181 (approx 60% across based on 0-300 scale usually)
        StackPane sliderStack = new StackPane();
        sliderStack.setAlignment(Pos.CENTER_LEFT);
        Circle thumb = new Circle(6, Color.WHITE);
        thumb.setStroke(Color.web("#2b2d42"));
        thumb.setStrokeWidth(2);
        thumb.setTranslateX(550 * 0.55); // Position manual adjustment

        sliderStack.getChildren().addAll(bar, thumb);

        container.getChildren().addAll(labels, sliderStack);
        return container;
    }

    private VBox createWeatherItem(String title, String val, String icon) {
        VBox v = new VBox(2);
        Label i = new Label(icon + " " + title);
        i.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");
        Label value = new Label(val);
        value.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        v.getChildren().addAll(i, value);
        return v;
    }

    public static void main(String[] args) {
        launch(args);
    }
}