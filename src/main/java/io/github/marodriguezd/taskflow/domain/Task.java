package io.github.marodriguezd.taskflow.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain model representing a task in TaskFlow. Utilizes Java 21 record features and
 * Math.clamp.
 */
public record Task(
        Long id,
        String name,
        Priority priority,
        int totalSeconds,
        int remainingSeconds,
        Instant createdAt,
        Instant updatedAt) {
    public Task {
        Objects.requireNonNull(name, "Task name must not be null");
        Objects.requireNonNull(priority, "Priority must not be null");
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Task name must not be blank");
        }
        name = trimmedName;

        if (totalSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Total seconds must be greater than zero: " + totalSeconds);
        }
        remainingSeconds = Math.clamp(remainingSeconds, 0, totalSeconds);

        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public static Task create(String name, Priority priority, int totalSeconds) {
        Instant now = Instant.now();
        return new Task(null, name, priority, totalSeconds, totalSeconds, now, now);
    }

    public Task withId(Long newId) {
        return new Task(
                newId, name, priority, totalSeconds, remainingSeconds, createdAt, updatedAt);
    }

    public Task withRemainingSeconds(int newRemaining) {
        return new Task(id, name, priority, totalSeconds, newRemaining, createdAt, Instant.now());
    }

    public Task withDetails(
            String newName, Priority newPriority, int newTotalSeconds, int newRemainingSeconds) {
        return new Task(
                id,
                newName,
                newPriority,
                newTotalSeconds,
                newRemainingSeconds,
                createdAt,
                Instant.now());
    }

    public double getProgressFraction() {
        if (totalSeconds <= 0) {
            return 0.0;
        }
        return (double) remainingSeconds / (double) totalSeconds;
    }

    public boolean isExpired() {
        return remainingSeconds <= 0;
    }
}
