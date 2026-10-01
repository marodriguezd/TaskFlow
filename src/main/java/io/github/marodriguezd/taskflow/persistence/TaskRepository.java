package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.Task;
import java.util.List;
import java.util.Optional;

/** Repository interface for managing active Task persistence. */
public interface TaskRepository {

    List<Task> findAll();

    Optional<Task> findById(long id);

    Task save(Task task);

    void deleteById(long id);

    void updateRemainingSeconds(long id, int remainingSeconds);

    int count();

    void clearAll();
}
