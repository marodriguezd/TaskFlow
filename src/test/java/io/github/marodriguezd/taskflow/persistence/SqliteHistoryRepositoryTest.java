package io.github.marodriguezd.taskflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
        HistoryItem item1 =
                new HistoryItem(
                        null,
                        "Oldest",
                        Priority.LOW,
                        600,
                        0,
                        HistoryEventType.COMPLETED,
                        false,
                        Instant.now().minusSeconds(100));
        HistoryItem item2 =
                new HistoryItem(
                        null,
                        "Middle",
                        Priority.MEDIUM,
                        600,
                        0,
                        HistoryEventType.DELETED,
                        false,
                        Instant.now().minusSeconds(50));
        HistoryItem item3 =
                new HistoryItem(
                        null,
                        "Newest",
                        Priority.HIGH,
                        600,
                        0,
                        HistoryEventType.COMPLETED,
                        true,
                        Instant.now());

        repository.save(item1);
        repository.save(item2);
        repository.save(item3);

        List<HistoryItem> list = repository.findAll();
        assertThat(list).hasSize(3);
        assertThat(list.get(0).name()).isEqualTo("Newest");
        assertThat(list.get(1).name()).isEqualTo("Middle");
        assertThat(list.get(2).name()).isEqualTo("Oldest");
    }

    @Test
    @DisplayName("Deletes history item by ID and clears all")
    void testDeleteAndClear() {
        HistoryItem saved1 =
                repository.save(
                        new HistoryItem(
                                null,
                                "H1",
                                Priority.HIGH,
                                600,
                                0,
                                HistoryEventType.COMPLETED,
                                false,
                                Instant.now()));
        HistoryItem saved2 =
                repository.save(
                        new HistoryItem(
                                null,
                                "H2",
                                Priority.LOW,
                                600,
                                0,
                                HistoryEventType.DELETED,
                                false,
                                Instant.now()));
        assertThat(repository.count()).isEqualTo(2);

        repository.deleteById(saved1.id());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(saved1.id())).isEmpty();

        repository.clearAll();
        assertThat(repository.count()).isEqualTo(0);
        assertThat(repository.findAll()).isEmpty();
    }
}
