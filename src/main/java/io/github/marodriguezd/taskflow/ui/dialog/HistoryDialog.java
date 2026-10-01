package io.github.marodriguezd.taskflow.ui.dialog;

import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.service.TaskService;
import io.github.marodriguezd.taskflow.ui.component.Icons;
import io.github.marodriguezd.taskflow.ui.theme.ThemeManager;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Modal dialog displaying the task completion and deletion history. */
public class HistoryDialog {

    private final Stage stage;
    private final TaskService taskService;
    private final Consumer<HistoryItem> onRestoreRequested;
    private double dragOffsetX;
    private double dragOffsetY;

    public HistoryDialog(
            Stage owner,
            ThemeManager themeManager,
            TaskService taskService,
            Consumer<HistoryItem> onRestoreRequested) {
        this.taskService = taskService;
        this.onRestoreRequested = onRestoreRequested;

        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);

        VBox card = new VBox(12);
        card.getStyleClass().add("dialog-card");
        card.setPadding(new Insets(16, 18, 16, 18));
        card.setPrefSize(420, 420);

        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Historial");
        title.setStyle("-fx-text-fill: -fx-text-hi; -fx-font-size: 16px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button closeBtn = new Button();
        closeBtn.getStyleClass().addAll("icon-button", "close-button");
        closeBtn.setGraphic(Icons.close(10));
        closeBtn.setOnAction(e -> stage.close());

        header.getChildren().addAll(title, spacer, closeBtn);

        // Scroll Area
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        VBox contentList = new VBox(8);
        contentList.setPadding(new Insets(2, 6, 2, 2));

        List<HistoryItem> history = taskService.getHistory();
        if (history.isEmpty()) {
            Label emptyLabel = new Label("No hay tareas en el historial");
            emptyLabel.setStyle("-fx-text-fill: -fx-text-mid; -fx-font-size: 12px;");
            emptyLabel.setAlignment(Pos.CENTER);
            emptyLabel.setMaxWidth(Double.MAX_VALUE);
            contentList.getChildren().add(emptyLabel);
        } else {
            for (HistoryItem item : history) {
                contentList.getChildren().add(createHistoryItemRow(item));
            }
        }

        scrollPane.setContent(contentList);
        card.getChildren().addAll(header, scrollPane);

        Scene scene = new Scene(card);
        themeManager.registerScene(scene);

        scene.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        stage.close();
                    }
                });

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

    private VBox createHistoryItemRow(HistoryItem item) {
        VBox box = new VBox(6);
        box.getStyleClass().add("history-card");

        Label nameLabel = new Label(item.name());
        nameLabel.setStyle(
                "-fx-text-fill: -fx-text-hi; -fx-font-size: 13px; -fx-font-weight: bold;");
        nameLabel.setWrapText(true);

        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);

        String infoText =
                String.format("%s · %s", item.getFormattedDate(), item.getModeDescription());
        Label infoLabel = new Label(infoText);
        infoLabel.setStyle("-fx-text-fill: -fx-text-mid; -fx-font-size: 10px;");
        infoLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(infoLabel, javafx.scene.layout.Priority.ALWAYS);

        Button restoreBtn = new Button("Restaurar");
        restoreBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: -fx-accent-lt; "
                        + "-fx-border-color: -fx-border; -fx-border-radius: 7px; -fx-background-radius: 7px; "
                        + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3px 8px; -fx-cursor: hand;");
        restoreBtn.setOnAction(
                e -> {
                    if (onRestoreRequested != null) {
                        onRestoreRequested.accept(item);
                    }
                    stage.close();
                });

        footer.getChildren().addAll(infoLabel, restoreBtn);
        box.getChildren().addAll(nameLabel, footer);
        return box;
    }

    public void showAndWait() {
        if (stage.getOwner() != null) {
            stage.setX(Math.max(0, stage.getOwner().getX() - 330));
            stage.setY(stage.getOwner().getY() + 40);
        }
        stage.showAndWait();
    }
}
