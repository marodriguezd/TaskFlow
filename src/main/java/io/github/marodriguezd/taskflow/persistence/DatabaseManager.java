package io.github.marodriguezd.taskflow.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the SQLite database connection lifecycle and schema migrations. */
public class DatabaseManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;

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

    public <T> T inTransaction(TransactionWork<T> work) {
        try (Connection conn = getConnection()) {
            return inTransaction(conn, work);
        } catch (SQLException e) {
            throw new PersistenceException("Could not execute database transaction", e);
        }
    }

    private <T> T inTransaction(Connection conn, TransactionWork<T> work) {
        try {
            conn.setAutoCommit(false);
            T result = work.execute(conn);
            conn.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            if (e instanceof PersistenceException persistenceException) {
                throw persistenceException;
            }
            throw new PersistenceException("Database transaction failed", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                log.warn("Could not restore SQLite auto-commit mode", e);
            }
        }
    }

    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute(Connection connection) throws SQLException;
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
        try {
            initializeSchemaTransaction();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    private void initializeSchemaTransaction() {
        inTransaction(
                keepAliveConnection,
                conn -> {
                    int version;
                    try (Statement stmt = conn.createStatement();
                            ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
                        version = rs.next() ? rs.getInt(1) : 0;
                    }
                    if (version > CURRENT_SCHEMA_VERSION) {
                        throw new PersistenceException(
                                "Database schema version "
                                        + version
                                        + " is newer than supported version "
                                        + CURRENT_SCHEMA_VERSION);
                    }

                    if (version == 0) {
                        createCurrentSchema(conn);
                    } else if (version < CURRENT_SCHEMA_VERSION) {
                        migrateSchema(conn, version);
                    } else {
                        ensureCurrentObjects(conn);
                    }
                    if (version < CURRENT_SCHEMA_VERSION) {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute("PRAGMA user_version = " + CURRENT_SCHEMA_VERSION);
                        }
                    }
                    log.info("Database schema version {} is ready.", CURRENT_SCHEMA_VERSION);
                    return null;
                });
    }

    private void migrateSchema(Connection conn, int fromVersion) throws SQLException {
        // Version-zero databases contain the original schema, which is unchanged for v1.
        // Version-specific additive steps belong here before updating PRAGMA user_version.
        if (fromVersion < 1) {
            createCurrentSchema(conn);
        }
        ensureCurrentObjects(conn);
    }

    private void ensureCurrentObjects(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority)");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_history_event_at ON history(event_at DESC)");
        }
    }

    private void createCurrentSchema(Connection conn) throws SQLException {
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
                """;
        String createPreferencesTable =
                """
                CREATE TABLE IF NOT EXISTS preferences (
                    pref_key TEXT PRIMARY KEY,
                    pref_value TEXT NOT NULL
                );
                """;

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTasksTable);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority)");
            stmt.executeUpdate(createHistoryTable);
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_history_event_at ON history(event_at DESC)");
            stmt.executeUpdate(createPreferencesTable);
        }
    }

    @Override
    public void close() {
        if (keepAliveConnection != null) {
            try {
                if (!keepAliveConnection.isClosed()) {
                    keepAliveConnection.close();
                }
            } catch (SQLException e) {
                log.warn("Could not close SQLite keep-alive connection", e);
            }
        }
    }
}
