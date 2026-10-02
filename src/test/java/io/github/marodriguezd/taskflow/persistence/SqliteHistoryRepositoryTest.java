package io.github.marodriguezd.taskflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SqliteHistoryRepositoryTest {

    private DatabaseManager databaseManager;
    private SqliteHistoryRepository repository;

    @BeforeEach
    void setUp() {
        databaseManager = DatabaseManager.inMemory();
        repository = new SqliteHistoryRepository(databaseManager);
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    @DisplayName("Saves history item and returns assigned ID")
    void testSaveAndFindById() {
        HistoryItem item =
                new HistoryItem(
                        null,
                        "Archived task",
                        Priority.HIGH,
                        1500,
                        0,
                        HistoryEventType.COMPLETED,
                        false,
                        Instant.now());
        HistoryItem saved = repository.save(item);

        assertThat(saved.id()).isNotNull().isPositive();
        assertThat(repository.findById(saved.id())).isPresent();
    }

    @Test
    @DisplayName("Returns history items in reverse chronological order (newest first)")
    void testFindAllOrdersDescending() {
        repository.save(item("Oldest", Priority.LOW, Instant.now().minusSeconds(100)));
        repository.save(item("Middle", Priority.MEDIUM, Instant.now().minusSeconds(50)));
        repository.save(item("Newest", Priority.HIGH, Instant.now()));

        List<HistoryItem> list = repository.findAll();
        assertThat(list).hasSize(3);
        assertThat(list)
                .extracting(HistoryItem::name)
                .containsExactly("Newest", "Middle", "Oldest");
    }

    @Test
    @DisplayName("Deletes history item by ID and clears all")
    void testDeleteAndClear() {
        HistoryItem saved1 = repository.save(item("H1", Priority.HIGH, Instant.now()));
        HistoryItem saved2 = repository.save(item("H2", Priority.LOW, Instant.now()));
        assertThat(repository.count()).isEqualTo(2);

        repository.deleteById(saved1.id());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(saved1.id())).isEmpty();

        repository.clearAll();
        assertThat(repository.count()).isZero();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("History count propagates database errors instead of returning zero")
    void propagatesCountFailure() throws Exception {
        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE history");
        }
        assertThatThrownBy(repository::count)
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("Could not count history");
    }

    private HistoryItem item(String name, Priority priority, Instant eventAt) {
        return new HistoryItem(
                null, name, priority, 600, 0, HistoryEventType.COMPLETED, false, eventAt);
    }
}
