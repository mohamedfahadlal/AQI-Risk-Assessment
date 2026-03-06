package com.aqi.utils;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.Popup;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public class MatrixDatePicker {
    private final Popup popup = new Popup();
    private LocalDate tempSelectedDate;
    private LocalDate viewDate;
    private final TextField targetField;

    private enum ViewMode { DAYS, MONTHS, YEARS }
    private ViewMode currentMode = ViewMode.DAYS;

    private final Button titleBtn = new Button();
    private final GridPane daysGrid = new GridPane();
    private final GridPane monthsGrid = new GridPane();
    private final GridPane yearsGrid = new GridPane();
    private final StackPane viewsStack = new StackPane(daysGrid, monthsGrid, yearsGrid);

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    public MatrixDatePicker(TextField targetField, Button triggerButton) {
        this.targetField = targetField;
        this.tempSelectedDate = LocalDate.now();
        this.viewDate = LocalDate.now();

        popup.setAutoHide(true);

        // Main Popup Container
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 4); " +
                "-fx-border-color: #e0e0e0; -fx-border-radius: 12; -fx-border-width: 1;");

        // Increased width slightly to prevent horizontal squeezing
        root.setPrefWidth(280);

        // --- HEADER ---
        BorderPane header = new BorderPane();
        Button prevBtn = createNavButton("<");
        Button nextBtn = createNavButton(">");
        titleBtn.setStyle("-fx-background-color: transparent; -fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand;");
        titleBtn.setOnAction(e -> handleTitleClick());

        prevBtn.setOnAction(e -> navigate(-1));
        nextBtn.setOnAction(e -> navigate(1));

        header.setLeft(prevBtn);
        header.setCenter(titleBtn);
        header.setRight(nextBtn);

        // --- BODY ---
        setupGrid(daysGrid);
        setupGrid(monthsGrid);
        setupGrid(yearsGrid);

        // --- FOOTER ---
        HBox footer = new HBox(15);
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> popup.hide());

        Button okBtn = new Button("OK");
        okBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 18;");
        okBtn.setOnAction(e -> {
            targetField.setText(tempSelectedDate.format(formatter));
            popup.hide();
        });
        footer.getChildren().addAll(cancelBtn, okBtn);

        root.getChildren().addAll(header, viewsStack, footer);
        popup.getContent().add(root);

        triggerButton.setOnAction(e -> showPopup());
    }

    private void showPopup() {
        if (!popup.isShowing()) {
            try {
                if (!targetField.getText().isEmpty()) {
                    tempSelectedDate = LocalDate.parse(targetField.getText(), formatter);
                    viewDate = tempSelectedDate;
                }
            } catch (Exception ex) { }

            switchView(ViewMode.DAYS);
            Point2D p = targetField.localToScreen(0, 0);
            if (p != null) {
                popup.show(targetField.getScene().getWindow(), p.getX(), p.getY() + targetField.getHeight() + 5);
            }
        }
    }

    private Button createNavButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: transparent; -fx-font-weight: bold; -fx-cursor: hand;");
        return b;
    }

    private void setupGrid(GridPane grid) {
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(5); grid.setVgap(5);
    }

    private void switchView(ViewMode mode) {
        currentMode = mode;
        daysGrid.setVisible(mode == ViewMode.DAYS);
        monthsGrid.setVisible(mode == ViewMode.MONTHS);
        yearsGrid.setVisible(mode == ViewMode.YEARS);

        if (mode == ViewMode.DAYS) renderDays();
        else if (mode == ViewMode.MONTHS) renderMonths();
        else if (mode == ViewMode.YEARS) renderYears();
    }

    private void navigate(int direction) {
        if (currentMode == ViewMode.DAYS) viewDate = viewDate.plusMonths(direction);
        else if (currentMode == ViewMode.MONTHS) viewDate = viewDate.plusYears(direction);
        else if (currentMode == ViewMode.YEARS) viewDate = viewDate.plusYears(direction * 12);
        switchView(currentMode);
    }

    private void handleTitleClick() {
        if (currentMode == ViewMode.DAYS) switchView(ViewMode.MONTHS);
        else if (currentMode == ViewMode.MONTHS) switchView(ViewMode.YEARS);
    }

    private Button createGridCell(String text, boolean isSelected, boolean isCurrentScope) {
        Button btn = new Button(text);

        // FIX: Forces the button to respect the size needed for the text
        btn.setMinWidth(Region.USE_PREF_SIZE);
        btn.setMinHeight(Region.USE_PREF_SIZE);

        String baseStyle = "-fx-background-radius: 18; -fx-cursor: hand; -fx-font-size: 12px; -fx-padding: 0;";
        if (isSelected) {
            btn.setStyle(baseStyle + "-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-weight: bold;");
        } else if (!isCurrentScope) {
            btn.setStyle(baseStyle + "-fx-background-color: transparent; -fx-text-fill: #c0c0c0;");
        } else {
            btn.setStyle(baseStyle + "-fx-background-color: transparent; -fx-text-fill: #333;");
            btn.setOnMouseEntered(e -> btn.setStyle(baseStyle + "-fx-background-color: #f0f0f0;"));
            btn.setOnMouseExited(e -> btn.setStyle(baseStyle + "-fx-background-color: transparent;"));
        }

        return btn;
    }

    private void renderDays() {
        daysGrid.getChildren().clear();
        titleBtn.setText(viewDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")));

        String[] weekDays = {"S", "M", "T", "W", "T", "F", "S"};
        for (int i = 0; i < 7; i++) {
            Label lbl = new Label(weekDays[i]);
            lbl.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
            lbl.setPrefSize(35, 20); // Matched width with buttons
            lbl.setAlignment(Pos.CENTER);
            daysGrid.add(lbl, i, 0);
        }

        LocalDate firstDay = YearMonth.of(viewDate.getYear(), viewDate.getMonth()).atDay(1);
        int startOffset = firstDay.getDayOfWeek().getValue() % 7;
        LocalDate currentRenderDate = firstDay.minusDays(startOffset);

        for (int row = 1; row <= 6; row++) {
            for (int col = 0; col < 7; col++) {
                boolean isSelected = currentRenderDate.equals(tempSelectedDate);
                boolean isCurrentMonth = currentRenderDate.getMonth() == viewDate.getMonth();

                Button dayBtn = createGridCell(String.valueOf(currentRenderDate.getDayOfMonth()), isSelected, isCurrentMonth);

                // FIX: Increased button size to ensure "28", "30", etc. fit comfortably
                dayBtn.setPrefSize(35, 35);

                LocalDate cellDate = currentRenderDate;
                dayBtn.setOnAction(e -> {
                    tempSelectedDate = cellDate;
                    viewDate = cellDate;
                    renderDays();
                });
                daysGrid.add(dayBtn, col, row);
                currentRenderDate = currentRenderDate.plusDays(1);
            }
        }
    }

    private void renderMonths() {
        monthsGrid.getChildren().clear();
        titleBtn.setText(String.valueOf(viewDate.getYear()));

        for (int i = 0; i < 12; i++) {
            int col = i % 4; int row = i / 4;
            boolean isSelected = (i + 1 == tempSelectedDate.getMonthValue() && viewDate.getYear() == tempSelectedDate.getYear());

            Button monthBtn = createGridCell(monthNames[i], isSelected, true);
            monthBtn.setPrefSize(60, 40); // Slightly wider

            int monthNum = i + 1;
            monthBtn.setOnAction(e -> {
                viewDate = viewDate.withMonth(monthNum);
                switchView(ViewMode.DAYS);
            });
            monthsGrid.add(monthBtn, col, row);
        }
    }

    private void renderYears() {
        yearsGrid.getChildren().clear();
        int startYear = viewDate.getYear() - (viewDate.getYear() % 12);
        titleBtn.setText(startYear + " - " + (startYear + 11));

        for (int i = 0; i < 12; i++) {
            int col = i % 4; int row = i / 4;
            int year = startYear + i;
            boolean isSelected = (year == tempSelectedDate.getYear());

            Button yearBtn = createGridCell(String.valueOf(year), isSelected, true);
            yearBtn.setPrefSize(60, 40); // Slightly wider

            yearBtn.setOnAction(e -> {
                viewDate = viewDate.withYear(year);
                switchView(ViewMode.MONTHS);
            });
            yearsGrid.add(yearBtn, col, row);
        }
    }
}