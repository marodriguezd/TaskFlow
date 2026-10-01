package io.github.marodriguezd.taskflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HistoryItemTest {

    @Test
    @DisplayName("Creates HistoryItem from active task correctly")
    void testFromTask() {
        Task task = Task.create("Refactor modules", Priority.HIGH, 1800).withRemainingSeconds(0);
        HistoryItem item = HistoryItem.fromTask(task, HistoryEventType.COMPLETED, false);

        assertThat(item.name()).isEqualTo("Refactor modules");
        assertThat(item.priority()).isEqualTo(Priority.HIGH);
        assertThat(item.totalSeconds()).isEqualTo(1800);
        assertThat(item.remainingSeconds()).isEqualTo(0);
        assertThat(item.eventType()).isEqualTo(HistoryEventType.COMPLETED);
        assertThat(item.completedManually()).isFalse();
        assertThat(item.getModeDescription()).isEqualTo("Completed by timer");
    }

    @Test
    @DisplayName("Mode descriptions reflect completion and deletion states")
    void testModeDescriptions() {
        HistoryItem manual =
                new HistoryItem(
                        1L,
                        "T1",
                        Priority.MEDIUM,
                        600,
                        300,
                        HistoryEventType.COMPLETED,
                        true,
                        Instant.now());
        assertThat(manual.getModeDescription()).isEqualTo("Completed manually");

        HistoryItem timer =
                new HistoryItem(
                        2L,
                        "T2",
                        Priority.LOW,
                        600,
                        0,
                        HistoryEventType.COMPLETED,
                        false,
                        Instant.now());
        assertThat(timer.getModeDescription()).isEqualTo("Completed by timer");

        HistoryItem deleted =
                new HistoryItem(
                        3L,
                        "T3",
                        Priority.HIGH,
                        600,
                        200,
                        HistoryEventType.DELETED,
                        false,
                        Instant.now());
        assertThat(deleted.getModeDescription()).isEqualTo("Deleted");
    }

    @Test
    @DisplayName("toRestoredTask restores task with original or full time")
    void testToRestoredTask() {
        HistoryItem item =
                new HistoryItem(
                        1L,
                        "Restored Task",
                        Priority.HIGH,
                        1200,
                        450,
                        HistoryEventType.DELETED,
                        false,
                        Instant.now());
        Task restored = item.toRestoredTask();

        assertThat(restored.name()).isEqualTo("Restored Task");
        assertThat(restored.priority()).isEqualTo(Priority.HIGH);
        assertThat(restored.totalSeconds()).isEqualTo(1200);
        assertThat(restored.remainingSeconds()).isEqualTo(450);

        // If remaining was 0, it resets to totalSeconds so the restored task can be worked on again
        HistoryItem completedZero =
                new HistoryItem(
                        2L,
                        "Done Task",
                        Priority.LOW,
                        1500,
                        0,
                        HistoryEventType.COMPLETED,
                        false,
                        Instant.now());
        Task restoredZero = completedZero.toRestoredTask();
        assertThat(restoredZero.remainingSeconds()).isEqualTo(1500);
    }
}
