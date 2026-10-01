package io.github.marodriguezd.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimerServiceTest {

    @Test
    @DisplayName("Single task playback: starting a second task autopauses the first")
    void testSingleActiveTaskAutopause() {
        TimerService timerService = new TimerService();

        Task task1 = Task.create("Task 1", Priority.HIGH, 1500).withId(1L);
        Task task2 = Task.create("Task 2", Priority.LOW, 600).withId(2L);

        AtomicReference<Long> lastStartedId = new AtomicReference<>();
        AtomicReference<Long> lastPausedId = new AtomicReference<>();

        timerService.addRunningStateListener(
                (id, running) -> {
                    if (running) {
                        lastStartedId.set(id);
                    } else {
                        lastPausedId.set(id);
                    }
                });

        // Start task 1
        timerService.start(task1);
        assertThat(timerService.isRunning(1L)).isTrue();
        assertThat(timerService.getRunningTaskId()).contains(1L);
        assertThat(lastStartedId.get()).isEqualTo(1L);

        // Start task 2 -> task 1 must be autopaused
        timerService.start(task2);
        assertThat(timerService.isRunning(1L)).isFalse();
        assertThat(timerService.isRunning(2L)).isTrue();
        assertThat(timerService.getRunningTaskId()).contains(2L);
        assertThat(lastPausedId.get()).isEqualTo(1L);
        assertThat(lastStartedId.get()).isEqualTo(2L);

        // Pause
        timerService.pause();
        assertThat(timerService.isRunning(2L)).isFalse();
        assertThat(timerService.getRunningTaskId()).isEmpty();
    }

    @Test
    @DisplayName("Toggle alternates running state")
    void testToggle() {
        TimerService timerService = new TimerService();
        Task task = Task.create("Toggle task", Priority.MEDIUM, 600).withId(10L);

        assertThat(timerService.isRunning(10L)).isFalse();

        timerService.toggle(task);
        assertThat(timerService.isRunning(10L)).isTrue();

        timerService.toggle(task);
        assertThat(timerService.isRunning(10L)).isFalse();
    }

    @Test
    @DisplayName("Stop if running stops the target task only if it is active")
    void testStopIfRunning() {
        TimerService timerService = new TimerService();
        Task task = Task.create("Task", Priority.HIGH, 600).withId(5L);

        timerService.start(task);
        assertThat(timerService.isRunning(5L)).isTrue();

        timerService.stopIfRunning(99L);
        assertThat(timerService.isRunning(5L)).isTrue();

        timerService.stopIfRunning(5L);
        assertThat(timerService.isRunning(5L)).isFalse();
    }
}
