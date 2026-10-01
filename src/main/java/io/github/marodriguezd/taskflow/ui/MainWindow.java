package io.github.marodriguezd.taskflow.ui;

import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import io.github.marodriguezd.taskflow.persistence.PreferenceRepository;
import io.github.marodriguezd.taskflow.service.PlatformService;
import io.github.marodriguezd.taskflow.service.TaskService;
import io.github.marodriguezd.taskflow.service.TimerService;
import io.github.marodriguezd.taskflow.ui.component.EmptyStateView;
import io.github.marodriguezd.taskflow.ui.component.HeaderView;
import io.github.marodriguezd.taskflow.ui.component.TaskCardView;
import io.github.marodriguezd.taskflow.ui.dialog.AddTaskDialog;
import io.github.marodriguezd.taskflow.ui.dialog.EditTaskDialog;
import io.github.marodriguezd.taskflow.ui.dialog.HistoryDialog;
import io.github.marodriguezd.taskflow.ui.theme.ThemeManager;
import io.github.marodriguezd.taskflow.ui.theme.UIConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main application window coordinating layout, task card rendering, and user interactions. */
public class MainWindow {

    private static final Logger log = LoggerFactory.getLogger(MainWindow.class);

    private final Stage stage;
    private final TaskService taskService;
    private final TimerService timerService;
    private final PreferenceRepository preferenceRepository;
    private final PlatformService platformService;
    private final ThemeManager themeManager;

    private UserPreferences preferences;
    private final HeaderView headerView;
    private final EmptyStateView emptyStateView;
    private final ScrollPane scrollPane;
    private final VBox taskList;
    private final Map<Long, TaskCardView> cardMap = new HashMap<>();

    // Frameless resize state
    private boolean isResizing = false;
    private int resizeMode = 0; // bitmask: 1=left, 2=right, 4=top, 8=bottom
    private double resizeStartScreenX;
    private double resizeStartScreenY;
    private double resizeStartWidth;
    private double resizeStartHeight;
    private double resizeStartX;
    private double resizeStartY;

    public MainWindow(
            Stage stage,
            TaskService taskService,
            TimerService timerService,
            PreferenceRepository preferenceRepository,
            PlatformService platformService,
            ThemeManager themeManager) {
        this.stage = stage;
        this.taskService = taskService;
        this.timerService = timerService;
        this.preferenceRepository = preferenceRepository;
        this.platformService = platformService;
        this.themeManager = themeManager;

        this.preferences =
                preferenceRepository.loadPreferences(platformService.getDefaultAlwaysOnTop());

        boolean frameless = platformService.usesFramelessWindow();
        if (frameless) {
            stage.initStyle(StageStyle.UNDECORATED);
        }

        stage.setTitle("TaskFlow");
        stage.setAlwaysOnTop(preferences.alwaysOnTop());

        // Configure min/max constraints
        stage.setMinWidth(UIConstants.PANEL_MIN_WIDTH);
        stage.setMinHeight(UIConstants.PANEL_MIN_HEIGHT);
        if (!platformService.isWindows()) {
            stage.setMaxWidth(UIConstants.PANEL_MAX_WIDTH);
            stage.setMaxHeight(UIConstants.PANEL_MAX_HEIGHT);
        }

        VBox root = new VBox();
        root.getStyleClass().add("main-panel");
        if (frameless) {
            root.setStyle(
                    "-fx-background-radius: 16px; -fx-border-radius: 16px; -fx-border-width: 1px;");
        }

        // 1. Header
        headerView =
                new HeaderView(stage, frameless, preferences.theme(), preferences.alwaysOnTop());
        headerView.getPinButton().setOnAction(e -> toggleAlwaysOnTop());
        headerView.getHistoryButton().setOnAction(e -> openHistoryDialog());
        headerView.getThemeButton().setOnAction(e -> toggleTheme());
        if (headerView.getCloseButton() != null) {
            headerView.getCloseButton().setOnAction(e -> closeApplication());
        }

        // 2. Center: StackPane with Task List and Empty State
        StackPane centerStack = new StackPane();
        VBox.setVgrow(centerStack, javafx.scene.layout.Priority.ALWAYS);

        emptyStateView = new EmptyStateView();

        taskList = new VBox(8);
        taskList.setPadding(new Insets(10, 10, 10, 10));

        scrollPane = new ScrollPane(taskList);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        centerStack.getChildren().addAll(emptyStateView, scrollPane);

        // 3. Footer
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(10, 12, 10, 12));
        footer.setPrefHeight(58.0);
        footer.getStyleClass().add("footer-bar");
        if (frameless) {
            footer.setStyle("-fx-background-radius: 0 0 16px 16px;");
        }

        Button addButton = new Button("＋  New task");
        addButton.getStyleClass().add("btn-primary");
        addButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(addButton, javafx.scene.layout.Priority.ALWAYS);
        addButton.setOnAction(e -> openAddTaskDialog());
        footer.getChildren().add(addButton);

        root.getChildren().addAll(headerView, centerStack, footer);

        Scene scene = new Scene(root);
        themeManager.registerScene(scene);
        stage.setScene(scene);

        // Timer event wiring
        timerService.addTickListener(this::onTimerTick);
        timerService.addRunningStateListener(this::onTimerRunningChanged);
        timerService.addCompletionListener(this::onTimerCompleted);

        // Geometry & close handling
        restoreWindowGeometry();
        setupGeometryPersistence();

        if (frameless) {
            setupFramelessEdgeResize(scene, root);
        }

        renderTasks();
    }

    private void renderTasks() {
        taskList.getChildren().clear();
        cardMap.clear();

        List<Task> tasks = taskService.getAllTasks();
        int count = tasks.size();

        headerView.updateTaskCount(count);
        scrollPane.setVisible(count > 0);
        emptyStateView.setVisible(count == 0);

        Long runningTaskId = timerService.getRunningTaskId().orElse(null);

        for (Task task : tasks) {
            boolean isRunning = runningTaskId != null && runningTaskId.equals(task.id());
            TaskCardView card = new TaskCardView(task, isRunning);

            card.setOnPlayToggle(t -> timerService.toggle(t));
            card.setOnEdit(this::openEditTaskDialog);
            card.setOnDelete(
                    t -> {
                        taskService.deleteTask(t.id(), true);
                        renderTasks();
                    });
            card.setOnComplete(
                    t -> {
                        taskService.completeTaskManually(t.id());
                        renderTasks();
                    });

            cardMap.put(task.id(), card);
            taskList.getChildren().add(card);
        }
    }

    private void onTimerTick(Task activeTask) {
        Platform.runLater(
                () -> {
                    TaskCardView card = cardMap.get(activeTask.id());
                    if (card != null) {
                        card.updateTask(activeTask);
                    }
                });
    }

    private void onTimerRunningChanged(Long taskId, Boolean isRunning) {
        Platform.runLater(
                () -> {
                    TaskCardView card = cardMap.get(taskId);
                    if (card != null) {
                        card.setRunning(isRunning);
                    }
                });
    }

    private void onTimerCompleted(Task completedTask) {
        Platform.runLater(
                () -> {
                    TaskCardView card = cardMap.get(completedTask.id());
                    if (card != null) {
                        card.updateTask(completedTask.withRemainingSeconds(0));
                        card.setRunning(false);
                    }
                });
    }

    private void openAddTaskDialog() {
        AddTaskDialog dialog = new AddTaskDialog(stage, themeManager);
        dialog.showAndWait()
                .ifPresent(
                        data -> {
                            taskService.createTask(data.name(), data.priority(), data.minutes());
                            renderTasks();
                        });
    }

    private void openEditTaskDialog(Task task) {
        EditTaskDialog dialog = new EditTaskDialog(stage, themeManager, task);
        dialog.showAndWait()
                .ifPresent(
                        data -> {
                            taskService.updateTask(
                                    task.id(), data.name(), data.priority(), data.minutes());
                            renderTasks();
                        });
    }

    private void openHistoryDialog() {
        HistoryDialog dialog =
                new HistoryDialog(stage, themeManager, taskService, this::restoreTaskFromHistory);
        dialog.showAndWait();
    }

    private void restoreTaskFromHistory(HistoryItem item) {
        taskService.restoreTaskFromHistory(item.id());
        renderTasks();
    }

    private void toggleAlwaysOnTop() {
        boolean selected = headerView.getPinButton().isSelected();
        stage.setAlwaysOnTop(selected);
        preferences = preferences.withAlwaysOnTop(selected);
        preferenceRepository.savePreferences(preferences);
    }

    private void toggleTheme() {
        themeManager.toggleTheme();
        preferences = preferences.withTheme(themeManager.getCurrentTheme());
        preferenceRepository.savePreferences(preferences);
        headerView.updateThemeIcon(themeManager.getCurrentTheme());
    }

    private void restoreWindowGeometry() {
        var bounds = Screen.getPrimary().getVisualBounds();
        Optional<WindowGeometry> savedGeo = preferenceRepository.loadGeometry();
        if (savedGeo.isPresent()) {
            WindowGeometry geo = savedGeo.get();
            // Ensure saved geometry is visibly within screen bounds
            if (geo.x() >= 0
                    && geo.y() >= 0
                    && geo.x() < bounds.getMaxX() - 50
                    && geo.y() < bounds.getMaxY() - 50) {
                stage.setX(geo.x());
                stage.setY(geo.y());
                stage.setWidth(Math.max(UIConstants.PANEL_MIN_WIDTH, geo.width()));
                stage.setHeight(Math.max(UIConstants.PANEL_MIN_HEIGHT, geo.height()));
                return;
            }
        }

        // Default center on primary screen
        double width = UIConstants.DEFAULT_WIDTH;
        double height = UIConstants.DEFAULT_HEIGHT;
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(Math.max(0, (bounds.getWidth() - width) / 2));
        stage.setY(Math.max(0, (bounds.getHeight() - height) / 2));
    }

    private void setupGeometryPersistence() {
        stage.xProperty().addListener((obs, oldVal, newVal) -> saveGeometry());
        stage.yProperty().addListener((obs, oldVal, newVal) -> saveGeometry());
        stage.widthProperty().addListener((obs, oldVal, newVal) -> saveGeometry());
        stage.heightProperty().addListener((obs, oldVal, newVal) -> saveGeometry());
        stage.setOnCloseRequest(e -> closeApplication());
    }

    private void saveGeometry() {
        if (stage.isShowing() && !stage.isIconified()) {
            double x = stage.getX();
            double y = stage.getY();
            double w = stage.getWidth();
            double h = stage.getHeight();
            // Ignore negative/offscreen coordinates caused by tiling window managers (dwm, etc.)
            if (x >= 0
                    && y >= 0
                    && w >= UIConstants.PANEL_MIN_WIDTH
                    && h >= UIConstants.PANEL_MIN_HEIGHT) {
                preferenceRepository.saveGeometry(new WindowGeometry(x, y, w, h));
            }
        }
    }

    private void setupFramelessEdgeResize(Scene scene, VBox root) {
        int margin = UIConstants.RESIZE_MARGIN;

        scene.setOnMouseMoved(
                e -> {
                    if (isResizing) return;
                    double x = e.getSceneX();
                    double y = e.getSceneY();
                    double w = scene.getWidth();
                    double h = scene.getHeight();

                    int mode = 0;
                    if (x <= margin) mode |= 1;
                    if (x >= w - margin) mode |= 2;
                    if (y <= margin) mode |= 4;
                    if (y >= h - margin) mode |= 8;

                    Cursor cursor =
                            switch (mode) {
                                case 1 | 4, 2 | 8 -> Cursor.NW_RESIZE;
                                case 2 | 4, 1 | 8 -> Cursor.NE_RESIZE;
                                case 1, 2 -> Cursor.H_RESIZE;
                                case 4, 8 -> Cursor.V_RESIZE;
                                default -> Cursor.DEFAULT;
                            };
                    scene.setCursor(cursor);
                });

        scene.setOnMousePressed(
                e -> {
                    double x = e.getSceneX();
                    double y = e.getSceneY();
                    double w = scene.getWidth();
                    double h = scene.getHeight();

                    resizeMode = 0;
                    if (x <= margin) resizeMode |= 1;
                    if (x >= w - margin) resizeMode |= 2;
                    if (y <= margin) resizeMode |= 4;
                    if (y >= h - margin) resizeMode |= 8;

                    if (resizeMode != 0) {
                        isResizing = true;
                        resizeStartScreenX = e.getScreenX();
                        resizeStartScreenY = e.getScreenY();
                        resizeStartWidth = stage.getWidth();
                        resizeStartHeight = stage.getHeight();
                        resizeStartX = stage.getX();
                        resizeStartY = stage.getY();
                    }
                });

        scene.setOnMouseDragged(
                e -> {
                    if (!isResizing || resizeMode == 0) return;
                    double dx = e.getScreenX() - resizeStartScreenX;
                    double dy = e.getScreenY() - resizeStartScreenY;

                    if ((resizeMode & 2) != 0) { // Right
                        stage.setWidth(
                                Math.max(UIConstants.PANEL_MIN_WIDTH, resizeStartWidth + dx));
                    } else if ((resizeMode & 1) != 0) { // Left
                        double newW = Math.max(UIConstants.PANEL_MIN_WIDTH, resizeStartWidth - dx);
                        stage.setWidth(newW);
                        stage.setX(resizeStartX + (resizeStartWidth - newW));
                    }

                    if ((resizeMode & 8) != 0) { // Bottom
                        stage.setHeight(
                                Math.max(UIConstants.PANEL_MIN_HEIGHT, resizeStartHeight + dy));
                    } else if ((resizeMode & 4) != 0) { // Top
                        double newH =
                                Math.max(UIConstants.PANEL_MIN_HEIGHT, resizeStartHeight - dy);
                        stage.setHeight(newH);
                        stage.setY(resizeStartY + (resizeStartHeight - newH));
                    }
                });

        scene.setOnMouseReleased(
                e -> {
                    isResizing = false;
                    resizeMode = 0;
                    saveGeometry();
                });
    }

    private void closeApplication() {
        saveGeometry();
        timerService.pause();
        Platform.exit();
        System.exit(0);
    }

    public void show() {
        stage.show();
    }
}
