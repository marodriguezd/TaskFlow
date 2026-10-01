package io.github.marodriguezd.taskflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.domain.ThemeMode;
import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import io.github.marodriguezd.taskflow.persistence.DatabaseManager;
import io.github.marodriguezd.taskflow.persistence.SqliteHistoryRepository;
import io.github.marodriguezd.taskflow.persistence.SqlitePreferenceRepository;
import io.github.marodriguezd.taskflow.persistence.SqliteTaskRepository;
import io.github.marodriguezd.taskflow.service.PlatformService;
import io.github.marodriguezd.taskflow.service.SoundService;
import io.github.marodriguezd.taskflow.service.TaskService;
import io.github.marodriguezd.taskflow.service.TimerService;
import io.github.marodriguezd.taskflow.ui.theme.ThemeManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EndToEndFlowTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName(
            "Comprehensive End-to-End User Flow: Lifecycle, Timer, Persistence, Priorities, History & Settings")
    void testFullEndToEndLifecycle() throws IOException {
        Path dbPath = tempDir.resolve("taskflow_e2e.db");

        // ─────────────────────────────────────────────
        // 1. First Run: Startup & Database Init
        // ─────────────────────────────────────────────
        DatabaseManager dbManager1 = new DatabaseManager(dbPath);
        SqliteTaskRepository taskRepo1 = new SqliteTaskRepository(dbManager1);
        SqliteHistoryRepository historyRepo1 = new SqliteHistoryRepository(dbManager1);
        SqlitePreferenceRepository prefRepo1 = new SqlitePreferenceRepository(dbManager1);
        PlatformService platformService = new PlatformService();
        TimerService timerService1 = new TimerService();
        SoundService soundService1 = new SoundService(platformService);
        TaskService taskService1 =
                new TaskService(taskRepo1, historyRepo1, timerService1, soundService1);

        // ─────────────────────────────────────────────
        // 2. Create Tasks across different Priorities
        // ─────────────────────────────────────────────
        Task t1 = taskService1.createTask("Task High Priority", Priority.HIGH, 25);
        Task t2 = taskService1.createTask("Task Medium Priority", Priority.MEDIUM, 15);
        Task t3 = taskService1.createTask("Task Low Priority", Priority.LOW, 10);

        List<Task> activeList = taskService1.getAllTasks();
        assertThat(activeList).hasSize(3);
        // Verified: Priority ordering High -> Medium -> Low
        assertThat(activeList.get(0).priority()).isEqualTo(Priority.HIGH);
        assertThat(activeList.get(1).priority()).isEqualTo(Priority.MEDIUM);
        assertThat(activeList.get(2).priority()).isEqualTo(Priority.LOW);

        // ─────────────────────────────────────────────
        // 3. Pomodoro Timer: Start, Autopause & Resume
        // ─────────────────────────────────────────────
        // Start T1
        timerService1.start(t1);
        assertThat(timerService1.isRunning(t1.id())).isTrue();
        assertThat(timerService1.getRunningTaskId()).contains(t1.id());

        // Start T2 -> T1 must automatically pause!
        timerService1.start(t2);
        assertThat(timerService1.isRunning(t1.id())).isFalse();
        assertThat(timerService1.isRunning(t2.id())).isTrue();
        assertThat(timerService1.getRunningTaskId()).contains(t2.id());

        // Pause T2
        timerService1.pause();
        assertThat(timerService1.isRunning(t2.id())).isFalse();
        assertThat(timerService1.getRunningTaskId()).isEmpty();

        // Resume T2 via toggle
        timerService1.toggle(t2);
        assertThat(timerService1.isRunning(t2.id())).isTrue();

        timerService1.pause();

        // ─────────────────────────────────────────────
        // 4. Edit Task with Proportional Remaining Time
        // ─────────────────────────────────────────────
        // Reduce remaining time of T2 to half (450s / 900s = 50%)
        taskRepo1.updateRemainingSeconds(t2.id(), 450);
        // Change total duration to 30 minutes (1800s) -> remaining must scale to 900s
        Task editedT2 =
                taskService1.updateTask(t2.id(), "Task Medium Renamed", Priority.MEDIUM, 30);
        assertThat(editedT2.name()).isEqualTo("Task Medium Renamed");
        assertThat(editedT2.totalSeconds()).isEqualTo(1800);
        assertThat(editedT2.remainingSeconds()).isEqualTo(900);

        // ─────────────────────────────────────────────
        // 5. Complete Task Manually
        // ─────────────────────────────────────────────
        taskService1.completeTaskManually(t3.id());
        assertThat(taskService1.getAllTasks()).hasSize(2);

        List<HistoryItem> historyAfterManual = taskService1.getHistory();
        assertThat(historyAfterManual).hasSize(1);
        assertThat(historyAfterManual.get(0).name()).isEqualTo("Task Low Priority");
        assertThat(historyAfterManual.get(0).eventType()).isEqualTo(HistoryEventType.COMPLETED);
        assertThat(historyAfterManual.get(0).completedManually()).isTrue();

        // ─────────────────────────────────────────────
        // 6. Delete Task (tracked in History as DELETED)
        // ─────────────────────────────────────────────
        taskService1.deleteTask(editedT2.id(), true);
        assertThat(taskService1.getAllTasks()).hasSize(1);

        List<HistoryItem> historyAfterDelete = taskService1.getHistory();
        assertThat(historyAfterDelete).hasSize(2);
        assertThat(historyAfterDelete.get(0).name()).isEqualTo("Task Medium Renamed");
        assertThat(historyAfterDelete.get(0).eventType()).isEqualTo(HistoryEventType.DELETED);

        // ─────────────────────────────────────────────
        // 7. Restore Task from History
        // ─────────────────────────────────────────────
        HistoryItem toRestore = historyAfterDelete.get(0);
        Task restored = taskService1.restoreTaskFromHistory(toRestore.id());
        assertThat(restored.name()).isEqualTo("Task Medium Renamed");
        assertThat(taskService1.getAllTasks()).hasSize(2);
        assertThat(taskService1.getHistory()).hasSize(1);

        // ─────────────────────────────────────────────
        // 8. Theme Switching & Window Geometry
        // ─────────────────────────────────────────────
        UserPreferences initialPrefs = prefRepo1.loadPreferences(false);
        assertThat(initialPrefs.theme()).isEqualTo(ThemeMode.DARK);

        ThemeManager themeManager = new ThemeManager(initialPrefs.theme());
        themeManager.toggleTheme();
        assertThat(themeManager.getCurrentTheme()).isEqualTo(ThemeMode.LIGHT);
        prefRepo1.savePreferences(new UserPreferences(themeManager.getCurrentTheme(), true, true));

        WindowGeometry savedGeometry = new WindowGeometry(180, 220, 360, 580);
        prefRepo1.saveGeometry(savedGeometry);

        // ─────────────────────────────────────────────
        // 9. Audio Notification Trigger
        // ─────────────────────────────────────────────
        soundService1.ensureDefaultSoundExists();
        soundService1.playCompletionSound();

        // Close session 1
        timerService1.shutdown();
        dbManager1.close();

        // ─────────────────────────────────────────────
        // 10. Second Run (Persistence Restart Verification)
        // ─────────────────────────────────────────────
        DatabaseManager dbManager2 = new DatabaseManager(dbPath);
        SqliteTaskRepository taskRepo2 = new SqliteTaskRepository(dbManager2);
        SqliteHistoryRepository historyRepo2 = new SqliteHistoryRepository(dbManager2);
        SqlitePreferenceRepository prefRepo2 = new SqlitePreferenceRepository(dbManager2);

        // Verify active tasks persisted across restart
        List<Task> restartedTasks = taskRepo2.findAll();
        assertThat(restartedTasks).hasSize(2);
        assertThat(restartedTasks)
                .extracting(Task::name)
                .containsExactlyInAnyOrder("Task High Priority", "Task Medium Renamed");

        // Verify history persisted across restart
        List<HistoryItem> restartedHistory = historyRepo2.findAll();
        assertThat(restartedHistory).hasSize(1);
        assertThat(restartedHistory.get(0).name()).isEqualTo("Task Low Priority");

        // Verify preferences & geometry persisted across restart
        UserPreferences restartedPrefs = prefRepo2.loadPreferences(false);
        assertThat(restartedPrefs.theme()).isEqualTo(ThemeMode.LIGHT);
        assertThat(restartedPrefs.alwaysOnTop()).isTrue();

        Optional<WindowGeometry> reloadedGeo = prefRepo2.loadGeometry();
        assertThat(reloadedGeo).isPresent();
        assertThat(reloadedGeo.get().x()).isEqualTo(180.0);
        assertThat(reloadedGeo.get().width()).isEqualTo(360.0);

        dbManager2.close();
    }
}
