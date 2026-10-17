package io.github.marodriguezd.taskflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.domain.ThemeMode;
import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import io.github.marodriguezd.taskflow.util.DateTimeUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyDataMigratorTest {

    @TempDir Path tempDir;

    private Path dataDir;
    private Path homeDir;
    private DatabaseManager databaseManager;
    private SqliteTaskRepository taskRepository;
    private SqliteHistoryRepository historyRepository;
    private SqlitePreferenceRepository preferenceRepository;
    private LegacyDataMigrator migrator;

    @BeforeEach
    void setUp() throws IOException {
        // Both legacy lookup roots are temporary: migration must never read or write the
        // developer's real home directory or application data directory.
        dataDir = Files.createDirectories(tempDir.resolve("data"));
        homeDir = Files.createDirectories(tempDir.resolve("home"));
        databaseManager = DatabaseManager.inMemory();
        taskRepository = new SqliteTaskRepository(databaseManager);
        historyRepository = new SqliteHistoryRepository(databaseManager);
        preferenceRepository = new SqlitePreferenceRepository(databaseManager);

        migrator =
                new LegacyDataMigrator(
                        dataDir, homeDir, taskRepository, historyRepository, preferenceRepository);
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

        Files.writeString(dataDir.resolve("taskflow_data.json"), tasksJson);
        Files.writeString(dataDir.resolve("taskflow_history.json"), historyJson);
        Files.writeString(dataDir.resolve("taskflow_geometry.json"), geometryJson);
        Files.writeString(dataDir.resolve("taskflow_settings.json"), settingsJson);

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
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("Task files in the home root and the data directory are both imported")
    void testTaskFilesFromBothLocationsAreImported() throws IOException {
        // Regression: the data-directory file used to be skipped (and renamed) whenever the
        // home-root file had already populated the tasks table.
        Files.writeString(
                homeDir.resolve(".taskflow_data.json"), arrayOf(taskJson("From home", 600, 300)));
        Files.writeString(
                dataDir.resolve("taskflow_data.json"),
                arrayOf(taskJson("From data directory", 900, 900)));

        migrator.migrateIfNecessary();

        assertThat(taskRepository.findAll())
                .extracting(Task::name)
                .containsExactlyInAnyOrder("From home", "From data directory");
        assertThat(Files.exists(homeDir.resolve(".taskflow_data.json"))).isFalse();
        assertThat(Files.exists(homeDir.resolve(".taskflow_data.json.migrated"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json"))).isFalse();
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("History files in the home root and the data directory are both imported")
    void testHistoryFilesFromBothLocationsAreImported() throws IOException {
        Files.writeString(
                homeDir.resolve(".taskflow_history.json"),
                arrayOf(historyJson("Home completion", "10:00  —  01/10/26")));
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(historyJson("Data directory completion", "11:00  —  02/10/26")));

        migrator.migrateIfNecessary();

        assertThat(historyRepository.findAll())
                .extracting(HistoryItem::name)
                .containsExactlyInAnyOrder("Home completion", "Data directory completion");
        assertThat(Files.exists(homeDir.resolve(".taskflow_history.json.migrated"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("taskflow_history.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("Overlapping sources keep distinct records and import shared records once")
    void testOverlappingSourcesAreMergedWithoutLosingDistinctRecords() throws IOException {
        Files.writeString(
                homeDir.resolve(".taskflow_data.json"),
                arrayOf(
                        taskJson("Shared", 600, 600),
                        taskJson("Home only", 300, 300),
                        taskJson("Same visible text", 600, 600)));
        Files.writeString(
                dataDir.resolve("taskflow_data.json"),
                arrayOf(
                        taskJson("Shared", 600, 600),
                        taskJson("Data only", 900, 900),
                        // Same name as a home-root record but different content: a distinct task,
                        // not an equivalent one.
                        taskJson("Same visible text", 900, 900)));

        Files.writeString(
                homeDir.resolve(".taskflow_history.json"),
                arrayOf(
                        historyJson("Shared completion", "10:00  —  01/10/26"),
                        historyJson("Home completion", "11:00  —  01/10/26")));
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(
                        historyJson("Shared completion", "10:00  —  01/10/26"),
                        historyJson("Data completion", "12:00  —  01/10/26"),
                        // Same text, different event time: a second, distinct history entry.
                        historyJson("Shared completion", "13:00  —  01/10/26")));

        migrator.migrateIfNecessary();

        assertThat(taskRepository.findAll())
                .extracting(Task::name)
                .containsExactlyInAnyOrder(
                        "Shared",
                        "Home only",
                        "Data only",
                        "Same visible text",
                        "Same visible text");
        long sharedTasks =
                taskRepository.findAll().stream().filter(t -> t.name().equals("Shared")).count();
        assertThat(sharedTasks).isEqualTo(1);

        List<HistoryItem> history = historyRepository.findAll();
        assertThat(history)
                .extracting(HistoryItem::name)
                .containsExactlyInAnyOrder(
                        "Shared completion",
                        "Shared completion",
                        "Home completion",
                        "Data completion");
        assertThat(history)
                .filteredOn(item -> item.name().equals("Shared completion"))
                .extracting(HistoryItem::eventAt)
                .containsExactlyInAnyOrder(
                        DateTimeUtil.parseLegacyOrDefault("10:00  —  01/10/26", Instant.EPOCH),
                        DateTimeUtil.parseLegacyOrDefault("13:00  —  01/10/26", Instant.EPOCH));
    }

    @Test
    @DisplayName("Identical repeated entries inside one file are preserved")
    void testIdenticalEntriesWithinOneFileArePreserved() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_data.json"),
                arrayOf(taskJson("Twin", 600, 600), taskJson("Twin", 600, 600)));
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(
                        historyJson("Twin completion", "10:00  —  01/10/26"),
                        historyJson("Twin completion", "10:00  —  01/10/26")));

        migrator.migrateIfNecessary();

        assertThat(taskRepository.count()).isEqualTo(2);
        assertThat(historyRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("A non-empty database still receives previously unimported legacy records")
    void testImportsIntoExistingDatabase() throws IOException {
        taskRepository.save(Task.create("Existing task", Priority.MEDIUM, 600));
        historyRepository.save(
                new HistoryItem(
                        null,
                        "Existing history",
                        Priority.MEDIUM,
                        900,
                        0,
                        HistoryEventType.COMPLETED,
                        false,
                        DateTimeUtil.parseLegacyOrDefault("09:00  —  01/10/26", Instant.EPOCH)));

        Files.writeString(
                dataDir.resolve("taskflow_data.json"),
                arrayOf(
                        // Same content as the existing row: one logical record, not two.
                        taskJson("Existing task", 600, 600), taskJson("Legacy only", 300, 300)));
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(
                        historyJson("Existing history", "09:00  —  01/10/26"),
                        historyJson("Legacy history", "15:00  —  01/10/26")));

        migrator.migrateIfNecessary();

        assertThat(taskRepository.findAll())
                .extracting(Task::name)
                .containsExactlyInAnyOrder("Existing task", "Legacy only");
        assertThat(historyRepository.findAll())
                .extracting(HistoryItem::name)
                .containsExactlyInAnyOrder("Existing history", "Legacy history");
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("Restoring a migrated source and restarting does not duplicate records")
    void testRestoredSourcesAreNotImportedTwice() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_data.json"), arrayOf(taskJson("Only once", 600, 600)));
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                // No timestamp: identity falls back to content so a retry is still deduplicated.
                arrayOf(historyJson("Only once completed", null)));

        migrator.migrateIfNecessary();
        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(historyRepository.count()).isEqualTo(1);

        // Simulate a restored backup or a rename that could not happen: the source reappears.
        Files.copy(
                dataDir.resolve("taskflow_data.json.migrated"),
                dataDir.resolve("taskflow_data.json"));
        Files.copy(
                dataDir.resolve("taskflow_history.json.migrated"),
                dataDir.resolve("taskflow_history.json"));

        migrator.migrateIfNecessary();

        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(historyRepository.count()).isEqualTo(1);
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json.migrated.1"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("taskflow_history.json.migrated.1"))).isTrue();
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
        Files.writeString(dataDir.resolve("taskflow_settings.json"), settingsJson);

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
        Files.writeString(dataDir.resolve("taskflow_data.json"), tasksJson);

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
        Files.writeString(dataDir.resolve("taskflow_data.json"), tasksJson);

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
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json"))).isFalse();
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("An existing .migrated backup never causes the source to be re-imported")
    void testRepeatedMigrationDoesNotReapplySettings() throws IOException {
        // A restored backup can leave both the source and the original .migrated file on disk.
        Files.writeString(
                dataDir.resolve("taskflow_settings.json.migrated"), "{\"theme\":\"dark\"}");
        Files.writeString(
                dataDir.resolve("taskflow_settings.json"),
                "{\"theme\":\"light\",\"always_on_top\":true}");

        migrator.migrateIfNecessary();
        assertThat(preferenceRepository.loadPreferences(false).theme()).isEqualTo(ThemeMode.LIGHT);

        // The user picks dark afterwards; a later startup must not resurrect the legacy value.
        preferenceRepository.savePreferences(
                preferenceRepository.loadPreferences(false).withTheme(ThemeMode.DARK));
        migrator.migrateIfNecessary();

        assertThat(preferenceRepository.loadPreferences(false).theme()).isEqualTo(ThemeMode.DARK);
        assertThat(Files.exists(dataDir.resolve("taskflow_settings.json"))).isFalse();
        assertThat(Files.exists(dataDir.resolve("taskflow_settings.json.migrated.1"))).isTrue();
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
        Files.writeString(dataDir.resolve("taskflow_data.json"), tasksJson);
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
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json.migrated"))).isFalse();

        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER fail_second_task");
        }
        migrator.migrateIfNecessary();

        assertThat(taskRepository.count()).isEqualTo(2);
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json"))).isFalse();
    }

    @Test
    @DisplayName("Handles corrupted JSON files gracefully without throwing")
    void testCorruptedJsonHandling() throws IOException {
        Files.writeString(dataDir.resolve("taskflow_data.json"), "{ NOT VALID JSON :::");
        migrator.migrateIfNecessary();
        assertThat(taskRepository.count()).isEqualTo(0);
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json"))).isTrue();
    }

    private static String arrayOf(String... records) {
        return "[" + String.join(",", records) + "]";
    }

    private static String taskJson(String name, int totalSeconds, int remainingSeconds) {
        return "{\"name\":\""
                + name
                + "\",\"priority\":\"Media\",\"total_seconds\":"
                + totalSeconds
                + ",\"remaining\":"
                + remainingSeconds
                + "}";
    }

    private static String historyJson(String name, String eventAt) {
        String dateField = eventAt == null ? "" : ",\"event_at\":\"" + eventAt + "\"";
        return "{\"name\":\""
                + name
                + "\",\"priority\":\"Media\",\"total_seconds\":900,\"remaining\":0"
                + ",\"history_event\":\"completed\",\"completed_manually\":false"
                + dateField
                + "}";
    }
}
