package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Task;

/** Atomic persistence operations spanning active tasks and history. */
public interface TaskLifecycleRepository {

    void archiveAndDelete(Task task, HistoryItem historyItem);

    void archiveCompletion(Task task, HistoryItem historyItem);

    Task restore(HistoryItem historyItem);
}
