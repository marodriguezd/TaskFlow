package io.github.marodriguezd.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.persistence.DatabaseManager;
import io.github.marodriguezd.taskflow.persistence.SqliteHistoryRepository;
import io.github.marodriguezd.taskflow.persistence.SqliteTaskRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskServiceTest {

    private DatabaseManager databaseManager;
    private SqliteTaskRepository taskRepository;
    private SqliteHistoryRepository historyRepository;
    private TimerService timerService;
    private SoundService soundService;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        databaseManager = DatabaseManager.inMemory();
        taskRepository = new SqliteTaskRepository(databaseManager);
        historyRepository = new SqliteHistoryRepository(databaseManager);
        PlatformService platformService = new PlatformService();
        timerService = new TimerService();
        soundService = new SoundService(platformService);

        taskService =
                new TaskService(taskRepository, historyRepository, timerService, soundService);
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    @DisplayName("Creates valid task and enforces name/minute validation")
    void testCreateTaskValidation() {
        Task created = taskService.createTask("Test task", Priority.HIGH, 25);
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Test task");
        assertThat(created.totalSeconds()).isEqualTo(1500);

        assertThatThrownBy(() -> taskService.createTask("", Priority.HIGH, 25))
                .isInstanceOf(ValidationException.class);

        assertThatThrownBy(() -> taskService.createTask("Task", Priority.HIGH, 0))
                .isInstanceOf(ValidationException.class);

        assertThatThrownBy(() -> taskService.createTask("Task", Priority.HIGH, 1000))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Updates task and scales remaining time proportionally")
    void testUpdateTaskProportionalTime() {
        // Original: 10 minutes (600s), 300s remaining (50% progress)
        Task original = taskService.createTask("Original", Priority.MEDIUM, 10);
        taskRepository.updateRemainingSeconds(original.id(), 300);

        // Update to 20 minutes (1200s) -> remaining should be 50% = 600s
        Task updated = taskService.updateTask(original.id(), "Updated", Priority.HIGH, 20);
        assertThat(updated.name()).isEqualTo("Updated");
        assertThat(updated.priority()).isEqualTo(Priority.HIGH);
        assertThat(updated.totalSeconds()).isEqualTo(1200);
        assertThat(updated.remainingSeconds()).isEqualTo(600);
    }

    @Test
    @DisplayName("Editing an expired task (00:00) resets time to full duration")
    void testUpdateExpiredTaskResetsTime() {
        Task original = taskService.createTask("Expired Task", Priority.LOW, 10);
        taskRepository.updateRemainingSeconds(original.id(), 0);

        Task updated = taskService.updateTask(original.id(), "Expired Task", Priority.LOW, 15);
        assertThat(updated.totalSeconds()).isEqualTo(900);
        assertThat(updated.remainingSeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("Deleting active unexpired task archives it to history with DELETED event")
    void testDeleteTaskArchivesToHistory() {
        Task task = taskService.createTask("To be deleted", Priority.HIGH, 10);
        taskService.deleteTask(task.id(), true);

        assertThat(taskService.getAllTasks()).isEmpty();
        List<HistoryItem> history = taskService.getHistory();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).name()).isEqualTo("To be deleted");
        assertThat(history.get(0).eventType()).isEqualTo(HistoryEventType.DELETED);
    }

    @Test
    @DisplayName("Completing task manually moves it to history as completed manual")
    void testCompleteTaskManually() {
        Task task = taskService.createTask("Manual Done", Priority.MEDIUM, 25);
        taskService.completeTaskManually(task.id());

        assertThat(taskService.getAllTasks()).isEmpty();
        List<HistoryItem> history = taskService.getHistory();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).name()).isEqualTo("Manual Done");
        assertThat(history.get(0).eventType()).isEqualTo(HistoryEventType.COMPLETED);
        assertThat(history.get(0).completedManually()).isTrue();
    }

    @Test
    @DisplayName(
            "Restoring task from history brings it back to active list and removes from history")
    void testRestoreTaskFromHistory() {
        Task task = taskService.createTask("To restore", Priority.LOW, 15);
        taskService.deleteTask(task.id(), true);

        List<HistoryItem> history = taskService.getHistory();
        assertThat(history).hasSize(1);

        Task restored = taskService.restoreTaskFromHistory(history.get(0).id());
        assertThat(restored.name()).isEqualTo("To restore");
        assertThat(restored.priority()).isEqualTo(Priority.LOW);

        assertThat(taskService.getAllTasks()).hasSize(1);
        assertThat(taskService.getHistory()).isEmpty();
    }
}
