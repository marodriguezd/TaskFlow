package io.github.marodriguezd.taskflow.service;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.persistence.HistoryRepository;
import io.github.marodriguezd.taskflow.persistence.TaskRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service encapsulating task business logic, lifecycle transitions, and history archiving. */
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final HistoryRepository historyRepository;
    private final TimerService timerService;
    private final SoundService soundService;
    private final ExecutorService timerPersistenceExecutor;

    public TaskService(
            TaskRepository taskRepository,
            HistoryRepository historyRepository,
            TimerService timerService,
            SoundService soundService) {
        this(
                taskRepository,
                historyRepository,
                timerService,
                soundService,
                newTimerPersistenceExecutor());
    }

    TaskService(
            TaskRepository taskRepository,
            HistoryRepository historyRepository,
            TimerService timerService,
            SoundService soundService,
            ExecutorService timerPersistenceExecutor) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.historyRepository = Objects.requireNonNull(historyRepository);
        this.timerService = Objects.requireNonNull(timerService);
        this.soundService = Objects.requireNonNull(soundService);
        this.timerPersistenceExecutor = Objects.requireNonNull(timerPersistenceExecutor);

        // Listen for automatic timer completions
        this.timerService.addCompletionListener(this::handleTimerCompletion);
        this.timerService.addTickListener(this::handleTimerTick);
    }

    private static ExecutorService newTimerPersistenceExecutor() {
        return Executors.newSingleThreadExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "taskflow-timer-persistence");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public Optional<Task> getTaskById(long id) {
        return taskRepository.findById(id);
    }

    public Task createTask(String name, Priority priority, int minutes) {
        validateName(name);
        validateMinutes(minutes);

        Priority effPriority = priority != null ? priority : Priority.MEDIUM;
        int totalSeconds = minutes * 60;
        Task task = Task.create(name.trim(), effPriority, totalSeconds);
        Task saved = taskRepository.save(task);
        log.info(
                "Created new task id={}, name='{}', priority={}, totalSeconds={}",
                saved.id(),
                saved.name(),
                saved.priority(),
                saved.totalSeconds());
        return saved;
    }

    public Task updateTask(long id, String newName, Priority newPriority, int newMinutes) {
        validateName(newName);
        validateMinutes(newMinutes);

        timerService.stopIfRunning(id);
        awaitTimerPersistence();
        timerService.invalidateSnapshot(id);
        Task existing =
                taskRepository
                        .findById(id)
                        .orElseThrow(() -> new ValidationException("Task not found with ID " + id));

        int newTotalSeconds = newMinutes * 60;
        int newRemaining;

        // If the task was already expired (00:00), reset to new total time so it can run again
        if (existing.isExpired()) {
            newRemaining = newTotalSeconds;
        } else {
            // Scale remaining time proportionally to maintain relative progress
            double ratio = (double) existing.remainingSeconds() / (double) existing.totalSeconds();
            newRemaining = Math.max(0, (int) Math.round(newTotalSeconds * ratio));
        }

        Priority effPriority = newPriority != null ? newPriority : existing.priority();
        Task updated =
                existing.withDetails(newName.trim(), effPriority, newTotalSeconds, newRemaining);
        taskRepository.save(updated);
        timerService.invalidateSnapshot(id);
        timerService.updateActiveTask(updated);
        log.info(
                "Updated task id={}, name='{}', remaining={}",
                id,
                updated.name(),
                updated.remainingSeconds());
        return updated;
    }

    public void deleteTask(long id, boolean trackInHistory) {
        timerService.stopIfRunning(id);
        awaitTimerPersistence();
        timerService.invalidateSnapshot(id);
        Optional<Task> existingOpt = taskRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return;
        }

        Task task = existingOpt.get();
        if (trackInHistory && !task.isExpired()) {
            HistoryItem historyItem = HistoryItem.fromTask(task, HistoryEventType.DELETED, false);
            taskRepository.archiveAndDelete(task, historyItem);
            timerService.invalidateSnapshot(id);
            log.info("Archived deleted task id={} to history", id);
            return;
        }

        taskRepository.deleteById(id);
        timerService.invalidateSnapshot(id);
        log.info("Deleted task id={}", id);
    }

    public void completeTaskManually(long id) {
        timerService.stopIfRunning(id);
        awaitTimerPersistence();
        timerService.invalidateSnapshot(id);
        Optional<Task> existingOpt = taskRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return;
        }

        Task task = existingOpt.get();
        HistoryItem historyItem = HistoryItem.fromTask(task, HistoryEventType.COMPLETED, true);
        taskRepository.archiveAndDelete(task, historyItem);
        timerService.invalidateSnapshot(id);
        log.info("Manually completed task id={} and moved to history", id);
    }

    private void handleTimerCompletion(Task completedTask) {
        log.info(
                "Timer expired for task id={}, name='{}'",
                completedTask.id(),
                completedTask.name());
        try {
            timerPersistenceExecutor.execute(
                    () -> {
                        HistoryItem historyItem =
                                HistoryItem.fromTask(
                                        completedTask, HistoryEventType.COMPLETED, false);
                        try {
                            taskRepository.archiveCompletion(completedTask, historyItem);
                            soundService.playCompletionSound();
                        } catch (RuntimeException e) {
                            log.error(
                                    "Could not persist timer completion for task {}",
                                    completedTask.id(),
                                    e);
                        }
                    });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("Timer completion persistence executor is shutting down", e);
        }
    }

    private void handleTimerTick(Task activeTask) {
        // Pause can be initiated by a JavaFX event; serialize every timer write away from that
        // thread so SQLite contention never blocks UI interaction.
        try {
            timerPersistenceExecutor.execute(
                    () -> {
                        if (timerService.isLatestSnapshot(activeTask)
                                && !timerService.isCompletedTask(activeTask.id())) {
                            try {
                                taskRepository.updateRemainingSeconds(
                                        activeTask.id(), activeTask.remainingSeconds());
                            } catch (RuntimeException e) {
                                log.error(
                                        "Could not persist timer state for task {}",
                                        activeTask.id(),
                                        e);
                            }
                        }
                    });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.warn("Timer tick persistence executor is shutting down", e);
        }
    }

    private void awaitTimerPersistence() {
        try {
            timerPersistenceExecutor.submit(() -> {}).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for timer persistence", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Timer persistence failed", e.getCause());
        }
    }

    public void shutdown() {
        timerPersistenceExecutor.shutdown();
        try {
            if (!timerPersistenceExecutor.awaitTermination(
                    3, java.util.concurrent.TimeUnit.SECONDS)) {
                timerPersistenceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            timerPersistenceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public Task restoreTaskFromHistory(long historyId) {
        HistoryItem historyItem =
                historyRepository
                        .findById(historyId)
                        .orElseThrow(
                                () ->
                                        new ValidationException(
                                                "History item not found with ID " + historyId));

        Task saved = taskRepository.restore(historyItem);
        log.info(
                "Restored task id={} ('{}') from history item id={}",
                saved.id(),
                saved.name(),
                historyId);
        return saved;
    }

    public List<HistoryItem> getHistory() {
        return historyRepository.findAll();
    }

    public void clearHistory() {
        historyRepository.clearAll();
        log.info("Cleared all history records");
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Task name must not be empty.");
        }
    }

    private void validateMinutes(int minutes) {
        if (minutes < 1 || minutes > 999) {
            throw new ValidationException("Duration must be between 1 and 999 minutes.");
        }
    }
}
