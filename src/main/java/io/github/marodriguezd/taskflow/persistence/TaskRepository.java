package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.Task;
import java.util.List;
import java.util.Optional;

/** Repository interface for managing active Task persistence. */
public interface TaskRepository extends TaskLifecycleRepository {

    List<Task> findAll();

    Optional<Task> findById(long id);

    Task save(Task task);

    /** Imports all tasks in a single transaction, used by legacy migration. */
    void saveAll(List<Task> tasks);

    void deleteById(long id);

    void updateRemainingSeconds(long id, int remainingSeconds);

    int count();

    void clearAll();
}
