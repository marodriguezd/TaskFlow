package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
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

/** SQLite implementation of TaskRepository. */
public class SqliteTaskRepository implements TaskRepository {

    private static final Logger log = LoggerFactory.getLogger(SqliteTaskRepository.class);
    private final DatabaseManager databaseManager;

    public SqliteTaskRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public List<Task> findAll() {
        String sql =
                """
                SELECT id, name, priority, total_seconds, remaining_seconds, created_at, updated_at
                FROM tasks
                ORDER BY
                    CASE priority
                        WHEN 'HIGH' THEN 0
                        WHEN 'Alta' THEN 0
                        WHEN 'MEDIUM' THEN 1
                        WHEN 'Media' THEN 1
                        WHEN 'LOW' THEN 2
                        WHEN 'Baja' THEN 2
                        ELSE 3
                    END,
                    id ASC
                """;

        List<Task> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Error reading tasks from database", e);
            throw new PersistenceException("Could not query tasks", e);
        }
        return list;
    }

    @Override
    public Optional<Task> findById(long id) {
        String sql =
                """
                SELECT id, name, priority, total_seconds, remaining_seconds, created_at, updated_at
                FROM tasks
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
            log.error("Error finding task by id {}", id, e);
            throw new PersistenceException("Could not find task by id " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public Task save(Task task) {
        if (task.id() == null) {
            return insert(task);
        } else {
            return update(task);
        }
    }

    private Task insert(Task task) {
        String sql =
                """
                INSERT INTO tasks (name, priority, total_seconds, remaining_seconds, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, task.name());
            stmt.setString(2, task.priority().name());
            stmt.setInt(3, task.totalSeconds());
            stmt.setInt(4, task.remainingSeconds());
            stmt.setString(5, task.createdAt().toString());
            stmt.setString(6, task.updatedAt().toString());

            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long id = generatedKeys.getLong(1);
                    return task.withId(id);
                } else {
                    throw new PersistenceException("Creating task failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            log.error("Error inserting task: {}", task, e);
            throw new PersistenceException("Could not insert task", e);
        }
    }

    private Task update(Task task) {
        String sql =
                """
                UPDATE tasks
                SET name = ?, priority = ?, total_seconds = ?, remaining_seconds = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, task.name());
            stmt.setString(2, task.priority().name());
            stmt.setInt(3, task.totalSeconds());
            stmt.setInt(4, task.remainingSeconds());
            stmt.setString(5, Instant.now().toString());
            stmt.setLong(6, task.id());

            stmt.executeUpdate();
            return task;
        } catch (SQLException e) {
            log.error("Error updating task: {}", task, e);
            throw new PersistenceException("Could not update task", e);
        }
    }

    @Override
    public void deleteById(long id) {
        String sql = "DELETE FROM tasks WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error deleting task with id: {}", id, e);
            throw new PersistenceException("Could not delete task with id " + id, e);
        }
    }

    @Override
    public void updateRemainingSeconds(long id, int remainingSeconds) {
        String sql = "UPDATE tasks SET remaining_seconds = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, remainingSeconds);
            stmt.setString(2, Instant.now().toString());
            stmt.setLong(3, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error updating remaining seconds for task id: {}", id, e);
            throw new PersistenceException("Could not update remaining seconds for id " + id, e);
        }
    }

    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM tasks";
        try (Connection conn = databaseManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("Error counting tasks", e);
        }
        return 0;
    }

    @Override
    public void clearAll() {
        try (Connection conn = databaseManager.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM tasks");
        } catch (SQLException e) {
            log.error("Error clearing all tasks", e);
            throw new PersistenceException("Could not clear tasks", e);
        }
    }

    private Task mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String name = rs.getString("name");
        String priorityStr = rs.getString("priority");
        Priority priority = Priority.fromDisplayName(priorityStr);
        int totalSeconds = rs.getInt("total_seconds");
        int remainingSeconds = rs.getInt("remaining_seconds");
        Instant createdAt = parseInstantSafe(rs.getString("created_at"), "created_at");
        Instant updatedAt = parseInstantSafe(rs.getString("updated_at"), "updated_at");

        return new Task(id, name, priority, totalSeconds, remainingSeconds, createdAt, updatedAt);
    }

    private Instant parseInstantSafe(String text, String column) {
        if (text == null || text.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            log.warn(
                    "Malformed {} value in tasks table: '{}'; defaulting to current time: {}",
                    column,
                    text,
                    e.getMessage());
            return Instant.now();
        }
    }
}
