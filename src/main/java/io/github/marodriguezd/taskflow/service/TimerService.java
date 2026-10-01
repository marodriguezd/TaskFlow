package io.github.marodriguezd.taskflow.service;

import io.github.marodriguezd.taskflow.domain.Task;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates task countdown timers, ensuring strict single-task active playback (autopause). Uses
 * standard ScheduledExecutorService for cross-platform reliability in both UI and headless
 * environments.
 */
public class TimerService {

    private static final Logger log = LoggerFactory.getLogger(TimerService.class);

    private final List<Consumer<Task>> tickListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Task>> completionListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<Long, Boolean>> runningStateListeners =
            new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread thread = new Thread(r, "taskflow-timer");
                        thread.setDaemon(true);
                        return thread;
                    });

    private ScheduledFuture<?> timerFuture;
    private Task activeTask;

    public TimerService() {
        // Scheduler is ready immediately
    }

    public synchronized void start(Task task) {
        if (task == null || task.isExpired()) {
            return;
        }

        Long previousTaskId = activeTask != null ? activeTask.id() : null;
        if (previousTaskId != null && !previousTaskId.equals(task.id())) {
            log.info("Autopausing previous task {} to start task {}", previousTaskId, task.id());
            notifyRunningState(previousTaskId, false);
        }

        this.activeTask = task;
        notifyRunningState(task.id(), true);

        stopScheduledTimer();
        timerFuture = scheduler.scheduleAtFixedRate(this::dispatchTick, 1, 1, TimeUnit.SECONDS);
        log.debug("Started timer for task id={}", task.id());
    }

    public synchronized void pause() {
        if (activeTask != null) {
            Long taskId = activeTask.id();
            stopScheduledTimer();
            activeTask = null;
            notifyRunningState(taskId, false);
            log.debug("Paused timer for task id={}", taskId);
        }
    }

    public synchronized void toggle(Task task) {
        if (task == null) {
            return;
        }
        if (isRunning(task.id())) {
            pause();
        } else {
            start(task);
        }
    }

    public synchronized void stopIfRunning(long taskId) {
        if (isRunning(taskId)) {
            pause();
        }
    }

    public synchronized boolean isRunning(Long taskId) {
        return activeTask != null && activeTask.id() != null && activeTask.id().equals(taskId);
    }

    public synchronized Optional<Long> getRunningTaskId() {
        return Optional.ofNullable(activeTask != null ? activeTask.id() : null);
    }

    public synchronized void updateActiveTask(Task updatedTask) {
        if (activeTask != null
                && activeTask.id() != null
                && activeTask.id().equals(updatedTask.id())) {
            this.activeTask = updatedTask;
        }
    }

    private void stopScheduledTimer() {
        if (timerFuture != null && !timerFuture.isDone()) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
    }

    private void dispatchTick() {
        try {
            if (Platform.isFxApplicationThread()) {
                handleTick();
            } else {
                try {
                    Platform.runLater(this::handleTick);
                } catch (IllegalStateException e) {
                    // Headless / non-JavaFX runtime (e.g. unit tests)
                    handleTick();
                }
            }
        } catch (Exception e) {
            log.error("Error dispatching timer tick", e);
        }
    }

    private synchronized void handleTick() {
        if (activeTask == null) {
            stopScheduledTimer();
            return;
        }

        int remaining = Math.max(0, activeTask.remainingSeconds() - 1);
        activeTask = activeTask.withRemainingSeconds(remaining);

        for (Consumer<Task> listener : tickListeners) {
            try {
                listener.accept(activeTask);
            } catch (Exception e) {
                log.error("Error in tick listener", e);
            }
        }

        if (activeTask.isExpired()) {
            Task completed = activeTask;
            pause();
            for (Consumer<Task> listener : completionListeners) {
                try {
                    listener.accept(completed);
                } catch (Exception e) {
                    log.error("Error in completion listener", e);
                }
            }
        }
    }

    public void addTickListener(Consumer<Task> listener) {
        tickListeners.add(listener);
    }

    public void addCompletionListener(Consumer<Task> listener) {
        completionListeners.add(listener);
    }

    public void addRunningStateListener(BiConsumer<Long, Boolean> listener) {
        runningStateListeners.add(listener);
    }

    private void notifyRunningState(Long taskId, boolean running) {
        for (BiConsumer<Long, Boolean> listener : runningStateListeners) {
            try {
                listener.accept(taskId, running);
            } catch (Exception e) {
                log.error("Error in running state listener", e);
            }
        }
    }

    public void shutdown() {
        stopScheduledTimer();
        scheduler.shutdownNow();
    }
}
