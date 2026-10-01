package io.github.marodriguezd.taskflow.ui.component;

import io.github.marodriguezd.taskflow.ui.i18n.Messages;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Visual placeholder displayed when no active tasks exist. */
public class EmptyStateView extends VBox {

    private final Label titleLabel;
    private final Label hintLabel;

    public EmptyStateView() {
        setAlignment(Pos.CENTER);
        setSpacing(6);
        getStyleClass().add("empty-state");

        Label iconLabel = new Label();
        iconLabel.setGraphic(Icons.clipboard(36));
        iconLabel.setStyle("-fx-background-color: transparent;");

        titleLabel = new Label(Messages.get("empty.title"));
        titleLabel.setStyle(
                "-fx-text-fill: -fx-text-mid; -fx-font-size: 13px; -fx-font-weight: bold;");

        hintLabel = new Label(Messages.get("empty.hint"));
        // Wrap long translations (e.g. German) instead of clipping at the 300px min width
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(260.0);
        hintLabel.setStyle("-fx-text-fill: -fx-text-lo; -fx-font-size: 11px;");

        getChildren().addAll(iconLabel, titleLabel, hintLabel);
    }

    /** Re-applies the localized texts after a runtime language switch. */
    public void refreshTexts() {
        titleLabel.setText(Messages.get("empty.title"));
        hintLabel.setText(Messages.get("empty.hint"));
    }
}
