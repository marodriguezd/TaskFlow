package io.github.marodriguezd.taskflow.ui.dialog;

import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.ui.i18n.Messages;
import io.github.marodriguezd.taskflow.ui.i18n.PriorityLabels;
import io.github.marodriguezd.taskflow.ui.theme.ThemeManager;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;

/** Modal dialog for creating a new Task. */
public class AddTaskDialog {

    public record TaskFormData(String name, Priority priority, int minutes) {}

    protected final Stage stage;
    protected final TextField nameField;
    protected final Spinner<Integer> minutesSpinner;
    protected final ComboBox<Priority> priorityCombo;
    protected final Button confirmButton;
    protected final Button cancelButton;
    protected final Label titleLabel;

    protected TaskFormData result;
    private double dragOffsetX;
    private double dragOffsetY;

    public AddTaskDialog(Stage owner, ThemeManager themeManager) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);

        VBox card = new VBox(12);
        card.getStyleClass().add("dialog-card");
        card.setPadding(new Insets(18, 18, 18, 18));
        // 300px floor kept from 1.0.0; content-driven beyond that so longer translations
        // (German/Italian labels) can never clip inside the dialog.
        card.setMinWidth(300.0);

        titleLabel = new Label(Messages.get("dialog.add.title"));
        titleLabel.getStyleClass().add("dialog-title");

        nameField = new TextField();
        nameField.setPromptText(Messages.get("dialog.name.prompt"));

        HBox optionsRow = new HBox(10);
        optionsRow.setAlignment(Pos.CENTER_LEFT);

        VBox timeCol = new VBox(4);
        Label timeLabel = new Label(Messages.get("dialog.minutes"));
        timeLabel.getStyleClass().add("dialog-label");
        minutesSpinner = new Spinner<>();
        minutesSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 25));
        minutesSpinner.setEditable(true);
        minutesSpinner.setPrefWidth(120.0);
        timeCol.getChildren().addAll(timeLabel, minutesSpinner);
        HBox.setHgrow(timeCol, javafx.scene.layout.Priority.ALWAYS);

        VBox priCol = new VBox(4);
        Label priLabel = new Label(Messages.get("dialog.priority"));
        priLabel.getStyleClass().add("dialog-label");
        priorityCombo = new ComboBox<>();
        priorityCombo.getItems().addAll(Priority.HIGH, Priority.MEDIUM, Priority.LOW);
        priorityCombo.setValue(Priority.MEDIUM);
        priorityCombo.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(Priority p) {
                        return PriorityLabels.label(p);
                    }

                    @Override
                    public Priority fromString(String string) {
                        return PriorityLabels.parse(string);
                    }
                });
        priorityCombo.setPrefWidth(130.0);
        priCol.getChildren().addAll(priLabel, priorityCombo);
        HBox.setHgrow(priCol, javafx.scene.layout.Priority.ALWAYS);

        optionsRow.getChildren().addAll(timeCol, priCol);

        HBox buttonRow = new HBox(8);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        cancelButton = new Button(Messages.get("dialog.cancel"));
        cancelButton.getStyleClass().add("btn-secondary");
        cancelButton.setOnAction(e -> stage.close());
        HBox.setHgrow(cancelButton, javafx.scene.layout.Priority.ALWAYS);
        cancelButton.setMaxWidth(Double.MAX_VALUE);

        confirmButton = new Button(Messages.get("dialog.add"));
        confirmButton.getStyleClass().addAll("btn-primary", "dialog-confirm-small");
        confirmButton.setOnAction(e -> handleConfirm());
        HBox.setHgrow(confirmButton, javafx.scene.layout.Priority.ALWAYS);
        confirmButton.setMaxWidth(Double.MAX_VALUE);

        buttonRow.getChildren().addAll(cancelButton, confirmButton);

        card.getChildren().addAll(titleLabel, nameField, optionsRow, buttonRow);

        Scene scene = new Scene(card);
        themeManager.registerScene(scene);

        // Keyboard shortcuts: Enter to submit, Esc to cancel
        scene.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER) {
                        handleConfirm();
                    } else if (event.getCode() == KeyCode.ESCAPE) {
                        stage.close();
                    }
                });

        // Window drag handling
        card.setOnMousePressed(
                e -> {
                    dragOffsetX = e.getSceneX();
                    dragOffsetY = e.getSceneY();
                });
        card.setOnMouseDragged(
                e -> {
                    stage.setX(e.getScreenX() - dragOffsetX);
                    stage.setY(e.getScreenY() - dragOffsetY);
                });

        stage.setScene(scene);
    }

    protected void handleConfirm() {
        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        if (name.isEmpty()) {
            nameField.requestFocus();
            return;
        }

        int minutes = minutesSpinner.getValue() != null ? minutesSpinner.getValue() : 25;
        Priority priority =
                priorityCombo.getValue() != null ? priorityCombo.getValue() : Priority.MEDIUM;

        result = new TaskFormData(name, priority, minutes);
        stage.close();
    }

    public Optional<TaskFormData> showAndWait() {
        if (stage.getOwner() != null) {
            stage.setX(Math.max(0, stage.getOwner().getX() - 310));
            stage.setY(stage.getOwner().getY() + 60);
        }
        stage.showAndWait();
        return Optional.ofNullable(result);
    }
}
