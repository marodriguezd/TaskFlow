package io.github.marodriguezd.taskflow.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the SQLite database connection lifecycle and schema migrations. */
public class DatabaseManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private final String jdbcUrl;
    private final Connection keepAliveConnection;

    public DatabaseManager(Path dbPath) {
        this("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    public DatabaseManager(String url) {
        if (":memory:".equals(url) || "jdbc:sqlite::memory:".equals(url)) {
            // Unique shared in-memory database URI per manager instance
            this.jdbcUrl =
                    "jdbc:sqlite:file:memdb_" + System.nanoTime() + "?mode=memory&cache=shared";
        } else {
            this.jdbcUrl = url;
        }

        try {
            // Anchor connection that stays open so SQLite keeps the in-memory database alive
            this.keepAliveConnection = DriverManager.getConnection(this.jdbcUrl);
            configurePragmas(keepAliveConnection);
        } catch (SQLException e) {
            throw new PersistenceException(
                    "Could not open SQLite database connection: " + this.jdbcUrl, e);
        }

        initializeSchema();
    }

    public static DatabaseManager inMemory() {
        return new DatabaseManager("jdbc:sqlite::memory:");
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        configurePragmas(conn);
        return conn;
    }

    private void configurePragmas(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            if (!jdbcUrl.contains("mode=memory")) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
            }
            stmt.execute("PRAGMA busy_timeout = 3000;");
        }
    }

    private void initializeSchema() {
        log.info("Initializing SQLite database schema at {}", jdbcUrl);
        String createTasksTable =
                """
                CREATE TABLE IF NOT EXISTS tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    total_seconds INTEGER NOT NULL,
                    remaining_seconds INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority);
                """;

        String createHistoryTable =
                """
                CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    total_seconds INTEGER NOT NULL,
                    remaining_seconds INTEGER NOT NULL,
                    event_type TEXT NOT NULL,
                    completed_manually INTEGER NOT NULL,
                    event_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_history_event_at ON history(event_at DESC);
                """;

        String createPreferencesTable =
                """
                CREATE TABLE IF NOT EXISTS preferences (
                    pref_key TEXT PRIMARY KEY,
                    pref_value TEXT NOT NULL
                );
                """;

        try (Statement stmt = keepAliveConnection.createStatement()) {
            stmt.executeUpdate(createTasksTable);
            stmt.executeUpdate(createHistoryTable);
            stmt.executeUpdate(createPreferencesTable);
            log.info("Database schema initialized successfully.");
        } catch (SQLException e) {
            log.error("Fatal error creating SQLite schema", e);
            throw new PersistenceException("Failed to initialize database schema", e);
        }
    }

    @Override
    public void close() {
        if (keepAliveConnection != null) {
            try {
                if (!keepAliveConnection.isClosed()) {
                    keepAliveConnection.close();
                }
            } catch (SQLException ignored) {
            }
        }
    }
}
