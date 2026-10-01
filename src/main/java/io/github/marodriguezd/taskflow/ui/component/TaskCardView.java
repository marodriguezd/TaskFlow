package io.github.marodriguezd.taskflow.ui.component;

import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.util.TimeFormatter;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Visual card representing a task with countdown timer, controls, and progress tracking. */
public class TaskCardView extends HBox {

    private Task task;
    private boolean isRunning;

    private final Region sideBar;
    private final Label nameLabel;
    private final Label pillLabel;
    private final Label timerLabel;
    private final Button editButton;
    private final Button deleteButton;
    private final Button doneButton;
    private final Button playButton;
    private final ProgressBarView progressBar;

    private Consumer<Task> onPlayToggle;
    private Consumer<Task> onEdit;
    private Consumer<Task> onDelete;
    private Consumer<Task> onComplete;

    public TaskCardView(Task task, boolean isRunning) {
        this.task = task;
        this.isRunning = isRunning;

        setMinHeight(86.0);
        getStyleClass().add("task-card");

        Priority pri = task.priority();

        // 1. Left priority color stripe (4px width)
        sideBar = new Region();
        sideBar.setMinWidth(4.0);
        sideBar.setMaxWidth(4.0);
        sideBar.setStyle(
                String.format(
                        "-fx-background-color: %s; -fx-background-radius: 4px 0 0 4px;",
                        pri.getHexColor()));

        // 2. Inner card container
        VBox inner = new VBox(6);
        inner.setPadding(new Insets(10, 12, 10, 12));
        HBox.setHgrow(inner, javafx.scene.layout.Priority.ALWAYS);

        // Row 1: Name + Priority Pill + Edit + Delete
        HBox row1 = new HBox(6);
        row1.setAlignment(Pos.CENTER_LEFT);

        nameLabel = new Label(task.name());
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setStyle(
                "-fx-text-fill: -fx-text-hi; -fx-font-size: 13px; -fx-font-weight: bold;");
        HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);

        pillLabel = new Label(pri.getDisplayName());
        pillLabel.setStyle(
                String.format(
                        "-fx-text-fill: %s; -fx-background-color: %s; -fx-font-size: 10px; -fx-font-weight: bold; "
                                + "-fx-background-radius: 5px; -fx-padding: 1px 6px; -fx-min-height: 18px;",
                        pri.getHexColor(), pri.getPillColor()));

        editButton = new Button();
        editButton.setMinSize(22, 22);
        editButton.setMaxSize(22, 22);
        editButton.setGraphic(Icons.edit(11));
        editButton.setTooltip(new Tooltip("Edit task"));
        editButton.getStyleClass().addAll("icon-button", "card-action-button", "card-edit-button");
        editButton.setOnAction(
                e -> {
                    if (onEdit != null) {
                        onEdit.accept(this.task);
                    }
                });

        deleteButton = new Button();
        deleteButton.setMinSize(22, 22);
        deleteButton.setMaxSize(22, 22);
        deleteButton.setGraphic(Icons.close(10));
        deleteButton.setTooltip(new Tooltip("Delete task"));
        deleteButton
                .getStyleClass()
                .addAll("icon-button", "card-action-button", "card-delete-button");
        deleteButton.setOnAction(
                e -> {
                    if (onDelete != null) {
                        onDelete.accept(this.task);
                    }
                });

        row1.getChildren().addAll(nameLabel, pillLabel, editButton, deleteButton);

        // Row 2: Timer + Mark Done + Play/Pause Button
        HBox row2 = new HBox(8);
        row2.setAlignment(Pos.CENTER_LEFT);

        timerLabel = new Label(TimeFormatter.format(task.remainingSeconds()));
        timerLabel.setStyle(
                String.format(
                        "-fx-text-fill: %s; -fx-font-size: 26px; -fx-font-weight: bold; "
                                + "-fx-font-family: 'Courier New', 'Consolas', monospace;",
                        pri.getHexColor()));
        timerLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(timerLabel, javafx.scene.layout.Priority.ALWAYS);

        doneButton = new Button("✓");
        doneButton.setMinSize(24, 24);
        doneButton.setMaxSize(24, 24);
        doneButton.setTooltip(new Tooltip("Mark as done"));
        doneButton.setStyle(
                String.format(
                        "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 12px; -fx-font-weight: bold; "
                                + "-fx-background-radius: 12px; -fx-cursor: hand; -fx-padding: 0;",
                        pri.getPillColor(), pri.getHexColor()));
        doneButton.setOnAction(
                e -> {
                    if (onComplete != null) {
                        onComplete.accept(this.task);
                    }
                });

        playButton = new Button();
        playButton.setMinSize(36, 36);
        playButton.setMaxSize(36, 36);
        playButton.setOnAction(
                e -> {
                    if (task.isExpired()) {
                        if (onDelete != null) {
                            onDelete.accept(this.task);
                        }
                    } else if (onPlayToggle != null) {
                        onPlayToggle.accept(this.task);
                    }
                });
        updatePlayButtonStyle();

        row2.getChildren().addAll(timerLabel, doneButton, playButton);

        // Row 3: Progress Bar
        progressBar =
                new ProgressBarView(
                        task.totalSeconds(), task.remainingSeconds(), pri.getHexColor());

        inner.getChildren().addAll(row1, row2, progressBar);
        getChildren().addAll(sideBar, inner);

        setupHoverEffects();
    }

    public Task getTask() {
        return task;
    }

    public void updateTask(Task updatedTask) {
        this.task = updatedTask;
        nameLabel.setText(task.name());
        timerLabel.setText(TimeFormatter.format(task.remainingSeconds()));
        progressBar.setProgress(task.remainingSeconds(), task.totalSeconds());
        updatePlayButtonStyle();

        if (task.isExpired()) {
            setStyle(
                    String.format(
                            "-fx-border-color: %s88; -fx-border-width: 1px 1px 1px 0; "
                                    + "-fx-border-radius: 0 10px 10px 0; -fx-background-radius: 0 10px 10px 0;",
                            task.priority().getHexColor()));
        }
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
        updatePlayButtonStyle();
    }

    private void updatePlayButtonStyle() {
        Priority pri = task.priority();
        if (task.isExpired()) {
            playButton.setText("✓");
            playButton.setStyle(
                    "-fx-background-color: rgba(96, 96, 120, 0.2); -fx-text-fill: -fx-text-lo; "
                            + "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 18px; -fx-cursor: hand;");
        } else {
            String icon = isRunning ? "❚❚" : "▶";
            int fontSize = isRunning ? 13 : 15;
            int paddingLeft = isRunning ? 0 : 2;
            playButton.setText(icon);
            playButton.setStyle(
                    String.format(
                            "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: %dpx; -fx-font-weight: 900; "
                                    + "-fx-background-radius: 18px; -fx-cursor: hand; -fx-padding: 0 0 0 %dpx;",
                            pri.getPillColor(), pri.getHexColor(), fontSize, paddingLeft));
        }
    }

    private void setupHoverEffects() {
        setOnMouseEntered(
                e -> {
                    if (!task.isExpired()) {
                        setStyle(
                                String.format(
                                        "-fx-border-color: %s; -fx-border-width: 1px 1px 1px 0; "
                                                + "-fx-border-radius: 0 10px 10px 0; -fx-background-radius: 0 10px 10px 0;",
                                        task.priority().getHexColor()));
                    }
                });

        setOnMouseExited(
                e -> {
                    if (!task.isExpired()) {
                        setStyle(
                                "-fx-border-color: -fx-border; -fx-border-width: 1px 1px 1px 0; "
                                        + "-fx-border-radius: 0 10px 10px 0; -fx-background-radius: 0 10px 10px 0;");
                    }
                });
    }

    public void setOnPlayToggle(Consumer<Task> onPlayToggle) {
        this.onPlayToggle = onPlayToggle;
    }

    public void setOnEdit(Consumer<Task> onEdit) {
        this.onEdit = onEdit;
    }

    public void setOnDelete(Consumer<Task> onDelete) {
        this.onDelete = onDelete;
    }

    public void setOnComplete(Consumer<Task> onComplete) {
        this.onComplete = onComplete;
    }
}
