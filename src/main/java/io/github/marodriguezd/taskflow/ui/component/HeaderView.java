package io.github.marodriguezd.taskflow.ui.component;

import io.github.marodriguezd.taskflow.domain.ThemeMode;
import io.github.marodriguezd.taskflow.ui.i18n.Messages;
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
    private final Button languageButton;
    private final Button infoButton;
    private final Button closeButton;

    /** Latest applied task count, re-rendered on language switch. */
    private int lastTaskCount;

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
        iconLabel.getStyleClass().add("header-icon");

        Label titleLabel = new Label("TaskFlow");
        titleLabel.getStyleClass().add("header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        countBadge = new Label(Messages.count("header.tasks", 0));
        countBadge.getStyleClass().add("badge-count");
        // Allow the badge to shrink (ellipsis) before header buttons do — German badge text
        // ("15 Aufgaben") plus the extra language button must not push controls out of view.
        countBadge.setMinWidth(0);
        countBadge.setVisible(false);
        countBadge.managedProperty().bind(countBadge.visibleProperty());

        pinButton = new ToggleButton();
        pinButton.getStyleClass().add("icon-button");
        pinButton.setGraphic(Icons.pin(13));
        pinButton.setSelected(initialAlwaysOnTop);
        pinButton.setTooltip(new Tooltip(Messages.get("tooltip.pin")));

        historyButton = new Button();
        historyButton.getStyleClass().add("icon-button");
        historyButton.setGraphic(Icons.history(13));
        historyButton.setTooltip(new Tooltip(Messages.get("tooltip.history")));

        themeButton = new Button();
        themeButton.getStyleClass().add("icon-button");
        themeButton.setGraphic(initialTheme == ThemeMode.DARK ? Icons.sun(13) : Icons.moon(13));
        themeButton.setTooltip(new Tooltip(Messages.get("tooltip.theme")));

        languageButton = new Button();
        languageButton.getStyleClass().add("icon-button");
        languageButton.setGraphic(Icons.globe(13));
        languageButton.setTooltip(new Tooltip(Messages.get("tooltip.language")));

        infoButton = new Button();
        infoButton.getStyleClass().add("icon-button");
        infoButton.setGraphic(Icons.info(13));
        infoButton.setTooltip(new Tooltip(Messages.get("tooltip.about")));

        getChildren()
                .addAll(
                        iconLabel,
                        titleLabel,
                        spacer,
                        countBadge,
                        pinButton,
                        historyButton,
                        themeButton,
                        languageButton,
                        infoButton);

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
        lastTaskCount = count;
        if (count <= 0) {
            countBadge.setVisible(false);
        } else {
            countBadge.setText(Messages.count("header.tasks", count));
            countBadge.setVisible(true);
        }
    }

    /** Re-applies every localized text after a runtime language switch. */
    public void refreshTexts() {
        pinButton.setTooltip(new Tooltip(Messages.get("tooltip.pin")));
        historyButton.setTooltip(new Tooltip(Messages.get("tooltip.history")));
        themeButton.setTooltip(new Tooltip(Messages.get("tooltip.theme")));
        languageButton.setTooltip(new Tooltip(Messages.get("tooltip.language")));
        infoButton.setTooltip(new Tooltip(Messages.get("tooltip.about")));
        if (lastTaskCount > 0) {
            countBadge.setText(Messages.count("header.tasks", lastTaskCount));
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

    public Button getLanguageButton() {
        return languageButton;
    }

    public Button getInfoButton() {
        return infoButton;
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
