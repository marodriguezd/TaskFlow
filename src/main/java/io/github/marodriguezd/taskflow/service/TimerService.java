package io.github.marodriguezd.taskflow.service;

import io.github.marodriguezd.taskflow.domain.Task;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates elapsed-time countdowns and enforces a single active task. */
public class TimerService {

    private static final Logger log = LoggerFactory.getLogger(TimerService.class);
    private static final long SECOND_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final List<Consumer<Task>> tickListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Task>> completionListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<Long, Boolean>> runningStateListeners =
            new CopyOnWriteArrayList<>();
    private final LongSupplier nanoTime;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread thread = new Thread(r, "taskflow-timer");
                        thread.setDaemon(true);
                        return thread;
                    });

    private ScheduledFuture<?> timerFuture;
    private Task activeTask;
    private final Map<Long, Task> latestSnapshots = new HashMap<>();
    private final Set<Long> completedTaskIds = new HashSet<>();
    private long deadlineNanos;
    private long generation;

    public TimerService() {
        this(System::nanoTime);
    }

    TimerService(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public void start(Task task) {
        List<Runnable> notifications = new ArrayList<>();
        synchronized (this) {
            if (task == null || task.isExpired()) {
                return;
            }
            if (activeTask != null) {
                pauseActiveLocked(true, notifications);
            }
            activeTask = task;
            rememberSnapshot(task);
            if (task.id() != null) {
                completedTaskIds.remove(task.id());
            }
            deadlineNanos = nanoTime.getAsLong() + task.remainingSeconds() * SECOND_NANOS;
            long timerGeneration = ++generation;
            notifications.add(() -> notifyRunningState(task.id(), true));
            timerFuture =
                    scheduler.scheduleAtFixedRate(
                            () -> dispatchTick(timerGeneration), 1, 1, TimeUnit.SECONDS);
        }
        // Listener callbacks are foreign code: dispatch them once the monitor is released so a
        // slow listener (or one that calls back into this service) can never block the timer or UI.
        dispatch(notifications);
        log.debug("Started timer for task id={}", task.id());
    }

    public void pause() {
        List<Runnable> notifications = new ArrayList<>();
        synchronized (this) {
            if (activeTask != null) {
                pauseActiveLocked(true, notifications);
            }
        }
        dispatch(notifications);
    }

    private void pauseActiveLocked(boolean applyElapsedTime, List<Runnable> notifications) {
        Task pausedTask = activeTask;
        if (applyElapsedTime) {
            updateRemainingFromDeadline(nanoTime.getAsLong());
        }
        stopScheduledTimer();
        generation++;
        Task stoppedTask = activeTask;
        rememberSnapshot(stoppedTask);
        if (applyElapsedTime && pausedTask.remainingSeconds() != stoppedTask.remainingSeconds()) {
            notifications.add(() -> notifyTick(stoppedTask));
        }
        activeTask = null;
        notifications.add(() -> notifyRunningState(pausedTask.id(), false));
        if (stoppedTask.isExpired()) {
            if (stoppedTask.id() != null) {
                completedTaskIds.add(stoppedTask.id());
            }
            notifications.add(() -> notifyCompletion(stoppedTask));
        }
        log.debug("Paused timer for task id={}", pausedTask.id());
    }

    public void toggle(Task task) {
        if (task == null) {
            return;
        }
        boolean running;
        synchronized (this) {
            running = isRunningLocked(task.id());
        }
        if (running) {
            pause();
        } else {
            start(task);
        }
    }

    public void stopIfRunning(long taskId) {
        boolean running;
        synchronized (this) {
            running = isRunningLocked(taskId);
        }
        if (running) {
            pause();
        }
    }

    public synchronized boolean isRunning(Long taskId) {
        return isRunningLocked(taskId);
    }

    private boolean isRunningLocked(Long taskId) {
        return activeTask != null && activeTask.id() != null && activeTask.id().equals(taskId);
    }

    public synchronized Optional<Long> getRunningTaskId() {
        return Optional.ofNullable(activeTask != null ? activeTask.id() : null);
    }

    public synchronized Optional<Task> getActiveTask() {
        return Optional.ofNullable(activeTask);
    }

    public synchronized boolean isLatestSnapshot(Task task) {
        return task.id() != null && task.equals(latestSnapshots.get(task.id()));
    }

    public synchronized boolean isRunningSnapshot(Task task) {
        return activeTask != null && activeTask.equals(task);
    }

    public synchronized boolean isCompletedTask(long taskId) {
        return completedTaskIds.contains(taskId);
    }

    public synchronized boolean isTaskCurrent(Task task) {
        return task.id() != null
                && (activeTask != null && activeTask.id().equals(task.id())
                        || latestSnapshots.containsKey(task.id()));
    }

    public synchronized void invalidateSnapshot(long taskId) {
        latestSnapshots.remove(taskId);
    }

    public synchronized void updateActiveTask(Task updatedTask) {
        if (activeTask != null
                && activeTask.id() != null
                && activeTask.id().equals(updatedTask.id())) {
            activeTask = updatedTask;
            rememberSnapshot(updatedTask);
            deadlineNanos = nanoTime.getAsLong() + updatedTask.remainingSeconds() * SECOND_NANOS;
        }
    }

    private void dispatchTick(long timerGeneration) {
        List<Runnable> notifications = new ArrayList<>();
        synchronized (this) {
            if (timerGeneration != generation || activeTask == null) {
                return;
            }
            Task previousTask = activeTask;
            updateRemainingFromDeadline(nanoTime.getAsLong());
            Task currentTask = activeTask;
            rememberSnapshot(currentTask);
            if (previousTask.remainingSeconds() != currentTask.remainingSeconds()) {
                notifications.add(() -> notifyTick(currentTask));
            }
            if (currentTask.isExpired() && timerGeneration == generation) {
                stopScheduledTimer();
                generation++;
                rememberSnapshot(currentTask);
                if (currentTask.id() != null) {
                    completedTaskIds.add(currentTask.id());
                }
                activeTask = null;
                notifications.add(() -> notifyRunningState(currentTask.id(), false));
                notifications.add(() -> notifyCompletion(currentTask));
            }
        }
        dispatch(notifications);
    }

    private static void dispatch(List<Runnable> notifications) {
        for (Runnable notification : notifications) {
            notification.run();
        }
    }

    private void updateRemainingFromDeadline(long nowNanos) {
        long nanosLeft = Math.max(0, deadlineNanos - nowNanos);
        int remaining =
                (int) Math.min(Integer.MAX_VALUE, (nanosLeft + SECOND_NANOS - 1) / SECOND_NANOS);
        activeTask = activeTask.withRemainingSeconds(remaining);
    }

    private void stopScheduledTimer() {
        if (timerFuture != null) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
    }

    private void rememberSnapshot(Task task) {
        if (task.id() != null) {
            latestSnapshots.put(task.id(), task);
        }
    }

    private void notifyCompletion(Task task) {
        for (Consumer<Task> listener : completionListeners) {
            try {
                listener.accept(task);
            } catch (Exception e) {
                log.error("Error in completion listener", e);
            }
        }
    }

    private void notifyTick(Task task) {
        for (Consumer<Task> listener : tickListeners) {
            try {
                listener.accept(task);
            } catch (Exception e) {
                log.error("Error in tick listener", e);
            }
        }
    }

    void tickForTest() {
        dispatchTick(captureGenerationForTest());
    }

    synchronized long captureGenerationForTest() {
        return generation;
    }

    void dispatchTickForTest(long timerGeneration) {
        dispatchTick(timerGeneration);
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

    public synchronized void shutdown() {
        stopScheduledTimer();
        generation++;
        activeTask = null;
        latestSnapshots.clear();
        completedTaskIds.clear();
        scheduler.shutdownNow();
    }
}
