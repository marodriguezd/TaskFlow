package io.github.marodriguezd.taskflow.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the SQLite database connection lifecycle and schema migrations.
 *
 * <p>Schema versions: {@code 1} is the original tasks/history/preferences schema; {@code 2} adds
 * additive legacy-import provenance (the {@code legacy_sources} table plus nullable provenance
 * columns on {@code tasks} and {@code history}). The upgrade never rewrites or drops existing rows,
 * and no pre-existing row is labelled as legacy-imported: the origin of rows written by builds
 * without provenance is unknown and must stay unknown.
 */
public class DatabaseManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final int CURRENT_SCHEMA_VERSION = 2;

    private static final String CREATE_LEGACY_SOURCES_TABLE =
            """
            CREATE TABLE IF NOT EXISTS legacy_sources (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_kind TEXT NOT NULL,
                source_hash TEXT NOT NULL,
                source_name TEXT NOT NULL,
                source_size INTEGER NOT NULL,
                imported_at TEXT NOT NULL,
                UNIQUE (source_kind, source_hash)
            )
            """;

    private final String jdbcUrl;
    private final boolean inMemory;
    private final Connection keepAliveConnection;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public DatabaseManager(Path dbPath) {
        this("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    public DatabaseManager(String url) {
        if (":memory:".equals(url) || "jdbc:sqlite::memory:".equals(url)) {
            // Unique shared in-memory database URI per manager instance
            this.jdbcUrl =
                    "jdbc:sqlite:file:memdb_" + System.nanoTime() + "?mode=memory&cache=shared";
            this.inMemory = true;
        } else {
            this.jdbcUrl = url;
            this.inMemory = url.contains("mode=memory");
        }

        try {
            // Anchor connection that stays open so SQLite keeps the in-memory database alive
            this.keepAliveConnection = DriverManager.getConnection(this.jdbcUrl);
            configurePragmas(keepAliveConnection);
        } catch (SQLException e) {
            this.closed.set(true);
            throw new PersistenceException(
                    "Could not open SQLite database connection: " + this.jdbcUrl, e);
        }

        initializeSchema();
    }

    public static DatabaseManager inMemory() {
        return new DatabaseManager("jdbc:sqlite::memory:");
    }

    public Connection getConnection() throws SQLException {
        if (closed.get()) {
            throw new SQLException("DatabaseManager is closed: " + jdbcUrl);
        }
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
            // Set the busy timeout before any statement that can take a write lock, so a
            // concurrent connection waits instead of failing the whole operation.
            stmt.execute("PRAGMA busy_timeout = 3000;");
            if (!inMemory) {
                stmt.execute("PRAGMA synchronous = NORMAL;");
            }
        }
    }

    private void configureDatabaseMode(Connection conn) throws SQLException {
        if (!inMemory) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
            }
        }
    }

    private void initializeSchema() {
        log.info("Initializing SQLite database schema at {}", jdbcUrl);
        try {
            configureDatabaseMode(keepAliveConnection);
            initializeSchemaTransaction();
        } catch (SQLException e) {
            close();
            throw new PersistenceException(
                    "Could not configure SQLite database pragmas: " + jdbcUrl, e);
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
                    }
                    if (version < CURRENT_SCHEMA_VERSION) {
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
        // Version-zero databases may contain the original schema without a version stamp, so the
        // idempotent column checks below cover both v0 and v1.
        if (fromVersion < 2) {
            upgradeToProvenanceSchema(conn);
        }
        ensureCurrentObjects(conn);
    }

    /**
     * Additive v2 upgrade: creates the source ledger and adds nullable provenance columns. Existing
     * rows keep {@code NULL} provenance, which honestly means "origin unknown" — backfilling would
     * risk labelling user-created rows as imported. The column checks make a retried upgrade safe
     * even if a previous attempt left some columns in place.
     */
    private void upgradeToProvenanceSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(CREATE_LEGACY_SOURCES_TABLE);
        }
        addColumnIfMissing(
                conn, "tasks", "legacy_source_id", "INTEGER REFERENCES legacy_sources(id)");
        addColumnIfMissing(conn, "tasks", "legacy_signature", "TEXT");
        addColumnIfMissing(
                conn, "history", "legacy_source_id", "INTEGER REFERENCES legacy_sources(id)");
        addColumnIfMissing(conn, "history", "legacy_signature", "TEXT");
        addColumnIfMissing(conn, "history", "legacy_source_timestamp", "TEXT");
    }

    private void addColumnIfMissing(Connection conn, String table, String column, String definition)
            throws SQLException {
        if (columnExists(conn, table, column)) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureCurrentObjects(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority)");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_history_event_at ON history(event_at DESC)");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_tasks_legacy_signature ON tasks(legacy_signature)");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_history_legacy_signature ON history(legacy_signature)");
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
                    updated_at TEXT NOT NULL,
                    legacy_source_id INTEGER REFERENCES legacy_sources(id),
                    legacy_signature TEXT
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
                    event_at TEXT NOT NULL,
                    legacy_source_id INTEGER REFERENCES legacy_sources(id),
                    legacy_signature TEXT,
                    legacy_source_timestamp TEXT
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
            stmt.executeUpdate(CREATE_LEGACY_SOURCES_TABLE);
            stmt.executeUpdate(createTasksTable);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority)");
            stmt.executeUpdate(createHistoryTable);
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_history_event_at ON history(event_at DESC)");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_tasks_legacy_signature ON tasks(legacy_signature)");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_history_legacy_signature ON history(legacy_signature)");
            stmt.executeUpdate(createPreferencesTable);
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
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
}
