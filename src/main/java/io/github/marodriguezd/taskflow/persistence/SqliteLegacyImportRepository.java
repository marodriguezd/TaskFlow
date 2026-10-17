package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Task;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SQLite implementation of {@link LegacyImportRepository}. */
public class SqliteLegacyImportRepository implements LegacyImportRepository {

    private static final Logger log = LoggerFactory.getLogger(SqliteLegacyImportRepository.class);

    private static final String INSERT_SOURCE =
            """
            INSERT INTO legacy_sources
                (source_kind, source_hash, source_name, source_size, imported_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(source_kind, source_hash) DO NOTHING
            """;

    private static final String INSERT_TASK =
            """
            INSERT INTO tasks
                (name, priority, total_seconds, remaining_seconds, created_at, updated_at,
                 legacy_source_id, legacy_signature)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_HISTORY =
            """
            INSERT INTO history
                (name, priority, total_seconds, remaining_seconds, event_type,
                 completed_manually, event_at, legacy_source_id, legacy_signature,
                 legacy_source_timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final DatabaseManager databaseManager;

    public SqliteLegacyImportRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean isSourceProcessed(String sourceKind, String sourceHash) {
        String sql =
                "SELECT 1 FROM legacy_sources WHERE source_kind = ? AND source_hash = ? LIMIT 1";
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sourceKind);
            stmt.setString(2, sourceHash);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Could not read legacy source provenance", e);
        }
    }

    @Override
    public ImportOutcome importBatch(ImportBatch batch) {
        return databaseManager.inTransaction(conn -> importBatch(conn, batch));
    }

    private ImportOutcome importBatch(Connection conn, ImportBatch batch) throws SQLException {
        boolean sourceInserted;
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_SOURCE)) {
            stmt.setString(1, batch.sourceKind());
            stmt.setString(2, batch.sourceHash());
            stmt.setString(3, batch.sourceName());
            stmt.setLong(4, batch.sourceSize());
            stmt.setString(5, Instant.now().toString());
            sourceInserted = stmt.executeUpdate() > 0;
        }
        if (!sourceInserted) {
            // A previous attempt committed and only the rename failed. The rows and their
            // provenance already exist; importing again would duplicate them.
            log.info(
                    "Legacy source {} ({}) was already imported; skipping {} task(s) and {} history item(s)",
                    batch.sourceName(),
                    batch.sourceKind(),
                    batch.tasks().size(),
                    batch.history().size());
            return new ImportOutcome(true, 0, batch.tasks().size(), 0, batch.history().size());
        }

        long sourceId = lastInsertedRowId(conn);
        Map<String, Integer> taskCounts = signatureCounts(conn, "tasks");
        Map<String, Integer> historyCounts = signatureCounts(conn, "history");

        int insertedTasks = 0;
        int skippedTasks = 0;
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_TASK)) {
            for (LegacyTaskCandidate candidate : batch.tasks()) {
                if (consume(taskCounts, candidate.signature())) {
                    skippedTasks++;
                    continue;
                }
                bindTask(stmt, candidate.task(), sourceId, candidate.signature());
                stmt.executeUpdate();
                insertedTasks++;
            }
        }

        int insertedHistory = 0;
        int skippedHistory = 0;
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_HISTORY)) {
            for (LegacyHistoryCandidate candidate : batch.history()) {
                if (consume(historyCounts, candidate.signature())) {
                    skippedHistory++;
                    continue;
                }
                bindHistory(stmt, candidate, sourceId);
                stmt.executeUpdate();
                insertedHistory++;
            }
        }

        return new ImportOutcome(
                false, insertedTasks, skippedTasks, insertedHistory, skippedHistory);
    }

    @Override
    public void markSourceProcessed(
            String sourceKind, String sourceHash, String sourceName, long sourceSize) {
        databaseManager.inTransaction(
                conn -> {
                    try (PreparedStatement stmt = conn.prepareStatement(INSERT_SOURCE)) {
                        stmt.setString(1, sourceKind);
                        stmt.setString(2, sourceHash);
                        stmt.setString(3, sourceName);
                        stmt.setLong(4, sourceSize);
                        stmt.setString(5, Instant.now().toString());
                        stmt.executeUpdate();
                    }
                    return null;
                });
    }

    private static boolean consume(Map<String, Integer> counts, String signature) {
        int available = counts.getOrDefault(signature, 0);
        if (available == 0) {
            return false;
        }
        counts.put(signature, available - 1);
        return true;
    }

    /**
     * Counts only rows that this migrator imported. Application-created rows have a {@code NULL}
     * signature and are deliberately invisible here: they must never suppress a legacy record.
     */
    private static Map<String, Integer> signatureCounts(Connection conn, String table)
            throws SQLException {
        String sql =
                "SELECT legacy_signature, COUNT(*) FROM "
                        + table
                        + " WHERE legacy_signature IS NOT NULL GROUP BY legacy_signature";
        Map<String, Integer> counts = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString(1), rs.getInt(2));
            }
        }
        return counts;
    }

    private static long lastInsertedRowId(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT last_insert_rowid()");
                ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
                throw new PersistenceException("Could not resolve the legacy source id");
            }
            return rs.getLong(1);
        }
    }

    private static void bindTask(PreparedStatement stmt, Task task, long sourceId, String signature)
            throws SQLException {
        stmt.setString(1, task.name());
        stmt.setString(2, task.priority().name());
        stmt.setInt(3, task.totalSeconds());
        stmt.setInt(4, task.remainingSeconds());
        stmt.setString(5, task.createdAt().toString());
        stmt.setString(6, task.updatedAt().toString());
        stmt.setLong(7, sourceId);
        stmt.setString(8, signature);
    }

    private static void bindHistory(
            PreparedStatement stmt, LegacyHistoryCandidate candidate, long sourceId)
            throws SQLException {
        HistoryItem item = candidate.item();
        stmt.setString(1, item.name());
        stmt.setString(2, item.priority().name());
        stmt.setInt(3, item.totalSeconds());
        stmt.setInt(4, item.remainingSeconds());
        stmt.setString(5, item.eventType().getCode());
        stmt.setInt(6, item.completedManually() ? 1 : 0);
        stmt.setString(7, item.eventAt().toString());
        stmt.setLong(8, sourceId);
        stmt.setString(9, candidate.signature());
        stmt.setString(10, candidate.canonicalSourceTimestamp());
    }

    /** SHA-256 of the original source bytes, used as the durable source identity. */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
