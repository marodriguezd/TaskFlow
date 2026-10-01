package io.github.marodriguezd.taskflow.ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Visual placeholder displayed when no active tasks exist. */
public class EmptyStateView extends VBox {

    public EmptyStateView() {
        setAlignment(Pos.CENTER);
        setSpacing(6);
        getStyleClass().add("empty-state");

        Label iconLabel = new Label();
        iconLabel.setGraphic(Icons.clipboard(36));
        iconLabel.setStyle("-fx-background-color: transparent;");

        Label titleLabel = new Label("No tasks yet");
        titleLabel.setStyle(
                "-fx-text-fill: -fx-text-mid; -fx-font-size: 13px; -fx-font-weight: bold;");

        Label hintLabel = new Label("Press + New task to get started");
        hintLabel.setStyle("-fx-text-fill: -fx-text-lo; -fx-font-size: 11px;");

        getChildren().addAll(iconLabel, titleLabel, hintLabel);
    }
}
