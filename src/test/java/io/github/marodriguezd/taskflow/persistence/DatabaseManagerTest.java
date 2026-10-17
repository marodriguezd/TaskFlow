package io.github.marodriguezd.taskflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.domain.ThemeMode;
import io.github.marodriguezd.taskflow.domain.UserPreferences;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseManagerTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("Fresh databases start at schema version two and initialize idempotently")
    void initializesVersionedSchemaOnce() throws Exception {
        DatabaseManager manager = DatabaseManager.inMemory();
        try {
            assertThat(userVersion(manager)).isEqualTo(2);
            new SqliteTaskRepository(manager).count();
            new SqliteHistoryRepository(manager).count();
            new SqliteLegacyImportRepository(manager).isSourceProcessed("tasks", "no-such-hash");
            assertThat(columnNames(manager, "tasks"))
                    .contains("legacy_source_id", "legacy_signature");
            assertThat(columnNames(manager, "history"))
                    .contains("legacy_source_id", "legacy_signature", "legacy_source_timestamp");
            assertThat(tableNames(manager)).contains("legacy_sources");
        } finally {
            manager.close();
        }
    }

    @Test
    @DisplayName("Schema v1 upgrades to v2 additively and preserves every row and setting")
    void upgradesV1DatabaseWithoutDataLoss() throws Exception {
        Path databaseFile = tempDir.resolve("v1.db");
        String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        createVersionOneDatabase(url);

        DatabaseManager manager = new DatabaseManager(url);
        try {
            assertThat(userVersion(manager)).isEqualTo(2);
            assertPreservedV1Data(manager);
            assertThat(columnNames(manager, "tasks"))
                    .contains("legacy_source_id", "legacy_signature");
            assertThat(columnNames(manager, "history"))
                    .contains("legacy_source_id", "legacy_signature", "legacy_source_timestamp");
            assertThat(tableNames(manager)).contains("legacy_sources");
            // Unknown origins are never invented: no pre-existing row is labelled as imported.
            assertThat(countWhereNotNull(manager, "tasks")).isZero();
            assertThat(countWhereNotNull(manager, "history")).isZero();
        } finally {
            manager.close();
        }

        // Opening the upgraded database again is a no-op and keeps data + version intact.
        DatabaseManager reopened = new DatabaseManager(url);
        try {
            assertThat(userVersion(reopened)).isEqualTo(2);
            assertPreservedV1Data(reopened);
            assertThat(countWhereNotNull(reopened, "tasks")).isZero();
            assertThat(countWhereNotNull(reopened, "history")).isZero();
        } finally {
            reopened.close();
        }
    }

    @Test
    @DisplayName("Transactions roll back all writes when an operation fails")
    void transactionRollsBack() throws Exception {
        DatabaseManager manager = DatabaseManager.inMemory();
        try {
            assertThatThrownBy(
                            () ->
                                    manager.inTransaction(
                                            connection -> {
                                                try (Statement statement =
                                                        connection.createStatement()) {
                                                    statement.executeUpdate(
                                                            "INSERT INTO preferences(pref_key, pref_value) VALUES ('before_failure', 'yes')");
                                                    statement.executeUpdate(
                                                            "INSERT INTO missing_table(value) VALUES ('failure')");
                                                }
                                                return null;
                                            }))
                    .isInstanceOf(PersistenceException.class);

            try (Connection connection = manager.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet result =
                            statement.executeQuery(
                                    "SELECT COUNT(*) FROM preferences WHERE pref_key = 'before_failure'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
        } finally {
            manager.close();
        }
    }

    @Test
    @DisplayName("A database with an unsupported future schema version is rejected")
    void rejectsNewerSchemaVersion() throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("future-schema.db");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 99");
        }
        assertThatThrownBy(() -> new DatabaseManager(url))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("newer than supported");
    }

    private void createVersionOneDatabase(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE tasks ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, "
                            + "priority TEXT NOT NULL, total_seconds INTEGER NOT NULL, "
                            + "remaining_seconds INTEGER NOT NULL, created_at TEXT NOT NULL, "
                            + "updated_at TEXT NOT NULL)");
            statement.executeUpdate("CREATE INDEX idx_tasks_priority ON tasks(priority)");
            statement.executeUpdate(
                    "CREATE TABLE history ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, "
                            + "priority TEXT NOT NULL, total_seconds INTEGER NOT NULL, "
                            + "remaining_seconds INTEGER NOT NULL, event_type TEXT NOT NULL, "
                            + "completed_manually INTEGER NOT NULL, event_at TEXT NOT NULL)");
            statement.executeUpdate("CREATE INDEX idx_history_event_at ON history(event_at DESC)");
            statement.executeUpdate(
                    "CREATE TABLE preferences (pref_key TEXT PRIMARY KEY, "
                            + "pref_value TEXT NOT NULL)");
            statement.executeUpdate(
                    "INSERT INTO tasks(name, priority, total_seconds, remaining_seconds, "
                            + "created_at, updated_at) VALUES "
                            + "('Kept task', 'MEDIUM', 600, 300, "
                            + "'2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
            statement.executeUpdate(
                    "INSERT INTO history(name, priority, total_seconds, remaining_seconds, "
                            + "event_type, completed_manually, event_at) VALUES "
                            + "('Kept history', 'HIGH', 900, 0, 'completed', 1, "
                            + "'2026-01-02T10:00:00Z')");
            statement.executeUpdate(
                    "INSERT INTO preferences(pref_key, pref_value) VALUES "
                            + "('theme', 'light'), ('always_on_top', 'true'), "
                            + "('language', 'fr'), ('sound_enabled', 'false'), "
                            + "('glass_opacity', '0.65'), ('geo_x', '11'), ('geo_y', '22'), "
                            + "('geo_width', '333'), ('geo_height', '555')");
            statement.execute("PRAGMA user_version = 1");
        }
    }

    private void assertPreservedV1Data(DatabaseManager manager) {
        List<Task> tasks = new SqliteTaskRepository(manager).findAll();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().name()).isEqualTo("Kept task");
        assertThat(tasks.getFirst().priority()).isEqualTo(Priority.MEDIUM);
        assertThat(tasks.getFirst().remainingSeconds()).isEqualTo(300);

        List<HistoryItem> history = new SqliteHistoryRepository(manager).findAll();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().name()).isEqualTo("Kept history");
        assertThat(history.getFirst().completedManually()).isTrue();
        assertThat(history.getFirst().eventAt()).isEqualTo(Instant.parse("2026-01-02T10:00:00Z"));

        UserPreferences preferences =
                new SqlitePreferenceRepository(manager).loadPreferences(false);
        assertThat(preferences.theme()).isEqualTo(ThemeMode.LIGHT);
        assertThat(preferences.alwaysOnTop()).isTrue();
        assertThat(preferences.language()).isEqualTo("fr");
        assertThat(preferences.soundEnabled()).isFalse();
        assertThat(preferences.glassOpacity()).isEqualTo(0.65);
        assertThat(new SqlitePreferenceRepository(manager).loadGeometry())
                .hasValueSatisfying(
                        geometry -> {
                            assertThat(geometry.x()).isEqualTo(11.0);
                            assertThat(geometry.y()).isEqualTo(22.0);
                            assertThat(geometry.width()).isEqualTo(333.0);
                            assertThat(geometry.height()).isEqualTo(555.0);
                        });
    }

    private int countWhereNotNull(DatabaseManager manager, String table) throws Exception {
        try (Connection connection = manager.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM "
                                        + table
                                        + " WHERE legacy_signature IS NOT NULL")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private List<String> columnNames(DatabaseManager manager, String table) throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection connection = manager.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                names.add(result.getString("name"));
            }
        }
        return names;
    }

    private List<String> tableNames(DatabaseManager manager) throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection connection = manager.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names;
    }

    private int userVersion(DatabaseManager manager) throws Exception {
        try (Connection connection = manager.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }
}
