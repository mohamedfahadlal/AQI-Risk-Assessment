package com.example.aqidashboard;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.transform.Rotate;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class DashboardController {

    @FXML
    private TextField citySearchField;

    @FXML
    private Label cityLabel;

    @FXML
    private Label aqiLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label tempLabel;

    @FXML
    private Label humidityLabel;

    @FXML
    private Label windLabel;

    @FXML
    private Label pm25Label;

    @FXML
    private Label pm10Label;

    @FXML
    private Line needle;

    @FXML
    private VBox mainCard;

    @FXML
    private ImageView aqiImageView;

    private String currentCity = "Kochi";

    private ContextMenu suggestionsPopup = new ContextMenu();

    private List<String> cityList = Arrays.asList(
            "Kochi",
            "Kollam",
            "Kozhikode",
            "Kannur",
            "Kottayam",
            "Thiruvananthapuram",
            "Delhi",
            "Mumbai",
            "Chennai"
    );


    // ================= SEARCH =================
    @FXML
    private void handleCitySearch() {

        String enteredCity = citySearchField.getText();

        if (enteredCity != null && !enteredCity.isEmpty()) {
            currentCity = enteredCity;
            loadAQIData(currentCity);
        }
    }

    // ================= LOCATE =================
    @FXML
    private void handleDetectLocation() {

        currentCity = "Kochi"; // mock
        citySearchField.setText(currentCity);
        loadAQIData(currentCity);
    }

    // ================= LOAD DATA =================
    private void loadAQIData(String city) {

        // Mock values (replace with API later)
        int aqi = city.equalsIgnoreCase("Kochi") ? 138 :
                city.equalsIgnoreCase("Kollam") ? 220 :
                        city.equalsIgnoreCase("Delhi") ? 305 : 35;

        int pm25 = 52;
        int pm10 = 64;

        cityLabel.setText(city + ", India");

        aqiLabel.setText(String.valueOf(aqi));
        pm25Label.setText(pm25 + " µg/m³");
        pm10Label.setText(pm10 + " µg/m³");

        tempLabel.setText("31°C");
        humidityLabel.setText("59%");
        windLabel.setText("13 km/h");

        updateNeedle(aqi);
        updateRisk(aqi);
    }

    // ================= NEEDLE ROTATION =================


    @FXML
    public void initialize() {

        updateNeedle(0);
        statusLabel.setText("");
        aqiLabel.setText("0");

        citySearchField.textProperty().addListener((observable, oldValue, newValue) -> {

            if (newValue == null || newValue.isEmpty()) {
                suggestionsPopup.hide();
                return;
            }

            List<String> filtered = cityList.stream()
                    .filter(city -> city.toLowerCase().startsWith(newValue.toLowerCase()))
                    .collect(Collectors.toList());

            if (filtered.isEmpty()) {
                suggestionsPopup.hide();
            } else {
                populatePopup(filtered);
                if (!suggestionsPopup.isShowing()) {
                    suggestionsPopup.show(citySearchField,
                            javafx.geometry.Side.BOTTOM,
                            0, 0);
                }
            }
        });
    }

    private void populatePopup(List<String> searchResult) {

        List<MenuItem> menuItems = new ArrayList<>();

        for (String result : searchResult) {
            MenuItem item = new MenuItem(result);

            item.setOnAction(event -> {
                citySearchField.setText(result);
                suggestionsPopup.hide();
                loadAQIData(result);
            });

            menuItems.add(item);
        }

        suggestionsPopup.getItems().clear();
        suggestionsPopup.getItems().addAll(menuItems);
    }


    // Logic to map non-linear AQI values to linear gauge angles
    private void updateNeedle(int aqi) {
        double angle;

        // Calculate rotation angle (0 to 180 degrees clockwise)
        if (aqi <= 50) {
            angle = (aqi / 50.0) * 30; // 0-30 degrees
        } else if (aqi <= 100) {
            angle = 30 + ((aqi - 50) / 50.0) * 30; // 30-60 degrees
        } else if (aqi <= 150) {
            angle = 60 + ((aqi - 100) / 50.0) * 30; // 60-90 degrees
        } else if (aqi <= 200) {
            angle = 90 + ((aqi - 150) / 50.0) * 30; // 90-120 degrees
        } else if (aqi <= 300) {
            angle = 120 + ((aqi - 200) / 100.0) * 30; // 120-150 degrees
        } else {
            double safeAqi = Math.min(aqi, 500);
            angle = 150 + ((safeAqi - 300) / 200.0) * 30; // 150-180 degrees
        }

        // Apply rotation. Pivot is center (200, 200)
        Rotate rotate = new Rotate(angle, 200, 200);
        needle.getTransforms().clear();
        needle.getTransforms().add(rotate);
    }

    // ================= RISK LEVEL =================
    private void updateRisk(int aqi) {
        String risk;
        String hexColor;
        String gradientStyle;
        String imageName; // New variable for image file

        String baseStyle = "-fx-background-radius: 30; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 30, 0, 0, 10); -fx-padding: 30 50;";

        if (aqi <= 50) {
            risk = "Good";
            hexColor = "#2ecc71";
            gradientStyle = "linear-gradient(to bottom right, #eafaf1, #d5f5e3)";
            imageName = "good.png";
        } else if (aqi <= 100) {
            risk = "Moderate";
            hexColor = "#f1c40f";
            gradientStyle = "linear-gradient(to bottom right, #fef9e7, #f9e79f)";
            imageName = "moderate.png";
        } else if (aqi <= 150) {
            risk = "Poor";
            hexColor = "#e67e22";
            gradientStyle = "linear-gradient(to bottom right, #fdf2e9, #fad7a0)";
            imageName = "poor.png";
        } else if (aqi <= 200) {
            risk = "Unhealthy";
            hexColor = "#d35400";
            gradientStyle = "linear-gradient(to bottom right, #fbeee6, #edbb99)";
            imageName = "unhealthy.png";
        } else if (aqi <= 300) {
            risk = "Severe";
            hexColor = "#8e44ad";
            gradientStyle = "linear-gradient(to bottom right, #f4ecf7, #d7bde2)";
            imageName = "severe.png";
        } else {
            risk = "Hazardous";
            hexColor = "#c0392b";
            gradientStyle = "linear-gradient(to bottom right, #fadbd8, #f1948a)";
            imageName = "hazardous.png";
        }

        // Apply Text & Color
        statusLabel.setText(risk);
        statusLabel.setStyle("-fx-text-fill: " + hexColor + "; -fx-font-weight: bold; -fx-font-size: 24px;");
        aqiLabel.setStyle("-fx-text-fill: " + hexColor + "; -fx-font-weight: 900; -fx-font-size: 55px;");
        mainCard.setStyle("-fx-background-color: " + gradientStyle + "; " + baseStyle);

        // Load and Set Image
        try {
            // Assumes images are in src/main/resources/images/
            String path = "/images/" + imageName;
            Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(path)));
            aqiImageView.setImage(image);
        } catch (Exception e) {
            System.out.println("Error loading image: " + imageName);
            e.printStackTrace();
        }
    }
}
