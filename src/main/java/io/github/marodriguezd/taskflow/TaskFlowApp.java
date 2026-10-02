package io.github.marodriguezd.taskflow;

import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.persistence.DatabaseManager;
import io.github.marodriguezd.taskflow.persistence.LegacyDataMigrator;
import io.github.marodriguezd.taskflow.persistence.PreferenceRepository;
import io.github.marodriguezd.taskflow.persistence.SqliteHistoryRepository;
import io.github.marodriguezd.taskflow.persistence.SqlitePreferenceRepository;
import io.github.marodriguezd.taskflow.persistence.SqliteTaskRepository;
import io.github.marodriguezd.taskflow.service.PlatformService;
import io.github.marodriguezd.taskflow.service.SoundService;
import io.github.marodriguezd.taskflow.service.TaskService;
import io.github.marodriguezd.taskflow.service.TimerService;
import io.github.marodriguezd.taskflow.ui.MainWindow;
import io.github.marodriguezd.taskflow.ui.i18n.Languages;
import io.github.marodriguezd.taskflow.ui.i18n.LocaleManager;
import io.github.marodriguezd.taskflow.ui.theme.ThemeManager;
import java.io.InputStream;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JavaFX application entry point for TaskFlow. */
public class TaskFlowApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(TaskFlowApp.class);

    private DatabaseManager databaseManager;
    private TimerService timerService;
    private TaskService taskService;

    @Override
    public void start(Stage primaryStage) {
        log.info("Starting TaskFlow Application (Java 21 / JavaFX)...");

        try {
            // 1. Core infrastructure
            PlatformService platformService = new PlatformService();
            databaseManager = new DatabaseManager(platformService.getDatabasePath());

            // 2. Repositories
            SqliteTaskRepository taskRepository = new SqliteTaskRepository(databaseManager);
            SqliteHistoryRepository historyRepository =
                    new SqliteHistoryRepository(databaseManager);
            PreferenceRepository preferenceRepository =
                    new SqlitePreferenceRepository(databaseManager);

            // 3. Migrate legacy JSON data if present
            LegacyDataMigrator migrator =
                    new LegacyDataMigrator(
                            platformService.getDataDirectory(),
                            taskRepository,
                            historyRepository,
                            preferenceRepository);
            migrator.migrateIfNecessary();

            UserPreferences preferences =
                    preferenceRepository.loadPreferences(platformService.getDefaultAlwaysOnTop());

            // 4. Domain & UI services
            timerService = new TimerService();
            SoundService soundService = new SoundService(platformService);
            soundService.setSoundEnabled(preferences.soundEnabled());
            taskService =
                    new TaskService(taskRepository, historyRepository, timerService, soundService);

            // First run (blank) or corrupt value: resolve the UI language from the OS locale
            // and persist the resolution so later OS changes never silently flip the language.
            String resolvedLanguage = Languages.resolveStored(preferences.language());
            if (!resolvedLanguage.equals(preferences.language())) {
                preferences = preferences.withLanguage(resolvedLanguage);
                preferenceRepository.savePreferences(preferences);
                log.info("Resolved UI language to '{}'", resolvedLanguage);
            }
            LocaleManager localeManager = new LocaleManager(resolvedLanguage);
            ThemeManager themeManager = new ThemeManager(preferences.theme());

            // 5. Load application icon
            loadApplicationIcon(primaryStage);

            // 6. Build and show main window
            MainWindow mainWindow =
                    new MainWindow(
                            primaryStage,
                            taskService,
                            timerService,
                            preferenceRepository,
                            platformService,
                            themeManager,
                            localeManager);
            mainWindow.show();
            log.info("TaskFlow application window rendered successfully.");

        } catch (Exception e) {
            log.error("Fatal startup error in TaskFlow application", e);
            throw e;
        }
    }

    private void loadApplicationIcon(Stage stage) {
        try (InputStream iconStream = getClass().getResourceAsStream("/assets/TaskFlow.png")) {
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception e) {
            log.warn("Could not load application icon: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        log.info("Stopping TaskFlow application and releasing database resources...");
        if (timerService != null) {
            timerService.shutdown();
        }
        if (taskService != null) {
            taskService.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
