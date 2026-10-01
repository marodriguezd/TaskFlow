package io.github.marodriguezd.taskflow.ui.component;

import io.github.marodriguezd.taskflow.domain.ThemeMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Custom draggable header bar containing navigation actions and system buttons. */
public class HeaderView extends HBox {

    private final Label countBadge;
    private final ToggleButton pinButton;
    private final Button historyButton;
    private final Button themeButton;
    private final Button closeButton;

    private double dragOffsetX;
    private double dragOffsetY;

    public HeaderView(
            Stage stage, boolean isFrameless, ThemeMode initialTheme, boolean initialAlwaysOnTop) {
        setPrefHeight(52.0);
        setMinHeight(52.0);
        setMaxHeight(52.0);
        setPadding(new Insets(0, 12, 0, 16));
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("header-bar");

        Label iconLabel = new Label("⏱");
        iconLabel.setStyle(
                "-fx-text-fill: -fx-accent-lt; -fx-font-size: 16px; -fx-background-color: transparent;");

        Label titleLabel = new Label("TaskFlow");
        titleLabel.setStyle(
                "-fx-text-fill: -fx-text-hi; -fx-font-size: 14px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        countBadge = new Label("0 tasks");
        countBadge.getStyleClass().add("badge-count");
        countBadge.setVisible(false);
        countBadge.managedProperty().bind(countBadge.visibleProperty());

        pinButton = new ToggleButton();
        pinButton.getStyleClass().add("icon-button");
        pinButton.setGraphic(Icons.pin(13));
        pinButton.setSelected(initialAlwaysOnTop);
        pinButton.setTooltip(new Tooltip("Toggle always on top"));

        historyButton = new Button();
        historyButton.getStyleClass().add("icon-button");
        historyButton.setGraphic(Icons.history(13));
        historyButton.setTooltip(new Tooltip("View task history"));

        themeButton = new Button();
        themeButton.getStyleClass().add("icon-button");
        themeButton.setGraphic(initialTheme == ThemeMode.DARK ? Icons.sun(13) : Icons.moon(13));
        themeButton.setTooltip(new Tooltip("Switch light/dark theme"));

        getChildren()
                .addAll(
                        iconLabel,
                        titleLabel,
                        spacer,
                        countBadge,
                        pinButton,
                        historyButton,
                        themeButton);

        if (isFrameless) {
            closeButton = new Button();
            closeButton.getStyleClass().addAll("icon-button", "close-button");
            closeButton.setGraphic(Icons.close(10));
            getChildren().add(closeButton);
        } else {
            closeButton = null;
        }

        setupWindowDragging(stage);
    }

    public void updateTaskCount(int count) {
        if (count <= 0) {
            countBadge.setVisible(false);
        } else {
            countBadge.setText(count + (count == 1 ? " task" : " tasks"));
            countBadge.setVisible(true);
        }
    }

    public void updateThemeIcon(ThemeMode mode) {
        themeButton.setGraphic(mode == ThemeMode.DARK ? Icons.sun(13) : Icons.moon(13));
    }

    public ToggleButton getPinButton() {
        return pinButton;
    }

    public Button getHistoryButton() {
        return historyButton;
    }

    public Button getThemeButton() {
        return themeButton;
    }

    public Button getCloseButton() {
        return closeButton;
    }

    private void setupWindowDragging(Stage stage) {
        setOnMousePressed(
                e -> {
                    dragOffsetX = e.getSceneX();
                    dragOffsetY = e.getSceneY();
                });

        setOnMouseDragged(
                e -> {
                    stage.setX(e.getScreenX() - dragOffsetX);
                    stage.setY(e.getScreenY() - dragOffsetY);
                });
    }
}
