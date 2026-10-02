package io.github.marodriguezd.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimerServiceTest {

    private TimerService timerService;

    @AfterEach
    void tearDown() {
        if (timerService != null) {
            timerService.shutdown();
        }
    }

    private TimerService newTimer() {
        AtomicLong nanoTime = new AtomicLong();
        timerService = new TimerService(nanoTime::get);
        return timerService;
    }

    @Test
    @DisplayName("Starting a second task autopauses the first")
    void testSingleActiveTaskAutopause() {
        TimerService service = newTimer();
        Task first = Task.create("Task 1", Priority.HIGH, 1500).withId(1L);
        Task second = Task.create("Task 2", Priority.LOW, 600).withId(2L);
        List<String> events = new ArrayList<>();
        service.addRunningStateListener((id, running) -> events.add(id + ":" + running));

        service.start(first);
        service.start(second);

        assertThat(service.getRunningTaskId()).contains(2L);
        assertThat(events).containsSubsequence("1:true", "1:false", "2:true");
        service.pause();
        assertThat(service.getRunningTaskId()).isEmpty();
    }

    @Test
    @DisplayName("Timer uses absolute elapsed time and completes exactly at its deadline")
    void calculatesFromDeadlineAndCompletes() {
        TimerService service = newTimer();
        AtomicLong clock = new AtomicLong();
        service.shutdown();
        service = new TimerService(clock::get);
        timerService = service;
        Task task = Task.create("Short task", Priority.MEDIUM, 3).withId(10L);
        List<Task> ticks = new ArrayList<>();
        List<Task> completions = new ArrayList<>();
        service.addTickListener(ticks::add);
        service.addCompletionListener(completions::add);
        service.start(task);

        clock.set(TimeUnit.MILLISECONDS.toNanos(1_500));
        service.tickForTest();
        assertThat(service.getActiveTask()).get().extracting(Task::remainingSeconds).isEqualTo(2);

        clock.set(TimeUnit.SECONDS.toNanos(3));
        service.tickForTest();
        assertThat(service.getRunningTaskId()).isEmpty();
        assertThat(ticks).extracting(Task::remainingSeconds).containsExactly(2, 0);
        assertThat(completions).singleElement().extracting(Task::remainingSeconds).isEqualTo(0);
    }

    @Test
    @DisplayName("Queued callback from a prior timer generation cannot update a restarted timer")
    void staleGenerationDoesNotTickNewTimer() {
        AtomicLong clock = new AtomicLong();
        timerService = new TimerService(clock::get);
        TimerService service = timerService;
        Task first = Task.create("First", Priority.HIGH, 60).withId(1L);
        Task second = Task.create("Second", Priority.LOW, 60).withId(2L);
        List<Task> ticks = new ArrayList<>();
        service.addTickListener(ticks::add);
        service.start(first);
        long staleGeneration = service.captureGenerationForTest();
        clock.set(TimeUnit.SECONDS.toNanos(1));
        service.tickForTest();
        Task latestFirst = service.getActiveTask().orElseThrow();
        service.start(second);
        service.dispatchTickForTest(staleGeneration);

        assertThat(ticks).containsExactly(latestFirst);
        assertThat(service.getRunningTaskId()).contains(2L);
        assertThat(service.getActiveTask()).get().extracting(Task::remainingSeconds).isEqualTo(60);
    }

    @Test
    @DisplayName("Pausing before deadline emits the accurate remaining-time snapshot")
    void pausePersistsElapsedTimeSnapshot() {
        AtomicLong clock = new AtomicLong();
        timerService = new TimerService(clock::get);
        List<Task> ticks = new ArrayList<>();
        timerService.addTickListener(ticks::add);
        timerService.start(Task.create("Pause", Priority.LOW, 10).withId(21L));
        clock.set(TimeUnit.SECONDS.toNanos(2));
        timerService.pause();

        assertThat(ticks).singleElement().extracting(Task::remainingSeconds).isEqualTo(8);
        assertThat(timerService.isLatestSnapshot(ticks.getFirst())).isTrue();
    }

    @Test
    @DisplayName("Toggle pauses and resumes a task")
    void testToggle() {
        TimerService service = newTimer();
        Task task = Task.create("Toggle task", Priority.MEDIUM, 600).withId(10L);
        service.toggle(task);
        assertThat(service.isRunning(10L)).isTrue();
        service.toggle(task);
        assertThat(service.isRunning(10L)).isFalse();
    }
}
