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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service encapsulating task business logic, lifecycle transitions, and history archiving. */
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final HistoryRepository historyRepository;
    private final TimerService timerService;
    private final SoundService soundService;

    public TaskService(
            TaskRepository taskRepository,
            HistoryRepository historyRepository,
            TimerService timerService,
            SoundService soundService) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.historyRepository = Objects.requireNonNull(historyRepository);
        this.timerService = Objects.requireNonNull(timerService);
        this.soundService = Objects.requireNonNull(soundService);

        // Listen for automatic timer completions
        this.timerService.addCompletionListener(this::handleTimerCompletion);
        this.timerService.addTickListener(this::handleTimerTick);
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

        Task existing =
                taskRepository
                        .findById(id)
                        .orElseThrow(() -> new ValidationException("Task not found with ID " + id));

        timerService.stopIfRunning(id);

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

        Optional<Task> existingOpt = taskRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return;
        }

        Task task = existingOpt.get();
        if (trackInHistory && !task.isExpired()) {
            HistoryItem historyItem = HistoryItem.fromTask(task, HistoryEventType.DELETED, false);
            historyRepository.save(historyItem);
            log.info("Archived deleted task id={} to history", id);
        }

        taskRepository.deleteById(id);
        log.info("Deleted task id={}", id);
    }

    public void completeTaskManually(long id) {
        timerService.stopIfRunning(id);

        Optional<Task> existingOpt = taskRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return;
        }

        Task task = existingOpt.get();
        HistoryItem historyItem = HistoryItem.fromTask(task, HistoryEventType.COMPLETED, true);
        historyRepository.save(historyItem);
        taskRepository.deleteById(id);
        log.info("Manually completed task id={} and moved to history", id);
    }

    private void handleTimerCompletion(Task completedTask) {
        log.info(
                "Timer expired for task id={}, name='{}'",
                completedTask.id(),
                completedTask.name());
        soundService.playCompletionSound();

        // Archive completion event
        HistoryItem historyItem =
                HistoryItem.fromTask(completedTask, HistoryEventType.COMPLETED, false);
        historyRepository.save(historyItem);

        // Update remaining seconds in repository
        taskRepository.updateRemainingSeconds(completedTask.id(), 0);
    }

    private void handleTimerTick(Task activeTask) {
        taskRepository.updateRemainingSeconds(activeTask.id(), activeTask.remainingSeconds());
    }

    public Task restoreTaskFromHistory(long historyId) {
        HistoryItem historyItem =
                historyRepository
                        .findById(historyId)
                        .orElseThrow(
                                () ->
                                        new ValidationException(
                                                "History item not found with ID " + historyId));

        Task restored = historyItem.toRestoredTask();
        Task saved = taskRepository.save(restored);
        historyRepository.deleteById(historyId);
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
