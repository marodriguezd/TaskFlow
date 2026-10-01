package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SQLite implementation of HistoryRepository. */
public class SqliteHistoryRepository implements HistoryRepository {

    private static final Logger log = LoggerFactory.getLogger(SqliteHistoryRepository.class);
    private final DatabaseManager databaseManager;

    public SqliteHistoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public List<HistoryItem> findAll() {
        String sql =
                """
                SELECT id, name, priority, total_seconds, remaining_seconds, event_type, completed_manually, event_at
                FROM history
                ORDER BY id DESC
                """;

        List<HistoryItem> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Error reading history from database", e);
            throw new PersistenceException("Could not query history", e);
        }
        return list;
    }

    @Override
    public Optional<HistoryItem> findById(long id) {
        String sql =
                """
                SELECT id, name, priority, total_seconds, remaining_seconds, event_type, completed_manually, event_at
                FROM history
                WHERE id = ?
                """;

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding history item by id {}", id, e);
            throw new PersistenceException("Could not find history item by id " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public HistoryItem save(HistoryItem item) {
        String sql =
                """
                INSERT INTO history (name, priority, total_seconds, remaining_seconds, event_type, completed_manually, event_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, item.name());
            stmt.setString(2, item.priority().name());
            stmt.setInt(3, item.totalSeconds());
            stmt.setInt(4, item.remainingSeconds());
            stmt.setString(5, item.eventType().getCode());
            stmt.setInt(6, item.completedManually() ? 1 : 0);
            stmt.setString(7, item.eventAt().toString());

            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return item.withId(keys.getLong(1));
                } else {
                    throw new PersistenceException("Creating history item failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            log.error("Error inserting history item: {}", item, e);
            throw new PersistenceException("Could not insert history item", e);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM history WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error deleting history item: {}", id, e);
            throw new PersistenceException("Could not delete history item " + id, e);
        }
    }

    @Override
    public void clearAll() {
        try (Connection conn = databaseManager.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM history");
        } catch (SQLException e) {
            log.error("Error clearing history", e);
            throw new PersistenceException("Could not clear history", e);
        }
    }

    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM history";
        try (Connection conn = databaseManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("Error counting history rows", e);
        }
        return 0;
    }

    private HistoryItem mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String name = rs.getString("name");
        Priority priority = Priority.fromDisplayName(rs.getString("priority"));
        int totalSeconds = rs.getInt("total_seconds");
        int remainingSeconds = rs.getInt("remaining_seconds");
        HistoryEventType eventType = HistoryEventType.fromCode(rs.getString("event_type"));
        boolean completedManually = rs.getInt("completed_manually") == 1;
        Instant eventAt = parseInstantSafe(rs.getString("event_at"));

        return new HistoryItem(
                id,
                name,
                priority,
                totalSeconds,
                remainingSeconds,
                eventType,
                completedManually,
                eventAt);
    }

    private Instant parseInstantSafe(String text) {
        if (text == null || text.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
