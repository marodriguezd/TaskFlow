package io.github.marodriguezd.taskflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SqliteTaskRepositoryTest {

    private DatabaseManager databaseManager;
    private SqliteTaskRepository repository;
    private SqliteHistoryRepository historyRepository;

    @BeforeEach
    void setUp() {
        databaseManager = DatabaseManager.inMemory();
        repository = new SqliteTaskRepository(databaseManager);
        historyRepository = new SqliteHistoryRepository(databaseManager);
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    @DisplayName("Saves new task, assigns auto-increment ID and retrieves by ID")
    void testSaveAndFindById() {
        Task task = Task.create("Implement feature", Priority.HIGH, 1500);
        Task saved = repository.save(task);

        assertThat(saved.id()).isNotNull().isPositive();
        assertThat(saved.name()).isEqualTo("Implement feature");

        Optional<Task> retrieved = repository.findById(saved.id());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().name()).isEqualTo("Implement feature");
        assertThat(retrieved.get().priority()).isEqualTo(Priority.HIGH);
    }

    @Test
    @DisplayName("Orders tasks by Priority (High -> Medium -> Low)")
    void testFindAllOrdersByPriority() {
        repository.save(Task.create("Low task", Priority.LOW, 600));
        repository.save(Task.create("High task", Priority.HIGH, 1200));
        repository.save(Task.create("Medium task", Priority.MEDIUM, 900));

        List<Task> all = repository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all.get(0).priority()).isEqualTo(Priority.HIGH);
        assertThat(all.get(1).priority()).isEqualTo(Priority.MEDIUM);
        assertThat(all.get(2).priority()).isEqualTo(Priority.LOW);
    }

    @Test
    @DisplayName("Updates remaining seconds cleanly")
    void testUpdateRemainingSeconds() {
        Task saved = repository.save(Task.create("Timer task", Priority.MEDIUM, 1000));
        repository.updateRemainingSeconds(saved.id(), 450);

        Optional<Task> updated = repository.findById(saved.id());
        assertThat(updated).isPresent();
        assertThat(updated.get().remainingSeconds()).isEqualTo(450);
    }

    @Test
    @DisplayName("Deletes task by ID")
    void testDeleteById() {
        Task task = repository.save(Task.create("To delete", Priority.LOW, 600));
        assertThat(repository.count()).isEqualTo(1);

        repository.deleteById(task.id());
        assertThat(repository.count()).isEqualTo(0);
        assertThat(repository.findById(task.id())).isEmpty();
    }

    @Test
    @DisplayName("Clears all active tasks")
    void testClearAll() {
        repository.save(Task.create("T1", Priority.HIGH, 600));
        repository.save(Task.create("T2", Priority.LOW, 600));
        assertThat(repository.count()).isEqualTo(2);

        repository.clearAll();
        assertThat(repository.count()).isEqualTo(0);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Completion archiving skips tasks that were removed concurrently")
    void archiveCompletionSkipsVanishedTask() {
        Task task = repository.save(Task.create("Completed twice", Priority.MEDIUM, 600));
        repository.deleteById(task.id());

        repository.archiveCompletion(
                task, HistoryItem.fromTask(task, HistoryEventType.COMPLETED, false));

        assertThat(historyRepository.count()).isZero();
    }

    @Test
    @DisplayName("Delete archiving skips tasks that no longer exist")
    void archiveAndDeleteSkipsVanishedTask() {
        Task task = repository.save(Task.create("Deleted twice", Priority.MEDIUM, 600));
        repository.deleteById(task.id());

        repository.archiveAndDelete(
                task, HistoryItem.fromTask(task, HistoryEventType.DELETED, false));

        assertThat(historyRepository.count()).isZero();
    }

    @Test
    @DisplayName("Bulk import is atomic: a failing row rolls back the whole batch")
    void saveAllRollsBackOnFailure() throws Exception {
        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TRIGGER fail_second_bulk BEFORE INSERT ON tasks "
                            + "WHEN NEW.name = 'Second' "
                            + "BEGIN SELECT RAISE(ABORT, 'simulated batch failure'); END");
        }

        assertThatThrownBy(
                        () ->
                                repository.saveAll(
                                        List.of(
                                                Task.create("First", Priority.HIGH, 600),
                                                Task.create("Second", Priority.LOW, 600))))
                .isInstanceOf(PersistenceException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("A corrupt task row is skipped instead of breaking the whole list")
    void corruptRowDoesNotBreakListing() throws Exception {
        Task healthy = repository.save(Task.create("Healthy", Priority.HIGH, 600));
        long corruptId;
        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO tasks (name, priority, total_seconds, remaining_seconds, created_at, updated_at) "
                            + "VALUES ('Corrupt', 'HIGH', 0, 0, 'not-a-timestamp', 'not-a-timestamp')");
            try (var keys = statement.executeQuery("SELECT last_insert_rowid()")) {
                assertThat(keys.next()).isTrue();
                corruptId = keys.getLong(1);
            }
        }

        List<Task> all = repository.findAll();
        assertThat(all).extracting(Task::name).containsExactly("Healthy");
        assertThat(repository.findById(healthy.id())).isPresent();
        assertThat(repository.findById(corruptId)).isEmpty();
    }

    @Test
    @DisplayName("Restore rolls back the inserted task if removing history fails")
    void restoreRollbackPreservesHistory() throws Exception {
        HistoryItem item =
                historyRepository.save(
                        new HistoryItem(
                                null,
                                "Atomic restore",
                                Priority.HIGH,
                                600,
                                300,
                                HistoryEventType.DELETED,
                                false,
                                Instant.now()));
        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TRIGGER fail_history_delete BEFORE DELETE ON history "
                            + "BEGIN SELECT RAISE(ABORT, 'simulated history delete failure'); END");
        }

        assertThatThrownBy(() -> repository.restore(item)).isInstanceOf(PersistenceException.class);
        assertThat(repository.findAll()).isEmpty();
        assertThat(historyRepository.findById(item.id())).isPresent();
    }
}
