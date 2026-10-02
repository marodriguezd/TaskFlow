package io.github.marodriguezd.taskflow.ui.component;

import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.ui.i18n.Messages;
import io.github.marodriguezd.taskflow.ui.i18n.PriorityLabels;
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
        getStyleClass().addAll("task-card", "priority-" + task.priority().name().toLowerCase());

        Priority pri = task.priority();

        // 1. Left priority color stripe (4px width)
        sideBar = new Region();
        sideBar.setMinWidth(4.0);
        sideBar.setMaxWidth(4.0);
        sideBar.getStyleClass().addAll("priority-sidebar", "priority-" + pri.name().toLowerCase());

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
        nameLabel.getStyleClass().add("task-name");
        HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);

        pillLabel = new Label(PriorityLabels.label(pri));
        pillLabel.getStyleClass().addAll("priority-pill", "priority-" + pri.name().toLowerCase());

        editButton = new Button();
        editButton.setMinSize(22, 22);
        editButton.setMaxSize(22, 22);
        editButton.setGraphic(Icons.edit(11));
        editButton.setTooltip(new Tooltip(Messages.get("tooltip.edit")));
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
        deleteButton.setTooltip(new Tooltip(Messages.get("tooltip.delete")));
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
        timerLabel.getStyleClass().addAll("task-timer", "priority-" + pri.name().toLowerCase());
        timerLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(timerLabel, javafx.scene.layout.Priority.ALWAYS);

        doneButton = new Button("✓");
        doneButton.setMinSize(24, 24);
        doneButton.setMaxSize(24, 24);
        doneButton.setTooltip(new Tooltip(Messages.get("tooltip.done")));
        doneButton
                .getStyleClass()
                .addAll("complete-button", "priority-" + pri.name().toLowerCase());
        doneButton.setOnAction(
                e -> {
                    if (onComplete != null) {
                        onComplete.accept(this.task);
                    }
                });

        playButton = new Button();
        playButton.setMinSize(36, 36);
        playButton.setMaxSize(36, 36);
        playButton.getStyleClass().add("play-button");
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
        pillLabel.setText(PriorityLabels.label(task.priority()));
        getStyleClass().removeAll("priority-high", "priority-medium", "priority-low");
        getStyleClass().add("priority-" + task.priority().name().toLowerCase());
        sideBar.getStyleClass().removeAll("priority-high", "priority-medium", "priority-low");
        sideBar.getStyleClass().add("priority-" + task.priority().name().toLowerCase());
        pillLabel.getStyleClass().removeAll("priority-high", "priority-medium", "priority-low");
        pillLabel.getStyleClass().add("priority-" + task.priority().name().toLowerCase());
        timerLabel.getStyleClass().removeAll("priority-high", "priority-medium", "priority-low");
        timerLabel.getStyleClass().add("priority-" + task.priority().name().toLowerCase());
        doneButton.getStyleClass().removeAll("priority-high", "priority-medium", "priority-low");
        doneButton.getStyleClass().add("priority-" + task.priority().name().toLowerCase());
        timerLabel.setText(TimeFormatter.format(task.remainingSeconds()));
        progressBar.setProgress(task.remainingSeconds(), task.totalSeconds());
        updatePlayButtonStyle();

        getStyleClass().removeAll("task-card-expired", "task-card-hover");
        if (task.isExpired()) {
            getStyleClass().add("task-card-expired");
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
            playButton.getStyleClass().remove("play-button");
            playButton
                    .getStyleClass()
                    .removeAll(
                            "priority-high",
                            "priority-medium",
                            "priority-low",
                            "play-button-running");
            playButton.getStyleClass().remove("play-button-paused");
            playButton.getStyleClass().add("play-button-expired");
        } else {
            playButton.setText(isRunning ? "❚❚" : "▶");
            playButton.getStyleClass().remove("play-button-expired");
            playButton
                    .getStyleClass()
                    .removeAll(
                            "priority-high",
                            "priority-medium",
                            "priority-low",
                            "play-button-running",
                            "play-button-paused");
            playButton
                    .getStyleClass()
                    .addAll(
                            "play-button",
                            "priority-" + pri.name().toLowerCase(),
                            isRunning ? "play-button-running" : "play-button-paused");
        }
    }

    private void setupHoverEffects() {
        setOnMouseEntered(
                e -> {
                    if (!task.isExpired()) {
                        getStyleClass().add("task-card-hover");
                    }
                });

        setOnMouseExited(
                e -> {
                    if (!task.isExpired()) {
                        getStyleClass().remove("task-card-hover");
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
