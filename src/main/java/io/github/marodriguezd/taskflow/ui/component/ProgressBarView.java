package io.github.marodriguezd.taskflow.ui.component;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

/**
 * Custom modern progress bar matching TaskFlow's visual design. Features rounded pill corners and
 * priority-colored gradient fills.
 */
public class ProgressBarView extends Pane {

    private final Rectangle trackRect;
    private final Rectangle fillRect;
    private final String priorityColorHex;

    private int totalSeconds;
    private int currentSeconds;

    public ProgressBarView(int totalSeconds, int currentSeconds, String priorityColorHex) {
        this.totalSeconds = Math.max(1, totalSeconds);
        this.currentSeconds = Math.max(0, currentSeconds);
        this.priorityColorHex = priorityColorHex != null ? priorityColorHex : "#7c6af7";

        setPrefHeight(4.0);
        setMinHeight(4.0);
        setMaxHeight(4.0);

        trackRect = new Rectangle();
        trackRect.setArcWidth(4.0);
        trackRect.setArcHeight(4.0);
        trackRect.setFill(Color.web("#ffffff", 0.12));

        fillRect = new Rectangle();
        fillRect.setArcWidth(4.0);
        fillRect.setArcHeight(4.0);

        getChildren().addAll(trackRect, fillRect);
        updateProgress();
    }

    public void setProgress(int current, int total) {
        this.currentSeconds = Math.max(0, current);
        this.totalSeconds = Math.max(1, total);
        updateProgress();
    }

    public void updateProgress() {
        double width = getWidth();
        if (width <= 0) {
            width = getPrefWidth() > 0 ? getPrefWidth() : 200.0;
        }

        trackRect.setWidth(width);
        trackRect.setHeight(4.0);

        double fraction = Math.clamp((double) currentSeconds / totalSeconds, 0.0, 1.0);
        double fillWidth = width * fraction;
        fillRect.setWidth(fillWidth);
        fillRect.setHeight(4.0);

        if (fillWidth > 0) {
            Color baseColor = Color.web(priorityColorHex);
            Color darkerColor = baseColor.deriveColor(0, 1.0, 0.7, 1.0);
            LinearGradient gradient =
                    new LinearGradient(
                            0,
                            0,
                            fillWidth,
                            0,
                            false,
                            CycleMethod.NO_CYCLE,
                            new Stop(0.0, darkerColor),
                            new Stop(1.0, baseColor));
            fillRect.setFill(gradient);
            fillRect.setVisible(true);
        } else {
            fillRect.setVisible(false);
        }
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        updateProgress();
    }
}
