package io.github.marodriguezd.taskflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskTest {

    @Test
    @DisplayName("Task factory creates valid task with full remaining time")
    void testCreateTask() {
        Task task = Task.create("Write documentation", Priority.HIGH, 1500);

        assertThat(task.id()).isNull();
        assertThat(task.name()).isEqualTo("Write documentation");
        assertThat(task.priority()).isEqualTo(Priority.HIGH);
        assertThat(task.totalSeconds()).isEqualTo(1500);
        assertThat(task.remainingSeconds()).isEqualTo(1500);
        assertThat(task.getProgressFraction()).isEqualTo(1.0);
        assertThat(task.isExpired()).isFalse();
        assertThat(task.createdAt()).isNotNull();
        assertThat(task.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Task validates non-blank name and positive seconds")
    void testTaskValidation() {
        assertThatThrownBy(() -> Task.create("", Priority.HIGH, 1500))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        assertThatThrownBy(() -> Task.create("   ", Priority.HIGH, 1500))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Task.create("Task", Priority.HIGH, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");

        assertThatThrownBy(() -> Task.create("Task", Priority.HIGH, -60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Task clamps remaining seconds within 0 and totalSeconds")
    void testRemainingSecondsClamping() {
        Task task = Task.create("Clamped", Priority.LOW, 600);
        Task overMax = task.withRemainingSeconds(9999);
        assertThat(overMax.remainingSeconds()).isEqualTo(600);

        Task negative = task.withRemainingSeconds(-50);
        assertThat(negative.remainingSeconds()).isEqualTo(0);
        assertThat(negative.isExpired()).isTrue();
        assertThat(negative.getProgressFraction()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Task progress fraction calculates accurately")
    void testProgressFraction() {
        Task task = Task.create("Task", Priority.MEDIUM, 1000).withRemainingSeconds(250);
        assertThat(task.getProgressFraction()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("withDetails updates metadata and recalculates progress")
    void testWithDetails() {
        Task original = Task.create("Old", Priority.LOW, 600).withRemainingSeconds(300);
        Task modified = original.withDetails("New name", Priority.HIGH, 1200, 600);

        assertThat(modified.name()).isEqualTo("New name");
        assertThat(modified.priority()).isEqualTo(Priority.HIGH);
        assertThat(modified.totalSeconds()).isEqualTo(1200);
        assertThat(modified.remainingSeconds()).isEqualTo(600);
        assertThat(modified.getProgressFraction()).isEqualTo(0.5);
    }
}
