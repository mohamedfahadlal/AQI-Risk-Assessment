package com.example.test2;

import com.aqi.utils.SceneManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModelDetailController {

    private static final String ML_SERVER = "http://localhost:5000";

    private String   modelName;
    private String   modelLabel;
    private String   modelColor;
    private JsonNode lastAqiData;

    private final HttpClient   http   = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private VBox      sidebarMenu;
    private ImageView plotImageView;
    private Label     plotTitleLabel;
    private Label     statusLabel;
    private StackPane plotArea;
    private String    activePlot = "feature_importance";

    private static final String METRICS_KEY   = "eval_metrics";
    private static final String SEPARATOR_KEY = "---";

    // ── Sidebar entries — separators are visual dividers ──────────
    private static final Map<String, String> PLOTS = new LinkedHashMap<>();
    static {
        PLOTS.put("feature_importance",  "📊  Feature Importance");
        PLOTS.put("shap",                "🔍  SHAP Summary");
        PLOTS.put("pdp",                 "📈  Partial Dependence");
        PLOTS.put("learning_curve",      "📚  Learning Curve");
        PLOTS.put("sep1",                SEPARATOR_KEY);
        PLOTS.put("actual_vs_predicted", "📉  Actual vs Predicted");
        PLOTS.put("residual",            "🎯  Residual Plot");
        PLOTS.put("error_distribution",  "📦  Error Distribution");
        PLOTS.put("sep2",                SEPARATOR_KEY);
        PLOTS.put(METRICS_KEY,           "🏅  Evaluation Metrics");
    }

    // ── Init ──────────────────────────────────────────────────────
    public void init(String modelNameInternal, JsonNode aqiData) {
        this.modelName   = modelNameInternal;
        this.lastAqiData = aqiData;
        this.modelLabel  = switch (modelNameInternal) {
            case "randomforest" -> "Random Forest";
            case "lightgbm"     -> "LightGBM";
            default             -> "XGBoost";
        };
        this.modelColor = switch (modelNameInternal) {
            case "randomforest" -> "#f59e0b";
            case "lightgbm"     -> "#8b5cf6";
            default             -> "#10b981";
        };
    }

    // ── Build page ────────────────────────────────────────────────
    public VBox buildPage() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0f172a;");
        root.setFillWidth(true);
        root.getChildren().add(buildNavbar());

        HBox body = new HBox(0);
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox plotPane = buildPlotPane();
        HBox.setHgrow(plotPane, Priority.ALWAYS);
        body.getChildren().addAll(buildSidebar(), plotPane);
        root.getChildren().add(body);

        Platform.runLater(() -> selectPlot("feature_importance"));
        return root;
    }

    // ── Navbar ────────────────────────────────────────────────────
    private HBox buildNavbar() {
        HBox nav = new HBox(14);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setStyle(
                "-fx-background-color: #1e293b;" +
                        "-fx-padding: 14 28;" +
                        "-fx-border-color: #334155 transparent transparent transparent;" +
                        "-fx-border-width: 0 0 1 0;");
        nav.setEffect(new DropShadow(12, 0, 3, Color.color(0, 0, 0, 0.3)));

        Label chevron = new Label("‹");
        chevron.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + modelColor + ";");
        StackPane chevronCircle = new StackPane(chevron);
        chevronCircle.setPrefSize(34, 34);
        String circleBase = "-fx-background-color: rgba(99,102,241,0.12); -fx-background-radius: 17; -fx-cursor: hand;";
        String circleHover = "-fx-background-color: rgba(99,102,241,0.28); -fx-background-radius: 17; -fx-cursor: hand;";
        chevronCircle.setStyle(circleBase);
        chevronCircle.setOnMouseClicked(e -> goBack());
        chevronCircle.setOnMouseEntered(e -> chevronCircle.setStyle(circleHover));
        chevronCircle.setOnMouseExited(e  -> chevronCircle.setStyle(circleBase));

        Label backLbl = new Label("Prediction");
        backLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b; -fx-cursor: hand;");
        backLbl.setOnMouseClicked(e -> goBack());

        Label sep = new Label("›");
        sep.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");

        Label modelBadge = new Label("  " + modelLabel + "  ");
        modelBadge.setStyle(
                "-fx-background-color: " + modelColor + "22;" +
                        "-fx-text-fill: " + modelColor + ";" +
                        "-fx-font-size: 13px; -fx-font-weight: bold;" +
                        "-fx-background-radius: 8; -fx-padding: 4 10;");

        Label pageTitle = new Label("Model Analysis");
        pageTitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #e2e8f0;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label infoPill = new Label("Flask ML  ·  Port 5000");
        infoPill.setStyle(
                "-fx-font-size: 11px; -fx-text-fill: #475569;" +
                        "-fx-background-color: #1e293b; -fx-border-color: #334155;" +
                        "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 4 12;");

        nav.getChildren().addAll(chevronCircle, backLbl, sep, modelBadge, pageTitle, spacer, infoPill);
        return nav;
    }

    // ── Sidebar ───────────────────────────────────────────────────
    private VBox buildSidebar() {
        VBox sidebar = new VBox(0);
        sidebar.setPrefWidth(230);
        sidebar.setMinWidth(230);
        sidebar.setMaxWidth(230);
        sidebar.setStyle(
                "-fx-background-color: #1e293b;" +
                        "-fx-border-color: transparent #334155 transparent transparent;" +
                        "-fx-border-width: 0 1 0 0;");

        // Header
        VBox header = new VBox(4);
        header.setStyle(
                "-fx-padding: 22 20 16 20;" +
                        "-fx-border-color: transparent transparent #334155 transparent;" +
                        "-fx-border-width: 0 0 1 0;");

        Label categoryLbl = new Label("ANALYSIS TYPE");
        categoryLbl.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold;" +
                        "-fx-text-fill: #475569; -fx-letter-spacing: 2px;");

        Label modelLbl = new Label(modelLabel);
        modelLbl.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold;" +
                        "-fx-text-fill: " + modelColor + ";");

        // Model accuracy pill — filled when metrics load, placeholder for now
        Label pillLbl = new Label("ML Model");
        pillLbl.setStyle(
                "-fx-font-size: 10px;" +
                        "-fx-text-fill: #475569;" +
                        "-fx-background-color: #0f172a;" +
                        "-fx-background-radius: 6; -fx-padding: 3 8;");
        pillLbl.setId("modelPill");

        header.getChildren().addAll(categoryLbl, modelLbl, pillLbl);
        sidebar.getChildren().add(header);

        // Menu
        sidebarMenu = new VBox(2);
        sidebarMenu.setStyle("-fx-padding: 10 8;");

        // Section labels
        String[] sectionLabels = { null, null, null, null,
                "Error Analysis", null, null,
                null, "Summary" };
        int idx = 0;
        for (Map.Entry<String, String> entry : PLOTS.entrySet()) {
            String key   = entry.getKey();
            String label = entry.getValue();

            if (SEPARATOR_KEY.equals(label)) {
                sidebarMenu.getChildren().add(makeSeparator());
            } else {
                sidebarMenu.getChildren().add(makeSidebarItem(key, label));
            }
            idx++;
        }

        sidebar.getChildren().add(sidebarMenu);

        // Bottom hint
        Region vSpacer = new Region();
        VBox.setVgrow(vSpacer, Priority.ALWAYS);

        Label hint = new Label("Hover chart for\nglow preview");
        hint.setStyle(
                "-fx-font-size: 10px; -fx-text-fill: #334155;" +
                        "-fx-text-alignment: center; -fx-padding: 0 0 16 0;");
        hint.setWrapText(true);
        hint.setMaxWidth(190);

        VBox bottom = new VBox(hint);
        bottom.setAlignment(Pos.BOTTOM_CENTER);
        VBox.setVgrow(bottom, Priority.ALWAYS);

        sidebar.getChildren().addAll(vSpacer, bottom);
        return sidebar;
    }

    private Region makeSeparator() {
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setMaxWidth(Double.MAX_VALUE);
        sep.setStyle("-fx-background-color: #334155; -fx-margin: 6 16;");
        VBox wrapper = new VBox(sep);
        wrapper.setPadding(new Insets(6, 16, 6, 16));
        // Return the wrapper as a Region trick — use a VBox cast via Region
        Region outer = new Region();
        outer.setPrefHeight(13);
        outer.setStyle("-fx-background-color: transparent;");
        // Embed a visible line
        StackPane line = new StackPane();
        line.setPrefHeight(13);
        line.setStyle("-fx-padding: 6 16;");
        Region lineInner = new Region();
        lineInner.setPrefHeight(1);
        lineInner.setMaxWidth(Double.MAX_VALUE);
        lineInner.setStyle("-fx-background-color: #334155;");
        line.getChildren().add(lineInner);
        // We can't return StackPane as Region cleanly, so return a spacer with border
        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setMaxWidth(Double.MAX_VALUE);
        divider.setStyle(
                "-fx-background-color: #334155;" +
                        "-fx-margin: 6 0;");
        VBox divBox = new VBox(divider);
        divBox.setPadding(new Insets(7, 16, 7, 16));
        // Cast trick: return a Pane
        return divider;
    }

    private VBox makeSidebarItem(String key, String label) {
        // Split emoji and text
        String emoji = label.split("  ")[0];
        String text  = label.contains("  ") ? label.split("  ", 2)[1] : label;

        Label emojiLbl = new Label(emoji);
        emojiLbl.setStyle("-fx-font-size: 14px; -fx-min-width: 24px;");

        Label textLbl = new Label(text);
        textLbl.setStyle(
                "-fx-font-size: 13px; -fx-text-fill: #94a3b8;" +
                        "-fx-font-family: 'Segoe UI';");
        textLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textLbl, Priority.ALWAYS);

        HBox row = new HBox(10, emojiLbl, textLbl);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox item = new VBox(row);
        item.setPadding(new Insets(9, 14, 9, 14));
        item.setStyle("-fx-background-radius: 10; -fx-cursor: hand;");
        item.setId("sidebar_" + key);

        String inactiveStyle  = "-fx-background-radius: 10; -fx-cursor: hand;";
        String hoverStyle     =
                "-fx-background-color: #334155;" +
                        "-fx-background-radius: 10; -fx-cursor: hand;";
        String activeStyle    =
                "-fx-background-color: " + modelColor + "1e;" +
                        "-fx-background-radius: 10; -fx-cursor: hand;" +
                        "-fx-border-color: " + modelColor + ";" +
                        "-fx-border-width: 0 0 0 3; -fx-border-radius: 0 10 10 0;";

        item.setOnMouseEntered(e -> {
            if (!key.equals(activePlot)) {
                item.setStyle(hoverStyle);
                textLbl.setStyle(
                        "-fx-font-size: 13px; -fx-text-fill: #cbd5e1;" +
                                "-fx-font-family: 'Segoe UI';");
            }
        });
        item.setOnMouseExited(e -> {
            if (!key.equals(activePlot)) {
                item.setStyle(inactiveStyle);
                textLbl.setStyle(
                        "-fx-font-size: 13px; -fx-text-fill: #94a3b8;" +
                                "-fx-font-family: 'Segoe UI';");
            }
        });
        item.setOnMouseClicked(e -> selectPlot(key));
        return item;
    }

    private void updateSidebarActive(String key) {
        for (javafx.scene.Node node : sidebarMenu.getChildren()) {
            if (!(node instanceof VBox item)) continue;
            if (item.getId() == null) continue;
            boolean active = item.getId().equals("sidebar_" + key);
            if (!(item.getChildren().get(0) instanceof HBox row)) continue;
            if (!(row.getChildren().get(1) instanceof Label textLbl)) continue;

            if (active) {
                item.setStyle(
                        "-fx-background-color: " + modelColor + "1e;" +
                                "-fx-background-radius: 10; -fx-cursor: hand;" +
                                "-fx-border-color: " + modelColor + ";" +
                                "-fx-border-width: 0 0 0 3; -fx-border-radius: 0 10 10 0;");
                textLbl.setStyle(
                        "-fx-font-size: 13px; -fx-font-weight: bold;" +
                                "-fx-text-fill: " + modelColor + ";" +
                                "-fx-font-family: 'Segoe UI';");
            } else {
                item.setStyle("-fx-background-radius: 10; -fx-cursor: hand;");
                textLbl.setStyle(
                        "-fx-font-size: 13px; -fx-text-fill: #94a3b8;" +
                                "-fx-font-family: 'Segoe UI';");
            }
        }
    }

    // ── Plot pane ─────────────────────────────────────────────────
    private VBox buildPlotPane() {
        VBox pane = new VBox(0);
        pane.setStyle("-fx-background-color: #0f172a;");
        pane.setFillWidth(true);

        // Title bar
        HBox titleBar = new HBox(12);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setStyle(
                "-fx-padding: 16 28;" +
                        "-fx-border-color: transparent transparent #1e293b transparent;" +
                        "-fx-border-width: 0 0 1 0;");

        plotTitleLabel = new Label("Feature Importance");
        plotTitleLabel.setStyle(
                "-fx-font-size: 17px; -fx-font-weight: bold;" +
                        "-fx-text-fill: #e2e8f0; -fx-font-family: 'Segoe UI';");

        statusLabel = new Label("Select a plot to load");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button saveBtn = new Button("💾  Save PNG");
        saveBtn.setStyle(
                "-fx-background-color: #1e293b; -fx-text-fill: #94a3b8;" +
                        "-fx-background-radius: 10; -fx-border-color: #334155;" +
                        "-fx-border-radius: 10; -fx-font-size: 12px;" +
                        "-fx-padding: 7 14; -fx-cursor: hand;");
        saveBtn.setOnMouseEntered(e -> saveBtn.setStyle(
                "-fx-background-color: #334155; -fx-text-fill: #e2e8f0;" +
                        "-fx-background-radius: 10; -fx-border-color: #475569;" +
                        "-fx-border-radius: 10; -fx-font-size: 12px;" +
                        "-fx-padding: 7 14; -fx-cursor: hand;"));
        saveBtn.setOnMouseExited(e -> saveBtn.setStyle(
                "-fx-background-color: #1e293b; -fx-text-fill: #94a3b8;" +
                        "-fx-background-radius: 10; -fx-border-color: #334155;" +
                        "-fx-border-radius: 10; -fx-font-size: 12px;" +
                        "-fx-padding: 7 14; -fx-cursor: hand;"));
        saveBtn.setOnAction(e -> savePlot());

        titleBar.getChildren().addAll(plotTitleLabel, statusLabel, spacer, saveBtn);
        pane.getChildren().add(titleBar);

        // Plot area
        plotArea = new StackPane();
        plotArea.setStyle("-fx-background-color: #0f172a;");
        VBox.setVgrow(plotArea, Priority.ALWAYS);

        plotImageView = new ImageView();
        plotImageView.setPreserveRatio(true);
        plotImageView.setSmooth(true);
        plotImageView.fitWidthProperty().bind(plotArea.widthProperty().subtract(60));
        plotImageView.fitHeightProperty().bind(plotArea.heightProperty().subtract(40));

        // ── Image hover glow effect ───────────────────────────────
        Color glowColor = Color.web(modelColor);
        DropShadow glowEffect = new DropShadow(22, 0, 0, glowColor);
        glowEffect.setSpread(0.08);

        ScaleTransition scaleIn  = new ScaleTransition(Duration.millis(200), plotImageView);
        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), plotImageView);

        plotImageView.setOnMouseEntered(e -> {
            if (plotImageView.getImage() == null) return;
            scaleOut.stop();
            scaleIn.setFromX(plotImageView.getScaleX());
            scaleIn.setFromY(plotImageView.getScaleY());
            scaleIn.setToX(1.025); scaleIn.setToY(1.025);
            plotImageView.setEffect(glowEffect);
            scaleIn.play();
        });
        plotImageView.setOnMouseExited(e -> {
            scaleIn.stop();
            scaleOut.setFromX(plotImageView.getScaleX());
            scaleOut.setFromY(plotImageView.getScaleY());
            scaleOut.setToX(1.0); scaleOut.setToY(1.0);
            plotImageView.setEffect(null);
            scaleOut.play();
        });

        // Placeholder
        VBox placeholder = new VBox(12);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setId("placeholder");
        Label placeholderIcon = new Label("📊");
        placeholderIcon.setStyle("-fx-font-size: 40px; -fx-opacity: 0.3;");
        Label placeholderText = new Label("Select an analysis type\nfrom the sidebar");
        placeholderText.setStyle(
                "-fx-font-size: 14px; -fx-text-fill: #334155;" +
                        "-fx-text-alignment: center;");
        placeholderText.setWrapText(true);
        placeholder.getChildren().addAll(placeholderIcon, placeholderText);

        // Spinner
        Label spinner = new Label("⟳");
        spinner.setId("spinner");
        spinner.setStyle("-fx-font-size: 36px; -fx-text-fill: " + modelColor + "; -fx-opacity: 0;");
        RotateTransition rt = new RotateTransition(Duration.seconds(1.0), spinner);
        rt.setByAngle(360); rt.setCycleCount(Animation.INDEFINITE); rt.play();

        plotArea.getChildren().addAll(placeholder, spinner, plotImageView);
        pane.getChildren().add(plotArea);
        return pane;
    }

    // ── Select plot ───────────────────────────────────────────────
    private void selectPlot(String key) {
        activePlot = key;
        updateSidebarActive(key);

        String label     = PLOTS.getOrDefault(key, key);
        String cleanLabel = label.replaceAll("^\\S+\\s+", "");
        plotTitleLabel.setText(cleanLabel);
        statusLabel.setText("Loading…");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        // Clear previous content
        plotImageView.setImage(null);
        plotImageView.setOpacity(0);
        plotImageView.setEffect(null);
        plotArea.getChildren().removeIf(n -> "metricsPanel".equals(n.getId()));

        // Show placeholder & spinner
        javafx.scene.Node placeholder = plotArea.lookup("#placeholder");
        if (placeholder != null) placeholder.setOpacity(0);
        javafx.scene.Node spinner = plotArea.lookup("#spinner");
        if (spinner != null) spinner.setOpacity(1);

        if (METRICS_KEY.equals(key)) {
            loadMetrics(spinner, cleanLabel);
        } else {
            loadPlotImage(key, spinner, cleanLabel);
        }
    }

    // ── Load matplotlib PNG ───────────────────────────────────────
    private void loadPlotImage(String key, javafx.scene.Node spinner, String cleanLabel) {
        new Thread(() -> {
            try {
                com.fasterxml.jackson.databind.node.ObjectNode payload = mapper.createObjectNode();
                payload.put("model", modelName);
                payload.put("plot",  key);
                appendAqiFields(payload);

                HttpResponse<String> resp = http.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(ML_SERVER + "/plot"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                                .timeout(java.time.Duration.ofSeconds(60)).build(),
                        HttpResponse.BodyHandlers.ofString());

                String body = resp.body();
                if (resp.statusCode() == 200 && body.trim().startsWith("{")) {
                    JsonNode result  = mapper.readTree(body);
                    byte[]  imgBytes = Base64.getDecoder().decode(result.path("image").asText());
                    Image   image    = new Image(new ByteArrayInputStream(imgBytes));
                    Platform.runLater(() -> {
                        if (spinner != null) spinner.setOpacity(0);
                        plotImageView.setImage(image);
                        FadeTransition ft = new FadeTransition(Duration.millis(350), plotImageView);
                        ft.setFromValue(0); ft.setToValue(1); ft.play();
                        statusLabel.setText("✓  " + modelLabel + "  ·  " + cleanLabel
                                + "   —   hover chart for glow");
                        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + modelColor + ";");
                    });
                } else {
                    String msg = body.trim().startsWith("{")
                            ? mapper.readTree(body).path("error").asText("Server error")
                            : "HTTP " + resp.statusCode();
                    Platform.runLater(() -> showError(spinner, msg));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> showError(spinner, ex.getMessage()));
            }
        }).start();
    }

    // ── Load evaluation metrics ───────────────────────────────────
    private void loadMetrics(javafx.scene.Node spinner, String cleanLabel) {
        new Thread(() -> {
            try {
                com.fasterxml.jackson.databind.node.ObjectNode payload = mapper.createObjectNode();
                payload.put("model", modelName);
                appendAqiFields(payload);

                HttpResponse<String> resp = http.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(ML_SERVER + "/metrics"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                                .timeout(java.time.Duration.ofSeconds(60)).build(),
                        HttpResponse.BodyHandlers.ofString());

                String body = resp.body();
                if (resp.statusCode() == 200 && body.trim().startsWith("{")) {
                    JsonNode json = mapper.readTree(body);
                    Platform.runLater(() -> {
                        if (spinner != null) spinner.setOpacity(0);
                        renderMetricsPanel(json);
                        statusLabel.setText("✓  " + modelLabel + "  ·  " + cleanLabel);
                        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + modelColor + ";");
                    });
                } else {
                    String msg = body.trim().startsWith("{")
                            ? mapper.readTree(body).path("error").asText("Server error")
                            : "HTTP " + resp.statusCode();
                    Platform.runLater(() -> showError(spinner, msg));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> showError(spinner, ex.getMessage()));
            }
        }).start();
    }

    // ── Render metrics cards ──────────────────────────────────────
    private void renderMetricsPanel(JsonNode json) {
        ScrollPane scroll = new ScrollPane();
        scroll.setId("metricsPanel");
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        VBox container = new VBox(28);
        container.setStyle("-fx-background-color: transparent; -fx-padding: 30 44;");
        container.setFillWidth(true);

        // Header
        Label header = new Label("Evaluation Metrics  —  " + modelLabel);
        header.setStyle(
                "-fx-font-size: 19px; -fx-font-weight: bold;" +
                        "-fx-text-fill: #e2e8f0; -fx-font-family: 'Segoe UI';");
        Label subheader = new Label(
                "Regression metrics computed on 150 synthetic samples · 50 per AQI class");
        subheader.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
        container.getChildren().addAll(header, subheader);

        // ── Row 1: primary regression metrics ────────────────────
        Label row1Label = new Label("REGRESSION ACCURACY");
        row1Label.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold;" +
                        "-fx-text-fill: #334155; -fx-letter-spacing: 2px;");

        FlowPane row1 = new FlowPane(14, 14);
        row1.setPrefWrapLength(860);
        row1.getChildren().addAll(
                makeMetricCard("MAE",
                        fmtVal(json, "mae"),
                        "Mean Absolute Error",
                        "Avg AQI units off per prediction",
                        "#0ea5e9"),
                makeMetricCard("RMSE",
                        fmtVal(json, "rmse"),
                        "Root Mean Squared Error",
                        "Penalizes large errors harder",
                        "#ef4444"),
                makeMetricCard("R² Score",
                        fmtPct(json, "r2"),
                        "Variance Explained",
                        "1.0 = perfect fit",
                        modelColor),
                makeMetricCard("MAPE",
                        fmtPct(json, "mape"),
                        "Mean Absolute % Error",
                        "Relative error across AQI range",
                        "#6366f1")
        );
        container.getChildren().addAll(row1Label, row1);

        // ── Row 2: secondary metrics ──────────────────────────────
        Label row2Label = new Label("ERROR CHARACTERISTICS");
        row2Label.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold;" +
                        "-fx-text-fill: #334155; -fx-letter-spacing: 2px;");

        FlowPane row2 = new FlowPane(14, 14);
        row2.setPrefWrapLength(860);
        row2.getChildren().addAll(
                makeMetricCard("Max Error",
                        fmtVal(json, "max_error"),
                        "Worst Single Prediction",
                        "Largest AQI prediction miss",
                        "#f59e0b"),
                makeMetricCard("Median AE",
                        fmtVal(json, "median_ae"),
                        "Median Absolute Error",
                        "Robust to outliers",
                        "#8b5cf6"),
                makeMetricCard("Samples",
                        "150",
                        "Test Set Size",
                        "50 Good · 50 Moderate · 50 Unhealthy",
                        "#475569")
        );
        container.getChildren().addAll(row2Label, row2);

        // ── Interpretation callout ────────────────────────────────
        String interp = json.path("interpretation").asText("");
        if (!interp.isEmpty()) {
            VBox callout = new VBox(8);
            callout.setStyle(
                    "-fx-background-color: " + modelColor + "14;" +
                            "-fx-border-color: " + modelColor + "55;" +
                            "-fx-border-width: 0 0 0 4;" +
                            "-fx-padding: 16 20; -fx-background-radius: 0 10 10 0;");

            Label interpTitle = new Label("💡  Model Insight");
            interpTitle.setStyle(
                    "-fx-font-size: 12px; -fx-font-weight: bold;" +
                            "-fx-text-fill: " + modelColor + ";");

            Label interpText = new Label(interp);
            interpText.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8; -fx-line-spacing: 3;");
            interpText.setWrapText(true);

            callout.getChildren().addAll(interpTitle, interpText);
            container.getChildren().add(callout);
        }

        // ── Metric guide ──────────────────────────────────────────
        Label guideLabel = new Label("METRIC GUIDE");
        guideLabel.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold;" +
                        "-fx-text-fill: #334155; -fx-letter-spacing: 2px;");

        VBox guideBox = new VBox(6);
        guideBox.setStyle(
                "-fx-background-color: #1e293b;" +
                        "-fx-background-radius: 12; -fx-padding: 16 20;");
        String[][] guide = {
                {"MAE",        "Lower is better. < 15 AQI = excellent, < 30 = good, > 50 = needs improvement."},
                {"RMSE",       "Lower is better. Higher than MAE means large occasional errors exist."},
                {"R² Score",   "Closer to 100% is better. > 90% = strong fit, < 70% = weak predictive power."},
                {"MAPE",       "Lower is better. < 10% = excellent, < 20% = acceptable for AQI forecasting."},
                {"Max Error",  "Worst case miss. Important for health safety — large max errors are concerning."},
                {"Median AE",  "If much lower than MAE, the model has occasional large outlier errors."},
        };
        for (String[] row : guide) {
            HBox guideRow = new HBox(12);
            Label key = new Label(row[0]);
            key.setPrefWidth(90);
            key.setStyle(
                    "-fx-font-size: 11px; -fx-font-weight: bold;" +
                            "-fx-text-fill: " + modelColor + ";");
            Label val = new Label(row[1]);
            val.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
            val.setWrapText(true);
            HBox.setHgrow(val, Priority.ALWAYS);
            guideRow.getChildren().addAll(key, val);
            guideBox.getChildren().add(guideRow);
        }
        container.getChildren().addAll(guideLabel, guideBox);

        scroll.setContent(container);
        scroll.setOpacity(0);
        plotArea.getChildren().add(scroll);
        FadeTransition ft = new FadeTransition(Duration.millis(350), scroll);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    // ── Metric card with hover ────────────────────────────────────
    private VBox makeMetricCard(String title, String value,
                                String subtitle, String description, String color) {
        Label titleLbl = new Label(title.toUpperCase());
        titleLbl.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold;" +
                        "-fx-text-fill: #475569; -fx-letter-spacing: 1px;");

        Label valueLbl = new Label(value);
        valueLbl.setStyle(
                "-fx-font-size: 30px; -fx-font-weight: bold;" +
                        "-fx-text-fill: " + color + "; -fx-font-family: 'Segoe UI';");

        Label subLbl = new Label(subtitle);
        subLbl.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: bold;" +
                        "-fx-text-fill: #64748b;");

        Label descLbl = new Label(description);
        descLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #475569;");
        descLbl.setWrapText(true);

        VBox card = new VBox(5, titleLbl, valueLbl, subLbl, descLbl);
        card.setPadding(new Insets(18, 22, 18, 22));
        card.setPrefWidth(185);

        String baseStyle =
                "-fx-background-color: #1e293b;" +
                        "-fx-background-radius: 14;" +
                        "-fx-border-color: " + color + "30;" +
                        "-fx-border-radius: 14; -fx-border-width: 1;";
        String hoverStyle =
                "-fx-background-color: #1e293b;" +
                        "-fx-background-radius: 14;" +
                        "-fx-border-color: " + color + "90;" +
                        "-fx-border-radius: 14; -fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, " + color + "55, 18, 0.1, 0, 5);";

        card.setStyle(baseStyle);

        ScaleTransition hoverIn  = new ScaleTransition(Duration.millis(160), card);
        ScaleTransition hoverOut = new ScaleTransition(Duration.millis(160), card);

        card.setOnMouseEntered(e -> {
            hoverOut.stop();
            hoverIn.setToX(1.04); hoverIn.setToY(1.04); hoverIn.play();
            card.setStyle(hoverStyle);
            valueLbl.setStyle(
                    "-fx-font-size: 30px; -fx-font-weight: bold;" +
                            "-fx-text-fill: " + color + "; -fx-font-family: 'Segoe UI';" +
                            "-fx-effect: dropshadow(gaussian, " + color + "99, 10, 0.2, 0, 0);");
        });
        card.setOnMouseExited(e -> {
            hoverIn.stop();
            hoverOut.setToX(1.0); hoverOut.setToY(1.0); hoverOut.play();
            card.setStyle(baseStyle);
            valueLbl.setStyle(
                    "-fx-font-size: 30px; -fx-font-weight: bold;" +
                            "-fx-text-fill: " + color + "; -fx-font-family: 'Segoe UI';");
        });

        return card;
    }

    // ── Helpers ───────────────────────────────────────────────────
    private void appendAqiFields(com.fasterxml.jackson.databind.node.ObjectNode p) {
        if (lastAqiData == null) return;
        p.put("pm25",             lastAqiData.path("pm25").asDouble(30));
        p.put("pm10",             lastAqiData.path("pm10").asDouble(50));
        p.put("no2",              lastAqiData.path("no2").asDouble(10));
        p.put("o3",               lastAqiData.path("o3").asDouble(50));
        p.put("co",               lastAqiData.path("co").asDouble(0.5));
        p.put("so2",              lastAqiData.path("so2").asDouble(5));
        p.put("temperature",      lastAqiData.path("temperature").asDouble(28));
        p.put("relativehumidity", lastAqiData.path("humidity").asDouble(65));
        p.put("wind_speed",       lastAqiData.path("windSpeed").asDouble(5));
        p.put("wind_direction",   lastAqiData.path("windDirection").asDouble(180));
        p.put("lat",              lastAqiData.path("lat").asDouble(10));
        p.put("lon",              lastAqiData.path("lon").asDouble(76));
        int aqi = lastAqiData.path("aqi").asInt(100);
        p.put("current_aqi", aqi);
        p.put("aqi_lag_1",   aqi);
        p.put("aqi_lag_2",   aqi);
        p.put("hour",        java.time.LocalDateTime.now().getHour());
        p.put("day_of_week", java.time.LocalDateTime.now().getDayOfWeek().getValue() % 7);
        p.put("month",       java.time.LocalDateTime.now().getMonthValue());
    }

    private String fmtVal(JsonNode json, String key) {
        double v = json.path(key).asDouble(-1);
        return v < 0 ? "—" : String.format("%.2f", v);
    }

    private String fmtPct(JsonNode json, String key) {
        double v = json.path(key).asDouble(-1);
        if (v < 0) return "—";
        // R2 and MAPE come as 0-1 range from server
        return String.format("%.1f%%", v * 100);
    }

    private void showError(javafx.scene.Node spinner, String msg) {
        if (spinner != null) spinner.setOpacity(0);
        statusLabel.setText("⚠  " + (msg != null ? msg : "Unknown error"));
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #ef4444;");
    }

    // ── Save PNG ──────────────────────────────────────────────────
    private void savePlot() {
        if (plotImageView.getImage() == null) return;
        try {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Save Plot");
            fc.setInitialFileName(modelName + "_" + activePlot + ".png");
            fc.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("PNG Image", "*.png"));
            java.io.File file = fc.showSaveDialog(null);
            if (file != null) {
                javax.imageio.ImageIO.write(
                        javafx.embed.swing.SwingFXUtils.fromFXImage(plotImageView.getImage(), null),
                        "png", file);
                statusLabel.setText("Saved  →  " + file.getName());
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + modelColor + ";");
            }
        } catch (Exception e) {
            statusLabel.setText("Save failed: " + e.getMessage());
        }
    }

    // ── Back ──────────────────────────────────────────────────────
    private void goBack() {
        SceneManager.switchScene("/com/example/test2/main_view.fxml", "Predict AQI");
    }
}
