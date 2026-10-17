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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyDataMigratorTest {

    private static final String TIMESTAMP = "10:00  —  01/10/26";

    @TempDir Path tempDir;

    private Path dataDir;
    private Path homeDir;
    private DatabaseManager databaseManager;
    private SqliteTaskRepository taskRepository;
    private SqliteHistoryRepository historyRepository;
    private SqlitePreferenceRepository preferenceRepository;
    private SqliteLegacyImportRepository legacyImportRepository;
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
        legacyImportRepository = new SqliteLegacyImportRepository(databaseManager);

        migrator = migratorInZone(ZoneId.systemDefault());
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    private LegacyDataMigrator migratorInZone(ZoneId zone) {
        return new LegacyDataMigrator(
                dataDir,
                homeDir,
                taskRepository,
                historyRepository,
                preferenceRepository,
                legacyImportRepository,
                zone);
    }

    @Test
    @DisplayName("Successfully migrates legacy JSON files into SQLite with provenance")
    void testSuccessfulMigration() throws IOException, SQLException {
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

        // 2. Verify history and its canonical source timestamp
        List<HistoryItem> history = historyRepository.findAll();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).name()).isEqualTo("Legacy Completed");
        assertThat(history.get(0).eventType()).isEqualTo(HistoryEventType.COMPLETED);
        assertThat(legacySourceTimestamp("Legacy Completed")).isEqualTo("2026-10-01T14:30");

        // 3. Verify geometry
        Optional<WindowGeometry> geometry = preferenceRepository.loadGeometry();
        assertThat(geometry).isPresent();
        assertThat(geometry.get().x()).isEqualTo(200.0);
        assertThat(geometry.get().width()).isEqualTo(380.0);

        // 4. Verify settings
        UserPreferences prefs = preferenceRepository.loadPreferences(false);
        assertThat(prefs.theme()).isEqualTo(ThemeMode.LIGHT);
        assertThat(prefs.alwaysOnTop()).isTrue();

        // 5. Every source kind is durably recorded, and every imported row carries provenance.
        assertThat(legacySourceCount(LegacyImportRepository.KIND_TASKS)).isEqualTo(1);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_HISTORY)).isEqualTo(1);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_GEOMETRY)).isEqualTo(1);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_SETTINGS)).isEqualTo(1);
        assertThat(importedRowCount("tasks")).isEqualTo(2);
        assertThat(importedRowCount("history")).isEqualTo(1);

        // 6. Verify files were renamed to .migrated
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
                        legacyInstant("10:00  —  01/10/26"), legacyInstant("13:00  —  01/10/26"));

        // Overlapping records are deduplicated through row provenance; both source files are
        // nevertheless recorded as processed because each contributed at least one record.
        assertThat(legacySourceCount(LegacyImportRepository.KIND_TASKS)).isEqualTo(2);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_HISTORY)).isEqualTo(2);
    }

    @Test
    @DisplayName("An untimestamped snapshot record listed first cannot consume the timestamped row")
    void testUntimestampedRecordListedFirstDoesNotConsumeTimestampedRow() throws IOException {
        assertOrderIndependentTimestampMatching(
                historyJson("Shared completion", null),
                historyJson("Shared completion", TIMESTAMP));
    }

    @Test
    @DisplayName("A timestamped snapshot record listed first matches the existing legacy row")
    void testTimestampedRecordListedFirstMatchesExistingLegacyRow() throws IOException {
        assertOrderIndependentTimestampMatching(
                historyJson("Shared completion", TIMESTAMP),
                historyJson("Shared completion", null));
    }

    @Test
    @DisplayName("Timestamped records with identical content but different times stay distinct")
    void testTimestampedDuplicatesWithDifferentTimesRemainDistinct() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(
                        historyJson("Repeated", "10:00  —  01/10/26"),
                        historyJson("Repeated", "13:00  —  01/10/26")));

        migrator.migrateIfNecessary();

        assertThat(historyRepository.count()).isEqualTo(2);
        assertThat(historyRepository.findAll())
                .extracting(HistoryItem::eventAt)
                .containsExactlyInAnyOrder(
                        legacyInstant("10:00  —  01/10/26"), legacyInstant("13:00  —  01/10/26"));
    }

    @Test
    @DisplayName("An unparsable timestamp falls back to content matching on retry")
    void testInvalidTimestampRetryDoesNotDuplicate() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(historyJson("Invalid date", "not-a-timestamp")));

        migrator.migrateIfNecessary();

        assertThat(historyRepository.count()).isEqualTo(1);
        Instant firstImportEvent = historyRepository.findAll().getFirst().eventAt();
        assertThat(canonicalTimestamp("Invalid date")).isNull();

        // Restored backup: the source reappears after a successful import.
        Files.copy(
                dataDir.resolve("taskflow_history.json.migrated"),
                dataDir.resolve("taskflow_history.json"));
        migrator.migrateIfNecessary();

        assertThat(historyRepository.count()).isEqualTo(1);
        assertThat(historyRepository.findAll().getFirst().eventAt()).isEqualTo(firstImportEvent);
    }

    @Test
    @DisplayName("Multiple identical untimestamped records keep their multiplicity")
    void testIdenticalUntimestampedRecordsKeepMultiplicity() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(
                        historyJson("Twin", null),
                        historyJson("Twin", null),
                        historyJson("Twin", null)));

        migrator.migrateIfNecessary();

        List<HistoryItem> stored = historyRepository.findAll();
        assertThat(stored).hasSize(3);
        // One fallback instant is computed per file and shared by every record in it. It is only a
        // stored display value, never an exact match identity.
        Instant fallbackEventAt = stored.getFirst().eventAt();
        assertThat(stored).extracting(HistoryItem::eventAt).containsOnly(fallbackEventAt);

        // Restored source: the source ledger skips it and preserves the stored multiplicity.
        Files.copy(
                dataDir.resolve("taskflow_history.json.migrated"),
                dataDir.resolve("taskflow_history.json"));
        migrator.migrateIfNecessary();

        List<HistoryItem> afterRetry = historyRepository.findAll();
        assertThat(afterRetry).hasSize(3);
        assertThat(afterRetry).extracting(HistoryItem::eventAt).containsOnly(fallbackEventAt);
    }

    @Test
    @DisplayName("A malformed history record does not abort the rest of the file")
    void testMalformedHistoryRecordDoesNotAbortImport() throws IOException {
        // The second element is null (unusable) and the third has a blank name. Aborting on the
        // first bad record would lose the records after it.
        String legacyHistory =
                """
                [
                  {"name": "First", "priority": "Media", "total_seconds": 900,
                   "remaining": 0, "history_event": "completed",
                   "completed_manually": false, "event_at": "10:00  —  01/10/26"},
                  null,
                  {"name": "   ", "priority": "Media", "total_seconds": 900,
                   "remaining": 0, "history_event": "completed",
                   "completed_manually": false, "event_at": "12:00  —  01/10/26"},
                  {"name": "Last", "priority": "Media", "total_seconds": 900,
                   "remaining": 0, "history_event": "completed",
                   "completed_manually": false, "event_at": "11:00  —  01/10/26"}
                ]
                """;
        Files.writeString(dataDir.resolve("taskflow_history.json"), legacyHistory);

        migrator.migrateIfNecessary();

        List<HistoryItem> stored = historyRepository.findAll();
        assertThat(stored).extracting(HistoryItem::name).containsExactlyInAnyOrder("First", "Last");
        assertThat(stored)
                .filteredOn(item -> item.name().equals("First"))
                .extracting(HistoryItem::eventAt)
                .containsExactly(legacyInstant("10:00  —  01/10/26"));
        assertThat(stored)
                .filteredOn(item -> item.name().equals("Last"))
                .extracting(HistoryItem::eventAt)
                .containsExactly(legacyInstant("11:00  —  01/10/26"));
        // Valid records are imported and the file is still backed up as processed.
        assertThat(Files.exists(dataDir.resolve("taskflow_history.json"))).isFalse();
        assertThat(Files.exists(dataDir.resolve("taskflow_history.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("A failed history import rolls back and is retried exactly once")
    void testFailedHistoryImportRollsBackAndRetries() throws Exception {
        String legacyHistory =
                """
                [
                  {"name": "A", "priority": "Media", "total_seconds": 900, "remaining": 0,
                   "history_event": "completed", "completed_manually": false,
                   "event_at": "10:00  —  01/10/26"},
                  {"name": "B", "priority": "Media", "total_seconds": 900, "remaining": 0,
                   "history_event": "completed", "completed_manually": false,
                   "event_at": "11:00  —  01/10/26"}
                ]
                """;
        Files.writeString(dataDir.resolve("taskflow_history.json"), legacyHistory);
        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TRIGGER fail_second_history BEFORE INSERT ON history "
                            + "WHEN NEW.name = 'B' "
                            + "BEGIN SELECT RAISE(ABORT, 'simulated import failure'); END");
        }

        migrator.migrateIfNecessary();

        // The whole file is one transaction: no rows, no false source-completion record, and the
        // source is left for a retry.
        assertThat(historyRepository.count()).isZero();
        assertThat(legacySourceCount(LegacyImportRepository.KIND_HISTORY)).isZero();
        assertThat(Files.exists(dataDir.resolve("taskflow_history.json"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("taskflow_history.json.migrated"))).isFalse();

        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER fail_second_history");
        }
        migrator.migrateIfNecessary();

        assertThat(historyRepository.findAll())
                .extracting(HistoryItem::name)
                .containsExactlyInAnyOrder("A", "B");
        assertThat(legacySourceCount(LegacyImportRepository.KIND_HISTORY)).isEqualTo(1);
        assertThat(Files.exists(dataDir.resolve("taskflow_history.json"))).isFalse();
        assertThat(Files.exists(dataDir.resolve("taskflow_history.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("A valid completed_at is used when event_at is unparsable")
    void testInvalidPrimaryTimestampFallsBackToCompletedAt() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(
                        historyJsonWithFields(
                                "Fallback", "not-a-timestamp", "09:30  —  01/10/26")));

        migrator.migrateIfNecessary();

        Instant expected = legacyInstant("09:30  —  01/10/26");
        assertThat(historyRepository.count()).isEqualTo(1);
        assertThat(historyRepository.findAll().getFirst().eventAt()).isEqualTo(expected);
        assertThat(canonicalTimestamp("Fallback")).isEqualTo("2026-10-01T09:30");

        // The fallback timestamp is a reliable identity, so a restored source is not re-imported.
        Files.copy(
                dataDir.resolve("taskflow_history.json.migrated"),
                dataDir.resolve("taskflow_history.json"));
        migrator.migrateIfNecessary();

        assertThat(historyRepository.count()).isEqualTo(1);
        assertThat(historyRepository.findAll().getFirst().eventAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Two unparsable timestamps fall back to content identity on retry")
    void testBothTimestampFieldsInvalidRetryDoesNotDuplicate() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(historyJsonWithFields("No date", "not-a-timestamp", "also-bad")));

        migrator.migrateIfNecessary();

        assertThat(historyRepository.count()).isEqualTo(1);
        Instant firstImportEvent = historyRepository.findAll().getFirst().eventAt();

        Files.copy(
                dataDir.resolve("taskflow_history.json.migrated"),
                dataDir.resolve("taskflow_history.json"));
        migrator.migrateIfNecessary();

        assertThat(historyRepository.count()).isEqualTo(1);
        assertThat(historyRepository.findAll().getFirst().eventAt()).isEqualTo(firstImportEvent);
    }

    @Test
    @DisplayName("event_at wins over completed_at when both parse")
    void testValidEventAtTakesPrecedenceOverCompletedAt() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(
                        historyJsonWithFields(
                                "Both dates", "10:00  —  01/10/26", "09:00  —  01/10/26")));

        migrator.migrateIfNecessary();

        assertThat(historyRepository.count()).isEqualTo(1);
        assertThat(historyRepository.findAll().getFirst().eventAt())
                .isEqualTo(legacyInstant("10:00  —  01/10/26"))
                .isNotEqualTo(legacyInstant("09:00  —  01/10/26"));
        assertThat(canonicalTimestamp("Both dates")).isEqualTo("2026-10-01T10:00");
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
    @DisplayName("Application-created rows never suppress a legacy record")
    void testApplicationCreatedRowsDoNotSuppressLegacyImport() throws IOException {
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
                        legacyInstant("09:00  —  01/10/26")));

        Files.writeString(
                dataDir.resolve("taskflow_data.json"),
                arrayOf(
                        // Same content as the application-created row. Content alone cannot prove
                        // that the legacy record was already imported, so it is imported.
                        taskJson("Existing task", 600, 600), taskJson("Legacy only", 300, 300)));
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(
                        historyJson("Existing history", "09:00  —  01/10/26"),
                        historyJson("Legacy history", "15:00  —  01/10/26")));

        migrator.migrateIfNecessary();

        assertThat(taskRepository.findAll())
                .extracting(Task::name)
                .containsExactlyInAnyOrder("Existing task", "Existing task", "Legacy only");
        assertThat(historyRepository.findAll())
                .extracting(HistoryItem::name)
                .containsExactlyInAnyOrder(
                        "Existing history", "Existing history", "Legacy history");
        assertThat(importedRowCount("tasks")).isEqualTo(2);
        assertThat(importedRowCount("history")).isEqualTo(2);
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("Restoring a migrated source and restarting does not duplicate records")
    void testRestoredSourcesAreNotImportedTwice() throws IOException, SQLException {
        Files.writeString(
                dataDir.resolve("taskflow_data.json"), arrayOf(taskJson("Only once", 600, 600)));
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                // No timestamp: identity falls back to content so a retry is still deduplicated.
                arrayOf(historyJson("Only once completed", null)));

        migrator.migrateIfNecessary();
        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(historyRepository.count()).isEqualTo(1);
        Instant originalEvent = historyRepository.findAll().getFirst().eventAt();

        // Simulate a restored backup or a rename that could not happen: the source reappears with
        // the exact same bytes after the provenance transaction committed.
        Files.copy(
                dataDir.resolve("taskflow_data.json.migrated"),
                dataDir.resolve("taskflow_data.json"));
        Files.copy(
                dataDir.resolve("taskflow_history.json.migrated"),
                dataDir.resolve("taskflow_history.json"));

        migrator.migrateIfNecessary();

        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(historyRepository.count()).isEqualTo(1);
        assertThat(historyRepository.findAll().getFirst().eventAt()).isEqualTo(originalEvent);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_TASKS)).isEqualTo(1);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_HISTORY)).isEqualTo(1);
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json.migrated.1"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("taskflow_history.json.migrated.1"))).isTrue();
    }

    @Test
    @DisplayName("A restored source is not re-imported after the JVM time zone changes")
    void testRestoredSourceAcrossTimeZoneChangeDoesNotDuplicate() throws IOException {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(historyJson("Tokyo completion", TIMESTAMP)));

        migratorInZone(tokyo).migrateIfNecessary();
        Instant tokyoEvent = legacyInstant(TIMESTAMP, tokyo);
        assertThat(historyRepository.count()).isEqualTo(1);
        assertThat(historyRepository.findAll().getFirst().eventAt()).isEqualTo(tokyoEvent);

        Files.copy(
                dataDir.resolve("taskflow_history.json.migrated"),
                dataDir.resolve("taskflow_history.json"));
        migratorInZone(ZoneId.of("America/Los_Angeles")).migrateIfNecessary();

        // Same source bytes: the durable source record skips it, so neither the row count nor the
        // originally stored instant changes.
        assertThat(historyRepository.count()).isEqualTo(1);
        assertThat(historyRepository.findAll().getFirst().eventAt()).isEqualTo(tokyoEvent);
    }

    @Test
    @DisplayName("Cross-source deduplication uses canonical local time, not the JVM zone")
    void testCrossSourceDeduplicationIgnoresTimeZoneChange() throws IOException {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(historyJson("Shared completion", TIMESTAMP)));
        migratorInZone(tokyo).migrateIfNecessary();
        Instant tokyoEvent = legacyInstant(TIMESTAMP, tokyo);

        // A different source snapshot (different bytes) contains the same record, imported under a
        // different zone. Its canonical local timestamp must still match the stored provenance.
        Files.writeString(
                homeDir.resolve(".taskflow_history.json"),
                arrayOf(
                        historyJson("Shared completion", TIMESTAMP),
                        historyJson("Home only", "11:00  —  01/10/26")));
        migratorInZone(ZoneId.of("America/Los_Angeles")).migrateIfNecessary();

        List<HistoryItem> stored = historyRepository.findAll();
        assertThat(stored)
                .extracting(HistoryItem::name)
                .containsExactlyInAnyOrder("Shared completion", "Home only");
        HistoryItem shared =
                stored.stream()
                        .filter(item -> item.name().equals("Shared completion"))
                        .findFirst()
                        .orElseThrow();
        assertThat(shared.eventAt()).isEqualTo(tokyoEvent);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_HISTORY)).isEqualTo(2);
    }

    @Test
    @DisplayName("Deleting an imported row removes its provenance and allows a valid reimport")
    void testDeletedImportedRowsCanBeReimportedFromAnotherSource() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_data.json"), arrayOf(taskJson("Reimport me", 600, 600)));
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(historyJson("Reimport history", TIMESTAMP)));
        migrator.migrateIfNecessary();

        taskRepository.deleteById(taskRepository.findAll().getFirst().id());
        historyRepository.deleteById(historyRepository.findAll().getFirst().id());
        assertThat(taskRepository.count()).isZero();
        assertThat(historyRepository.count()).isZero();
        assertThat(importedRowCount("tasks")).isZero();
        assertThat(importedRowCount("history")).isZero();

        // A different source snapshot contains the same records; the deleted provenance no longer
        // suppresses them.
        Files.writeString(
                homeDir.resolve(".taskflow_data.json"),
                arrayOf(taskJson("Reimport me", 600, 600), taskJson("Other task", 300, 300)));
        Files.writeString(
                homeDir.resolve(".taskflow_history.json"),
                arrayOf(
                        historyJson("Reimport history", TIMESTAMP),
                        historyJson("Other history", "11:00  —  01/10/26")));
        migrator.migrateIfNecessary();

        assertThat(taskRepository.findAll())
                .extracting(Task::name)
                .containsExactlyInAnyOrder("Reimport me", "Other task");
        assertThat(historyRepository.findAll())
                .extracting(HistoryItem::name)
                .containsExactlyInAnyOrder("Reimport history", "Other history");
    }

    @Test
    @DisplayName("A fully overlapping source is marked processed exactly once")
    void testFullyOverlappingSourceIsMarkedProcessedAndNotReimported() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_data.json"), arrayOf(taskJson("Shared", 600, 600)));
        migrator.migrateIfNecessary();

        // Different bytes (extra unknown field), identical record: nothing new is inserted, but the
        // second source is still recorded as processed.
        Files.writeString(
                homeDir.resolve(".taskflow_data.json"),
                "[{\"name\":\"Shared\",\"priority\":\"Media\",\"total_seconds\":600,"
                        + "\"remaining\":600,\"unknown_field\":true}]");
        migrator.migrateIfNecessary();

        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_TASKS)).isEqualTo(2);

        Files.copy(
                homeDir.resolve(".taskflow_data.json.migrated"),
                homeDir.resolve(".taskflow_data.json"));
        migrator.migrateIfNecessary();

        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_TASKS)).isEqualTo(2);
        assertThat(Files.exists(homeDir.resolve(".taskflow_data.json.migrated.1"))).isTrue();
    }

    @Test
    @DisplayName("Identical source copies are imported once and both are retired")
    void testIdenticalSourceCopiesImportOnce() throws IOException {
        String json = arrayOf(taskJson("Copy", 600, 600));
        Files.writeString(homeDir.resolve(".taskflow_data.json"), json);
        Files.writeString(dataDir.resolve("taskflow_data.json"), json);

        migrator.migrateIfNecessary();

        assertThat(taskRepository.count()).isEqualTo(1);
        // Identical bytes are the same source: one ledger entry, no duplicate rows.
        assertThat(legacySourceCount(LegacyImportRepository.KIND_TASKS)).isEqualTo(1);
        assertThat(Files.exists(homeDir.resolve(".taskflow_data.json.migrated"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json.migrated"))).isTrue();
    }

    @Test
    @DisplayName("A restored singleton source cannot overwrite newer geometry")
    void testRestoredGeometryDoesNotOverwriteCurrentValue() throws IOException {
        Files.writeString(
                dataDir.resolve("taskflow_geometry.json"),
                "{\"x\":1,\"y\":2,\"width\":340,\"height\":560}");
        migrator.migrateIfNecessary();

        preferenceRepository.saveGeometry(new WindowGeometry(9, 8, 700, 800));
        Files.copy(
                dataDir.resolve("taskflow_geometry.json.migrated"),
                dataDir.resolve("taskflow_geometry.json"));
        migrator.migrateIfNecessary();

        WindowGeometry geometry = preferenceRepository.loadGeometry().orElseThrow();
        assertThat(geometry.x()).isEqualTo(9.0);
        assertThat(geometry.width()).isEqualTo(700.0);
    }

    @Test
    @DisplayName("Conflicting geometry resolves deterministically: data directory wins")
    void testDataDirectoryGeometryWinsOverConflictingHomeGeometry() throws IOException {
        Files.writeString(
                homeDir.resolve(".taskflow_geometry.json"),
                "{\"x\":10,\"y\":20,\"width\":300,\"height\":500}");
        Files.writeString(
                dataDir.resolve("taskflow_geometry.json"),
                "{\"x\":40,\"y\":50,\"width\":360,\"height\":600}");

        migrator.migrateIfNecessary();

        WindowGeometry geometry = preferenceRepository.loadGeometry().orElseThrow();
        assertThat(geometry.x()).isEqualTo(40.0);
        assertThat(geometry.y()).isEqualTo(50.0);
        assertThat(geometry.width()).isEqualTo(360.0);
        assertThat(geometry.height()).isEqualTo(600.0);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_GEOMETRY)).isEqualTo(2);
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
        // first bad record would lose the remaining tasks.
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
    @DisplayName("An existing .migrated backup never causes settings to be re-applied")
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
    @DisplayName("A failed bulk import leaves no rows or source record and is retried")
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

        // The whole file is one transaction: no rows, no false source-completion record, and the
        // source is left for a retry.
        assertThat(taskRepository.count()).isZero();
        assertThat(legacySourceCount(LegacyImportRepository.KIND_TASKS)).isZero();
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json.migrated"))).isFalse();

        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER fail_second_task");
        }
        migrator.migrateIfNecessary();

        assertThat(taskRepository.count()).isEqualTo(2);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_TASKS)).isEqualTo(1);
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json"))).isFalse();
    }

    @Test
    @DisplayName("Handles corrupted JSON files gracefully without throwing")
    void testCorruptedJsonHandling() throws IOException {
        Files.writeString(dataDir.resolve("taskflow_data.json"), "{ NOT VALID JSON :::");
        migrator.migrateIfNecessary();
        assertThat(taskRepository.count()).isEqualTo(0);
        assertThat(legacySourceCount(LegacyImportRepository.KIND_TASKS)).isZero();
        assertThat(Files.exists(dataDir.resolve("taskflow_data.json"))).isTrue();
    }

    private void assertOrderIndependentTimestampMatching(String first, String second)
            throws IOException {
        // Import the timestamped record through an initial source, then present a different
        // snapshot with both records in the requested order.
        Files.writeString(
                dataDir.resolve("taskflow_history.json"),
                arrayOf(historyJson("Shared completion", TIMESTAMP)));
        migrator.migrateIfNecessary();
        Instant existingEvent = legacyInstant(TIMESTAMP);
        assertThat(historyRepository.count()).isEqualTo(1);

        Files.writeString(homeDir.resolve(".taskflow_history.json"), arrayOf(first, second));
        migrator.migrateIfNecessary();

        // The timestamped record matches its existing legacy row; the untimestamped record is a
        // genuinely additional entry, whichever order they appear in.
        List<HistoryItem> stored = historyRepository.findAll();
        assertThat(stored).hasSize(2);
        assertThat(stored).filteredOn(item -> item.eventAt().equals(existingEvent)).hasSize(1);
        assertThat(stored).filteredOn(item -> !item.eventAt().equals(existingEvent)).hasSize(1);
    }

    private int legacySourceCount(String kind) {
        String sql = "SELECT COUNT(*) FROM legacy_sources WHERE source_kind = ?";
        try (Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kind);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not count legacy sources", e);
        }
    }

    private int importedRowCount(String table) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE legacy_signature IS NOT NULL";
        try (Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        } catch (SQLException e) {
            throw new AssertionError("Could not count imported rows", e);
        }
    }

    private String canonicalTimestamp(String name) {
        String sql = "SELECT legacy_source_timestamp FROM history WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not read the canonical timestamp", e);
        }
    }

    private String legacySourceTimestamp(String name) {
        return canonicalTimestamp(name);
    }

    private static Instant legacyInstant(String text) {
        return DateTimeUtil.parseLegacyOrDefault(text, Instant.EPOCH);
    }

    private static Instant legacyInstant(String text, ZoneId zone) {
        return DateTimeUtil.parseLegacy(text, zone).orElseThrow();
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
        return historyJsonWithFields(name, eventAt, null);
    }

    private static String historyJsonWithFields(String name, String eventAt, String completedAt) {
        String eventField = eventAt == null ? "" : ",\"event_at\":\"" + eventAt + "\"";
        String completedField =
                completedAt == null ? "" : ",\"completed_at\":\"" + completedAt + "\"";
        return "{\"name\":\""
                + name
                + "\",\"priority\":\"Media\",\"total_seconds\":900,\"remaining\":0"
                + ",\"history_event\":\"completed\",\"completed_manually\":false"
                + eventField
                + completedField
                + "}";
    }
}
