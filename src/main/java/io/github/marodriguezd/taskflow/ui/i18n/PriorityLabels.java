package io.github.marodriguezd.taskflow.ui.i18n;

import io.github.marodriguezd.taskflow.domain.Priority;
import java.util.Locale;

/**
 * Localized presentation of {@link Priority} for UI rendering only (task-card pill, dialog combo).
 *
 * <p>Domain and persistence are untouched by this class: {@link Priority#getDisplayName()} stays
 * English as the legacy-parse source, the database stores enum names ({@code HIGH}/{@code MEDIUM}/
 * {@code LOW}), and {@link Priority#fromDisplayName} keeps accepting English and legacy Spanish
 * labels for existing data. Only ever feed it display text produced by {@link #label}.
 */
public final class PriorityLabels {

    private PriorityLabels() {
        // Prevent instantiation
    }

    /** Returns the i18n key for a priority, e.g. {@code priority.high}. */
    public static String key(Priority priority) {
        return "priority." + priority.name().toLowerCase(Locale.ROOT);
    }

    /** Returns the localized display label, e.g. "High" / "Hoch" / "高". Never null. */
    public static String label(Priority priority) {
        if (priority == null) {
            return "";
        }
        return Messages.get(key(priority));
    }

    /**
     * Reverse-maps a displayed (possibly localized) label back to a Priority. Falls back to {@link
     * Priority#fromDisplayName} so English labels, enum names, and legacy Spanish data labels keep
     * resolving exactly as before.
     */
    public static Priority parse(String displayedLabel) {
        if (displayedLabel == null || displayedLabel.isBlank()) {
            return Priority.MEDIUM;
        }
        String trimmed = displayedLabel.trim();
        for (Priority priority : Priority.values()) {
            if (label(priority).equalsIgnoreCase(trimmed)) {
                return priority;
            }
        }
        return Priority.fromDisplayName(trimmed);
    }
}
