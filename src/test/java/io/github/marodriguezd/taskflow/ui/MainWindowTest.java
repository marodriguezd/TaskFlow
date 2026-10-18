package io.github.marodriguezd.taskflow.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.ThemeMode;
import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import io.github.marodriguezd.taskflow.persistence.DatabaseManager;
import io.github.marodriguezd.taskflow.persistence.PreferenceRepository;
import io.github.marodriguezd.taskflow.persistence.SqliteHistoryRepository;
import io.github.marodriguezd.taskflow.persistence.SqliteTaskRepository;
import io.github.marodriguezd.taskflow.service.PlatformService;
import io.github.marodriguezd.taskflow.service.SoundService;
import io.github.marodriguezd.taskflow.service.TaskService;
import io.github.marodriguezd.taskflow.service.TimerService;
import io.github.marodriguezd.taskflow.ui.i18n.LocaleManager;
import io.github.marodriguezd.taskflow.ui.theme.ThemeManager;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Slider;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MainWindowTest {

    @BeforeEach
    void checkDisplay() {
        Assumptions.assumeTrue(
                JavaFxTestSupport.isAvailable(),
                "JavaFX display not available in this environment");
    }

    @Test
    @DisplayName(
            "Dragging slider in AboutDialog does not persist per micro-event; persists once on close")
    void testAboutDialogOpacityPersistenceDecoupled() throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(
                () -> {
                    try {
                        DatabaseManager db = DatabaseManager.inMemory();
                        SqliteTaskRepository taskRepo = new SqliteTaskRepository(db);
                        SqliteHistoryRepository histRepo = new SqliteHistoryRepository(db);
                        PlatformService platformService = new PlatformService();
                        TimerService timerService = new TimerService();
                        SoundService soundService = new SoundService(platformService);
                        TaskService taskService =
                                new TaskService(taskRepo, histRepo, timerService, soundService);
                        ThemeManager themeManager = new ThemeManager(ThemeMode.DARK);
                        LocaleManager localeManager = new LocaleManager("en");

                        AtomicInteger saveCount = new AtomicInteger(0);
                        AtomicReference<UserPreferences> lastSaved = new AtomicReference<>();
                        PreferenceRepository prefRepo =
                                new PreferenceRepository() {
                                    @Override
                                    public UserPreferences loadPreferences(
                                            boolean defaultAlwaysOnTop) {
                                        return UserPreferences.defaults(false)
                                                .withGlassOpacity(0.85);
                                    }

                                    @Override
                                    public void savePreferences(UserPreferences preferences) {
                                        saveCount.incrementAndGet();
                                        lastSaved.set(preferences);
                                    }

                                    @Override
                                    public Optional<WindowGeometry> loadGeometry() {
                                        return Optional.empty();
                                    }

                                    @Override
                                    public void saveGeometry(WindowGeometry geometry) {}
                                };

                        Stage stage = new Stage();
                        MainWindow window =
                                new MainWindow(
                                        stage,
                                        taskService,
                                        timerService,
                                        prefRepo,
                                        platformService,
                                        themeManager,
                                        localeManager);
                        stage.show();

                        // Queue interaction that runs while openAboutDialog is blocking in
                        // showAndWait
                        Platform.runLater(
                                () -> {
                                    try {
                                        Stage aboutStage = null;
                                        for (Window w : Window.getWindows()) {
                                            if (w instanceof Stage s
                                                    && s != stage
                                                    && s.isShowing()) {
                                                aboutStage = s;
                                                break;
                                            }
                                        }
                                        assertThat(aboutStage).isNotNull();

                                        Slider slider =
                                                (Slider)
                                                        aboutStage
                                                                .getScene()
                                                                .lookup(".opacity-slider");
                                        assertThat(slider).isNotNull();

                                        // Simulate multiple drag events
                                        slider.setValue(70.0);
                                        slider.setValue(60.0);
                                        slider.setValue(55.0);

                                        // Persistence must NOT have been called during the drag
                                        assertThat(saveCount.get()).isZero();

                                        // Close modal dialog
                                        aboutStage.close();
                                    } catch (Throwable t) {
                                        future.completeExceptionally(t);
                                    }
                                });

                        // This blocks until aboutStage.close() is called
                        window.openAboutDialog();

                        // Now showAndWait has finished: persistence should have been called exactly
                        // once
                        assertThat(saveCount.get()).isEqualTo(1);
                        assertThat(lastSaved.get()).isNotNull();
                        assertThat(lastSaved.get().glassOpacity()).isEqualTo(0.55);

                        window.shutdown();
                        stage.close();
                        db.close();
                        future.complete(null);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });

        future.get(5, TimeUnit.SECONDS);
    }
}
