package io.github.marodriguezd.taskflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.domain.ThemeMode;
import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyDataMigratorTest {

    @TempDir Path tempDir;

    private DatabaseManager databaseManager;
    private SqliteTaskRepository taskRepository;
    private SqliteHistoryRepository historyRepository;
    private SqlitePreferenceRepository preferenceRepository;
    private LegacyDataMigrator migrator;

    @BeforeEach
    void setUp() {
        databaseManager = DatabaseManager.inMemory();
        taskRepository = new SqliteTaskRepository(databaseManager);
        historyRepository = new SqliteHistoryRepository(databaseManager);
        preferenceRepository = new SqlitePreferenceRepository(databaseManager);

        migrator =
                new LegacyDataMigrator(
                        tempDir, taskRepository, historyRepository, preferenceRepository);
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    @DisplayName("Successfully migrates legacy JSON files into SQLite")
    void testSuccessfulMigration() throws IOException {
        String tasksJson =
                """
                [
                  {
                    "name": "Legacy Task 1",
                    "priority": "Alta",
                    "total_seconds": 1500,
                    "remaining": 1200
                  },
                  {
                    "name": "Legacy Task 2",
                    "priority": "Baja",
                    "total_seconds": 600,
                    "remaining": 600
                  }
                ]
                """;

        String historyJson =
                """
                [
                  {
                    "name": "Legacy Completed",
                    "priority": "Media",
                    "total_seconds": 1500,
                    "remaining": 0,
                    "history_event": "completed",
                    "completed_manually": false,
                    "event_at": "14:30  —  01/10/26"
                  }
                ]
                """;

        String geometryJson =
                """
                {
                  "x": 200,
                  "y": 300,
                  "width": 380,
                  "height": 620
                }
                """;

        String settingsJson =
                """
                {
                  "theme": "light",
                  "always_on_top": true
                }
                """;

        Files.writeString(tempDir.resolve("taskflow_data.json"), tasksJson);
        Files.writeString(tempDir.resolve("taskflow_history.json"), historyJson);
        Files.writeString(tempDir.resolve("taskflow_geometry.json"), geometryJson);
        Files.writeString(tempDir.resolve("taskflow_settings.json"), settingsJson);

        migrator.migrateIfNecessary();

        // 1. Verify active tasks
        List<Task> tasks = taskRepository.findAll();
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).name()).isEqualTo("Legacy Task 1");
        assertThat(tasks.get(0).priority()).isEqualTo(Priority.HIGH);
        assertThat(tasks.get(0).remainingSeconds()).isEqualTo(1200);

        // 2. Verify history
        List<HistoryItem> history = historyRepository.findAll();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).name()).isEqualTo("Legacy Completed");
        assertThat(history.get(0).eventType()).isEqualTo(HistoryEventType.COMPLETED);

        // 3. Verify geometry
        Optional<WindowGeometry> geometry = preferenceRepository.loadGeometry();
        assertThat(geometry).isPresent();
        assertThat(geometry.get().x()).isEqualTo(200.0);
        assertThat(geometry.get().width()).isEqualTo(380.0);

        // 4. Verify settings
        UserPreferences prefs = preferenceRepository.loadPreferences(false);
        assertThat(prefs.theme()).isEqualTo(ThemeMode.LIGHT);
        assertThat(prefs.alwaysOnTop()).isTrue();

        // 5. Verify files were renamed to .migrated
        assertThat(Files.exists(tempDir.resolve("taskflow_data.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("Handles corrupted JSON files gracefully without throwing")
    void testCorruptedJsonHandling() throws IOException {
        Files.writeString(tempDir.resolve("taskflow_data.json"), "{ NOT VALID JSON :::");
        migrator.migrateIfNecessary();
        assertThat(taskRepository.count()).isEqualTo(0);
    }
}
