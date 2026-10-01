package io.github.marodriguezd.taskflow.domain;

import io.github.marodriguezd.taskflow.util.DateTimeUtil;
import java.time.Instant;
import java.util.Objects;

/** Immutable record representing an archived task in the completion/deletion history. */
public record HistoryItem(
        Long id,
        String name,
        Priority priority,
        int totalSeconds,
        int remainingSeconds,
        HistoryEventType eventType,
        boolean completedManually,
        Instant eventAt) {
    public HistoryItem {
        Objects.requireNonNull(name, "History item name must not be null");
        Objects.requireNonNull(priority, "Priority must not be null");
        Objects.requireNonNull(eventType, "EventType must not be null");
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("History item name must not be blank");
        }
        name = trimmedName;

        totalSeconds = Math.max(60, totalSeconds);
        remainingSeconds = Math.clamp(remainingSeconds, 0, totalSeconds);
        if (eventAt == null) {
            eventAt = Instant.now();
        }
    }

    public static HistoryItem fromTask(
            Task task, HistoryEventType eventType, boolean completedManually) {
        return new HistoryItem(
                null,
                task.name(),
                task.priority(),
                task.totalSeconds(),
                task.remainingSeconds(),
                eventType,
                completedManually,
                Instant.now());
    }

    public HistoryItem withId(Long newId) {
        return new HistoryItem(
                newId,
                name,
                priority,
                totalSeconds,
                remainingSeconds,
                eventType,
                completedManually,
                eventAt);
    }

    public String getFormattedDate() {
        return DateTimeUtil.format(eventAt);
    }

    public String getModeDescription() {
        if (eventType == HistoryEventType.DELETED) {
            return "Eliminada";
        }
        if (completedManually) {
            return "Completada manual";
        }
        return "Completada por temporizador";
    }

    /** Converts this history item back into an active Task for restoration. */
    public Task toRestoredTask() {
        int restoredRemaining = remainingSeconds <= 0 ? totalSeconds : remainingSeconds;
        return Task.create(name, priority, totalSeconds).withRemainingSeconds(restoredRemaining);
    }
}
