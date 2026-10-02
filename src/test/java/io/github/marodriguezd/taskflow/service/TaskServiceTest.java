package io.github.marodriguezd.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.persistence.DatabaseManager;
import io.github.marodriguezd.taskflow.persistence.PersistenceException;
import io.github.marodriguezd.taskflow.persistence.SqliteHistoryRepository;
import io.github.marodriguezd.taskflow.persistence.SqliteTaskRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
        taskService.shutdown();
        timerService.shutdown();
        databaseManager.close();
    }

    @Test
    @DisplayName("Completed countdown persistence leaves task at zero after queued ticks")
    void completionPersistsZeroAfterEarlierTicks() throws Exception {
        Task task = taskService.createTask("Complete at zero", Priority.HIGH, 1);
        CountDownLatch persisted = new CountDownLatch(1);
        AtomicLong clock = new AtomicLong();
        TimerService completionTimer = new TimerService(clock::get);
        TaskService completionService =
                new TaskService(
                        new SqliteTaskRepository(databaseManager) {
                            @Override
                            public void archiveCompletion(Task completedTask, HistoryItem item) {
                                super.archiveCompletion(completedTask, item);
                                persisted.countDown();
                            }
                        },
                        historyRepository,
                        completionTimer,
                        soundService);
        completionTimer.start(task);
        clock.set(TimeUnit.SECONDS.toNanos(60));
        completionTimer.dispatchTickForTest(completionTimer.captureGenerationForTest());

        assertThat(persisted.await(2, TimeUnit.SECONDS)).isTrue();
        completionTimer.shutdown();
        completionService.shutdown();
        taskService.shutdown();
        assertThat(taskRepository.findById(task.id())).isPresent();
        assertThat(taskRepository.findById(task.id()).orElseThrow().remainingSeconds()).isZero();
        assertThat(historyRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Timer persistence runs on its own worker and saves the latest remaining time")
    void timerPersistenceRunsOffCallerThread() throws Exception {
        Task task = taskService.createTask("Background write", Priority.MEDIUM, 10);
        CountDownLatch persisted = new CountDownLatch(1);
        AtomicReference<String> persistenceThread = new AtomicReference<>();
        TimerService observer = new TimerService();
        TaskService observingService =
                new TaskService(
                        new SqliteTaskRepository(databaseManager) {
                            @Override
                            public void updateRemainingSeconds(long id, int remainingSeconds) {
                                persistenceThread.set(Thread.currentThread().getName());
                                super.updateRemainingSeconds(id, remainingSeconds);
                                persisted.countDown();
                            }
                        },
                        historyRepository,
                        observer,
                        soundService);
        try {
            observer.start(task.withRemainingSeconds(9));
            observer.tickForTest();
            assertThat(persisted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(persistenceThread.get()).startsWith("taskflow-timer-persistence");
        } finally {
            observingService.shutdown();
            observer.shutdown();
        }
    }

    @Test
    @DisplayName("Failed history archival leaves the task intact")
    void failedArchiveRollsBackTaskDeletion() throws Exception {
        Task task = taskService.createTask("Atomic delete", Priority.HIGH, 10);
        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TRIGGER fail_history_insert BEFORE INSERT ON history "
                            + "BEGIN SELECT RAISE(ABORT, 'simulated history failure'); END");
        }

        assertThatThrownBy(() -> taskService.completeTaskManually(task.id()))
                .isInstanceOf(PersistenceException.class);
        assertThat(taskRepository.findById(task.id())).isPresent();
        assertThat(historyRepository.findAll()).isEmpty();
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
    @DisplayName("Task edits drain a queued pause snapshot before reading persisted time")
    void updateDrainsPauseSnapshotBeforeReadingTask() throws Exception {
        taskService.shutdown();
        timerService.shutdown();

        AtomicLong clock = new AtomicLong();
        timerService = new TimerService(clock::get);
        CountDownLatch workerBlocked = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
        persistenceExecutor.execute(
                () -> {
                    workerBlocked.countDown();
                    try {
                        releaseWorker.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
        ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
        try {
            assertThat(workerBlocked.await(2, TimeUnit.SECONDS)).isTrue();
            taskService =
                    new TaskService(
                            taskRepository,
                            historyRepository,
                            timerService,
                            soundService,
                            persistenceExecutor);
            CountDownLatch pauseSnapshotQueued = new CountDownLatch(1);
            timerService.addTickListener(task -> pauseSnapshotQueued.countDown());
            Task task = taskService.createTask("Edited while timer runs", Priority.MEDIUM, 10);
            timerService.start(task);
            clock.set(TimeUnit.SECONDS.toNanos(2));

            CompletableFuture<Task> update =
                    CompletableFuture.supplyAsync(
                            () -> taskService.updateTask(task.id(), "Edited", Priority.HIGH, 20),
                            updateExecutor);
            assertThat(pauseSnapshotQueued.await(2, TimeUnit.SECONDS)).isTrue();
            releaseWorker.countDown();

            assertThat(update.get(2, TimeUnit.SECONDS).remainingSeconds()).isEqualTo(1196);
        } finally {
            releaseWorker.countDown();
            updateExecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Updates task and scales remaining time proportionally")
    void testUpdateTaskProportionalTime() {
        Task original = taskService.createTask("Original", Priority.MEDIUM, 10);
        taskRepository.updateRemainingSeconds(original.id(), 300);
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
        assertThat(history.getFirst().name()).isEqualTo("To be deleted");
        assertThat(history.getFirst().eventType()).isEqualTo(HistoryEventType.DELETED);
    }

    @Test
    @DisplayName("Completing task manually moves it to history as completed manual")
    void testCompleteTaskManually() {
        Task task = taskService.createTask("Manual Done", Priority.MEDIUM, 25);
        taskService.completeTaskManually(task.id());

        assertThat(taskService.getAllTasks()).isEmpty();
        List<HistoryItem> history = taskService.getHistory();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().name()).isEqualTo("Manual Done");
        assertThat(history.getFirst().eventType()).isEqualTo(HistoryEventType.COMPLETED);
        assertThat(history.getFirst().completedManually()).isTrue();
    }

    @Test
    @DisplayName("Restoring task from history moves it back to active tasks")
    void testRestoreTaskFromHistory() {
        Task task = taskService.createTask("To restore", Priority.LOW, 15);
        taskService.deleteTask(task.id(), true);
        List<HistoryItem> history = taskService.getHistory();
        assertThat(history).hasSize(1);

        Task restored = taskService.restoreTaskFromHistory(history.getFirst().id());
        assertThat(restored.name()).isEqualTo("To restore");
        assertThat(restored.priority()).isEqualTo(Priority.LOW);
        assertThat(taskService.getAllTasks()).hasSize(1);
        assertThat(taskService.getHistory()).isEmpty();
    }
}
