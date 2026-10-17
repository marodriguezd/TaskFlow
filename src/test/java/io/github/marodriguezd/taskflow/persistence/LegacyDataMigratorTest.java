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
    @DisplayName("Settings migration preserves existing language and sound preferences")
    void testSettingsMigrationPreservesExistingPreferences() throws IOException {
        // Preferences already stored (e.g. language chosen, sound disabled) before a legacy
        // settings file is imported — migration must only overwrite theme/always_on_top.
        preferenceRepository.savePreferences(
                new UserPreferences(ThemeMode.DARK, false, false, "de"));

        String settingsJson =
                """
                {
                  "theme": "light",
                  "always_on_top": true
                }
                """;
        Files.writeString(tempDir.resolve("taskflow_settings.json"), settingsJson);

        migrator.migrateIfNecessary();

        UserPreferences prefs = preferenceRepository.loadPreferences(false);
        assertThat(prefs.theme()).isEqualTo(ThemeMode.LIGHT);
        assertThat(prefs.alwaysOnTop()).isTrue();
        assertThat(prefs.language()).isEqualTo("de");
        assertThat(prefs.soundEnabled()).isFalse();
    }

    @Test
    @DisplayName("Unicode names, numeric strings, and unknown fields survive migration")
    void testUnicodeAndLooseTypes() throws IOException {
        String tasksJson =
                """
                [
                  {
                    "name": "Café ☕ — 日本語",
                    "priority": "alta",
                    "total_seconds": "900",
                    "remaining": "450",
                    "unknown_field": {"nested": [1, 2, 3]}
                  }
                ]
                """;
        Files.writeString(tempDir.resolve("taskflow_data.json"), tasksJson);

        migrator.migrateIfNecessary();

        List<Task> tasks = taskRepository.findAll();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().name()).isEqualTo("Café ☕ — 日本語");
        assertThat(tasks.getFirst().priority()).isEqualTo(Priority.HIGH);
        assertThat(tasks.getFirst().totalSeconds()).isEqualTo(900);
        assertThat(tasks.getFirst().remainingSeconds()).isEqualTo(450);
    }

    @Test
    @DisplayName("A malformed record does not abort the rest of the file")
    void testMalformedRecordDoesNotAbortImport() throws IOException {
        // The middle element is null (unusable) and the last has a zero duration. Aborting on the
        // first bad record would lose the remaining tasks, and because rows already exist the next
        // startup would skip the file entirely.
        String tasksJson =
                """
                [
                  {"name": "First", "priority": "Alta", "total_seconds": 600, "remaining": 300},
                  null,
                  {"name": "Zero duration", "priority": "Media", "total_seconds": 0, "remaining": 0}
                ]
                """;
        Files.writeString(tempDir.resolve("taskflow_data.json"), tasksJson);

        migrator.migrateIfNecessary();

        List<Task> tasks = taskRepository.findAll();
        assertThat(tasks)
                .extracting(Task::name)
                .containsExactlyInAnyOrder("First", "Zero duration");
        Task zero =
                tasks.stream()
                        .filter(t -> t.name().equals("Zero duration"))
                        .findFirst()
                        .orElseThrow();
        assertThat(zero.totalSeconds()).isEqualTo(1);
        assertThat(zero.remainingSeconds()).isZero();
        assertThat(Files.exists(tempDir.resolve("taskflow_data.json"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("taskflow_data.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("An existing .migrated backup never causes the source to be re-imported")
    void testRepeatedMigrationDoesNotReapplySettings() throws IOException {
        // A restored backup can leave both the source and the original .migrated file on disk.
        Files.writeString(
                tempDir.resolve("taskflow_settings.json.migrated"), "{\"theme\":\"dark\"}");
        Files.writeString(
                tempDir.resolve("taskflow_settings.json"),
                "{\"theme\":\"light\",\"always_on_top\":true}");

        migrator.migrateIfNecessary();
        assertThat(preferenceRepository.loadPreferences(false).theme()).isEqualTo(ThemeMode.LIGHT);

        // The user picks dark afterwards; a later startup must not resurrect the legacy value.
        preferenceRepository.savePreferences(
                preferenceRepository.loadPreferences(false).withTheme(ThemeMode.DARK));
        migrator.migrateIfNecessary();

        assertThat(preferenceRepository.loadPreferences(false).theme()).isEqualTo(ThemeMode.DARK);
        assertThat(Files.exists(tempDir.resolve("taskflow_settings.json"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("taskflow_settings.json.migrated.1"))).isTrue();
    }

    @Test
    @DisplayName("A failed bulk import leaves no rows behind and is retried on the next startup")
    void testFailedImportRollsBackAndRetries() throws Exception {
        String tasksJson =
                """
                [
                  {"name": "A", "total_seconds": 600, "remaining": 600},
                  {"name": "B", "total_seconds": 900, "remaining": 900}
                ]
                """;
        Files.writeString(tempDir.resolve("taskflow_data.json"), tasksJson);
        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TRIGGER fail_second_task BEFORE INSERT ON tasks "
                            + "WHEN NEW.name = 'B' "
                            + "BEGIN SELECT RAISE(ABORT, 'simulated import failure'); END");
        }

        migrator.migrateIfNecessary();

        // The whole file is one transaction: nothing imported and the source is left for a retry.
        assertThat(taskRepository.count()).isZero();
        assertThat(Files.exists(tempDir.resolve("taskflow_data.json"))).isTrue();

        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER fail_second_task");
        }
        migrator.migrateIfNecessary();

        assertThat(taskRepository.count()).isEqualTo(2);
        assertThat(Files.exists(tempDir.resolve("taskflow_data.json"))).isFalse();
    }

    @Test
    @DisplayName("Handles corrupted JSON files gracefully without throwing")
    void testCorruptedJsonHandling() throws IOException {
        Files.writeString(tempDir.resolve("taskflow_data.json"), "{ NOT VALID JSON :::");
        migrator.migrateIfNecessary();
        assertThat(taskRepository.count()).isEqualTo(0);
    }
}
