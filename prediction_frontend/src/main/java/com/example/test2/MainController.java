package com.example.test2;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.geometry.Insets;

import java.util.HashMap;
import java.util.Map;

public class MainController {

    /* =======================
       UI CONTROLS
       ======================= */
    @FXML private HBox rootBox;
    @FXML private VBox sidebar;
    @FXML private HBox mainArea;
    @FXML private Label headerLabel;
    @FXML private ComboBox<String> cityBox;
    @FXML private Button predictButton;

    @FXML private Label symptomsTitle;
    @FXML private CheckBox symptomBreath;
    @FXML private CheckBox symptomCough;
    @FXML private CheckBox symptomChest;
    @FXML private CheckBox symptomIrritation;
    @FXML private CheckBox symptomFatigue;

    @FXML private Label cityLabel;
    @FXML private StackPane aqiCircle;
    @FXML private Label aqiLabel;
    @FXML private Label aqiStatus;
    @FXML private Label pm25Label;
    @FXML private Label pm10Label;

    @FXML private VBox adviceCard;
    @FXML private Label adviceTitle;
    @FXML private Label adviceText;

    private final Map<String, AQIData> cityData = new HashMap<>();

    // Animation Properties
    private IntegerProperty currentAqiValue = new SimpleIntegerProperty(0);
    private Timeline aqiTimeline;

    /* =======================
       INITIALIZE
       ======================= */
    @FXML
    public void initialize() {
        buildProgrammaticUI();

        // Bind the label's text directly to our animated integer property
        aqiLabel.textProperty().bind(currentAqiValue.asString());

        cityData.put("delhi", new AQIData("Delhi", 265, 185, 320));
        cityData.put("mumbai", new AQIData("Mumbai", 120, 75, 140));
        cityData.put("chennai", new AQIData("Chennai", 95, 50, 110));
        cityData.put("bangalore", new AQIData("Bangalore", 65, 40, 80));
        cityData.put("kolkata", new AQIData("Kolkata", 210, 160, 260));
        cityData.put("kochi", new AQIData("Kochi", 138, 52, 64));
        cityData.put("hyderabad", new AQIData("Hyderabad", 175, 110, 220));
        cityData.put("pune", new AQIData("Pune", 90, 45, 100));
        cityData.put("ahmedabad", new AQIData("Ahmedabad", 240, 180, 290));

        cityBox.getItems().addAll("Delhi", "Mumbai", "Chennai", "Bangalore", "Kolkata", "Kochi", "Hyderabad", "Pune", "Ahmedabad");
        cityBox.setEditable(true);
    }

    private void buildProgrammaticUI() {
        sidebar.setBackground(new Background(new BackgroundFill(Color.web("#1A1C23"), CornerRadii.EMPTY, Insets.EMPTY)));
        mainArea.setBackground(new Background(new BackgroundFill(Color.web("#111217"), CornerRadii.EMPTY, Insets.EMPTY)));

        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        headerLabel.setTextFill(Color.web("#FFFFFF"));
        symptomsTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        symptomsTitle.setTextFill(Color.web("#8e9bb0"));

        Color checkboxColor = Color.web("#D1D5DB");
        symptomBreath.setTextFill(checkboxColor);
        symptomCough.setTextFill(checkboxColor);
        symptomChest.setTextFill(checkboxColor);
        symptomIrritation.setTextFill(checkboxColor);
        symptomFatigue.setTextFill(checkboxColor);

        predictButton.setBackground(new Background(new BackgroundFill(Color.web("#3B82F6"), new CornerRadii(6), Insets.EMPTY)));
        predictButton.setTextFill(Color.WHITE);
        predictButton.setFont(Font.font("System", FontWeight.BOLD, 14));
        predictButton.setPadding(new Insets(12));

        predictButton.setOnMouseEntered(e -> predictButton.setBackground(new Background(new BackgroundFill(Color.web("#60A5FA"), new CornerRadii(6), Insets.EMPTY))));
        predictButton.setOnMouseExited(e -> predictButton.setBackground(new Background(new BackgroundFill(Color.web("#3B82F6"), new CornerRadii(6), Insets.EMPTY))));

        cityLabel.setFont(Font.font("System", FontWeight.BOLD, 36));
        cityLabel.setTextFill(Color.WHITE);

        aqiLabel.setFont(Font.font("System", FontWeight.BOLD, 72));
        aqiLabel.setTextFill(Color.web("#4B5563"));

        aqiStatus.setFont(Font.font("System", FontWeight.BOLD, 22));
        aqiStatus.setTextFill(Color.web("#8e9bb0"));

        pm25Label.setFont(Font.font("System", FontWeight.NORMAL, 16));
        pm25Label.setTextFill(Color.web("#9CA3AF"));
        pm10Label.setFont(Font.font("System", FontWeight.NORMAL, 16));
        pm10Label.setTextFill(Color.web("#9CA3AF"));

        aqiCircle.setBackground(new Background(new BackgroundFill(Color.web("#1A1C23"), new CornerRadii(120), Insets.EMPTY)));
        aqiCircle.setBorder(new Border(new BorderStroke(Color.web("#374151"), BorderStrokeStyle.SOLID, new CornerRadii(120), new BorderWidths(8))));

        // Floating Advice Card styling inside the right panel
        adviceCard.setBackground(new Background(new BackgroundFill(Color.web("#1A1C23"), new CornerRadii(12), Insets.EMPTY)));
        HBox.setMargin(adviceCard, new Insets(40, 40, 40, 0)); // Adds margin so it floats inside the main area

        adviceTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        adviceTitle.setTextFill(Color.WHITE);

        adviceText.setFont(Font.font("System", FontWeight.NORMAL, 15));
        adviceText.setTextFill(Color.web("#D1D5DB"));
        adviceText.setTextAlignment(TextAlignment.LEFT); // Left alignment is better for long scrolling text
    }

    /* =======================
       MAIN ACTION & ANIMATION
       ======================= */
    @FXML
    private void searchCityAQI() {
        String cityInput = cityBox.getEditor().getText().trim().toLowerCase();

        if (!cityData.containsKey(cityInput)) {
            showAlert("City Not Found", "No AQI data available for this city.", Color.web("#1A1C23"));
            return;
        }

        AQIData data = cityData.get(cityInput);
        int predictedAQI = predictNextHourAQI(data);

        cityLabel.setText(data.city().toUpperCase());
        pm25Label.setText("PM2.5: " + data.pm25() + " µg/m³");
        pm10Label.setText("PM10: " + data.pm10() + " µg/m³");

        // 1. Play the count-up animation for the AQI number
        if (aqiTimeline != null) aqiTimeline.stop();
        currentAqiValue.set(0); // Reset to 0 before animating up
        aqiTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1.2), new KeyValue(currentAqiValue, predictedAQI))
        );
        aqiTimeline.play();

        // 2. Load the advice text
        String advice = buildHealthAdvice(predictedAQI);

        // 3. Immediately snap the colors while the number animates
        if (predictedAQI <= 100) {
            applyStyles("GOOD", Color.web("#10B981"), advice);
        } else if (predictedAQI <= 200) {
            applyStyles("MODERATE", Color.web("#F59E0B"), advice);
        } else {
            applyStyles("POOR", Color.web("#EF4444"), advice);
            showAlert("Health Advisory ⚠️", "Air quality is UNHEALTHY.\nTake precautions.", Color.web("#3F1D1D"));
        }
    }

    private int predictNextHourAQI(AQIData data) {
        int predicted = data.aqi();
        if (data.pm25() > 150) predicted += 20;
        else if (data.pm25() > 80) predicted += 10;
        if (data.pm10() > 200) predicted += 15;
        else if (data.pm10() > 100) predicted += 5;
        return Math.min(predicted, 500);
    }

    private String buildHealthAdvice(int predictedAQI) {
        StringBuilder advice = new StringBuilder();

        advice.append("GENERAL STATUS:\n");
        if (predictedAQI <= 50) advice.append("Air quality is satisfactory. Air pollution poses little or no risk.\n\n");
        else if (predictedAQI <= 100) advice.append("Air quality is acceptable. Sensitive people should reduce prolonged outdoor exertion.\n\n");
        else if (predictedAQI <= 150) advice.append("Sensitive groups may experience health effects.\n\n");
        else if (predictedAQI <= 200) advice.append("Everyone may begin to experience health effects.\n\n");
        else advice.append("EMERGENCY: Health warning of emergency conditions. Remain indoors.\n\n");

        boolean anySymptom = symptomBreath.isSelected() || symptomCough.isSelected() ||
                symptomChest.isSelected() || symptomIrritation.isSelected() || symptomFatigue.isSelected();

        if (anySymptom) {
            advice.append("SYMPTOM ACTION PLAN:\n");
            if (symptomBreath.isSelected()) advice.append("• Breath: ").append(predictedAQI > 150 ? "CRITICAL. Use fast-acting inhaler, seek medical help.\n" : "Reduce physical exertion.\n");
            if (symptomCough.isSelected()) advice.append("• Cough: ").append(predictedAQI > 150 ? "Run HEPA air purifier indoors.\n" : "Wear N95 mask outside.\n");
            if (symptomChest.isSelected()) advice.append("• Chest: ").append(predictedAQI > 150 ? "URGENT. Consult a healthcare provider.\n" : "Rest completely.\n");
            if (symptomIrritation.isSelected()) advice.append("• Eyes: ").append(predictedAQI > 150 ? "Wash face, use lubricating drops.\n" : "Wear wrap-around sunglasses.\n");
            if (symptomFatigue.isSelected()) advice.append("• Fatigue: ").append(predictedAQI > 150 ? "Close windows, rest indoors.\n" : "Take frequent breaks.\n");
            advice.append("\n");
        }

        if (predictedAQI > 100) {
            advice.append("PRECAUTIONS:\n- Keep windows closed.\n- Turn on air purifiers.\n- Wear an N95 mask outdoors.\n");
        }

        return advice.toString();
    }

    private void applyStyles(String status, Color alertColor, String advice) {
        aqiStatus.setText(status);
        aqiStatus.setTextFill(alertColor);
        aqiLabel.setTextFill(alertColor);
        adviceText.setText(advice);
        aqiCircle.setBorder(new Border(new BorderStroke(alertColor, BorderStrokeStyle.SOLID, new CornerRadii(120), new BorderWidths(8))));
    }

    private void showAlert(String title, String message, Color bgColor) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setBackground(new Background(new BackgroundFill(bgColor, CornerRadii.EMPTY, Insets.EMPTY)));
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        alert.show();
    }
}