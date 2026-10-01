package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.HistoryItem;
import java.util.List;
import java.util.Optional;

/** Repository interface for completed and deleted task history. */
public interface HistoryRepository {

    List<HistoryItem> findAll();

    Optional<HistoryItem> findById(long id);

    HistoryItem save(HistoryItem item);

    void deleteById(long id);

    void clearAll();

    int count();
}
