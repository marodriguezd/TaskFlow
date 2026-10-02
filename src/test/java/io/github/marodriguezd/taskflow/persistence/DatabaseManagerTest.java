package io.github.marodriguezd.taskflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseManagerTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("Fresh databases start at schema version one and initialize idempotently")
    void initializesVersionedSchemaOnce() throws Exception {
        DatabaseManager manager = DatabaseManager.inMemory();
        try {
            assertThat(userVersion(manager)).isEqualTo(1);
            new SqliteTaskRepository(manager).count();
            new SqliteHistoryRepository(manager).count();
        } finally {
            manager.close();
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

    private int userVersion(DatabaseManager manager) throws Exception {
        try (Connection connection = manager.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }
}
