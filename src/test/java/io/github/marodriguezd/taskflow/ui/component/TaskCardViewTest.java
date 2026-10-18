package io.github.marodriguezd.taskflow.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.ui.JavaFxTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ticker behaviour: per-second updates must not churn styles that did not change. */
class TaskCardViewTest {

    @BeforeEach
    void checkDisplay() {
        Assumptions.assumeTrue(
                JavaFxTestSupport.isAvailable(),
                "JavaFX display not available in this environment");
    }

    private static Task task(Priority priority, int remaining) {
        return Task.create("Tick", priority, 600).withId(1L).withRemainingSeconds(remaining);
    }

    @Test
    @DisplayName("A tick with unchanged priority leaves the card's style classes untouched")
    void unchangedPriorityDoesNotRestyle() {
        TaskCardView card = new TaskCardView(task(Priority.HIGH, 600), true);

        card.updateTask(task(Priority.HIGH, 599));

        assertThat(card.getStyleClass()).contains("priority-high", "task-card");
        assertThat(card.getStyleClass()).doesNotContain("priority-medium", "priority-low");
    }

    @Test
    @DisplayName("Changing priority swaps the priority style class on every styled node")
    void priorityChangeUpdatesStyleClasses() {
        TaskCardView card = new TaskCardView(task(Priority.HIGH, 600), true);

        card.updateTask(task(Priority.LOW, 599));

        assertThat(card.getStyleClass()).contains("priority-low");
        assertThat(card.getStyleClass()).doesNotContain("priority-high");
    }

    @Test
    @DisplayName("Countdown ticks preserve the hover highlight")
    void tickKeepsHoverState() {
        TaskCardView card = new TaskCardView(task(Priority.MEDIUM, 600), true);
        card.getStyleClass().add("task-card-hover");

        card.updateTask(task(Priority.MEDIUM, 599));

        assertThat(card.getStyleClass()).contains("task-card-hover");
    }

    @Test
    @DisplayName("Expiry adds the expired style and revival removes it")
    void expiredStyleFollowsTaskState() {
        TaskCardView card = new TaskCardView(task(Priority.MEDIUM, 5), true);

        card.updateTask(task(Priority.MEDIUM, 0));
        assertThat(card.getStyleClass()).contains("task-card-expired");

        card.updateTask(task(Priority.MEDIUM, 5));
        assertThat(card.getStyleClass()).doesNotContain("task-card-expired");
    }
}
