package io.github.marodriguezd.taskflow.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** Utility for formatting and parsing dates across the application. */
public final class DateTimeUtil {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm  —  dd/MM/yy");

    private DateTimeUtil() {}

    /**
     * Formats an Instant using the application's standard display format (matching legacy
     * TaskFlow).
     */
    public static String format(Instant instant) {
        if (instant == null) {
            return "—";
        }
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return DISPLAY_FORMATTER.format(localDateTime);
    }

    /** Formats the current time using the standard display format. */
    public static String nowFormatted() {
        return format(Instant.now());
    }

    /**
     * Parses a legacy wall-clock value without attaching a time zone. This is the canonical,
     * time-zone-independent source representation used for legacy identity. Returns empty for
     * missing, blank, or unparsable values.
     */
    public static Optional<LocalDateTime> parseLegacyLocal(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(dateStr.trim(), DISPLAY_FORMATTER));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Attempts to parse a legacy timestamp with the JVM default time zone, returning empty for
     * missing, blank, or unparseable values. Callers that need a value can fall back to {@link
     * #parseLegacyOrDefault}.
     */
    public static Optional<Instant> parseLegacy(String dateStr) {
        return parseLegacy(dateStr, ZoneId.systemDefault());
    }

    /** Parses a legacy timestamp using an explicit time zone (used for deterministic imports). */
    public static Optional<Instant> parseLegacy(String dateStr, ZoneId zone) {
        return parseLegacyLocal(dateStr).map(local -> local.atZone(zone).toInstant());
    }

    /** Attempts to parse a legacy string representation or returns the supplied default. */
    public static Instant parseLegacyOrDefault(String dateStr, Instant defaultInstant) {
        return parseLegacy(dateStr).orElse(defaultInstant);
    }

    /**
     * Resolves the legacy timestamp fields with the documented precedence: a parsable {@code
     * event_at} wins, then a parsable {@code completed_at}, and only then the supplied fallback
     * instant. The fallback is stored for display but carries no local value, so callers must not
     * treat it as a reliable identity.
     */
    public static LegacyTimestamp parseLegacyTimestamp(
            String eventAt, String completedAt, ZoneId zone, Instant fallback) {
        Optional<LocalDateTime> eventLocal = parseLegacyLocal(eventAt);
        if (eventLocal.isPresent()) {
            LocalDateTime local = eventLocal.get();
            return new LegacyTimestamp(
                    local, local.atZone(zone).toInstant(), LegacyTimestamp.Source.EVENT_AT);
        }
        Optional<LocalDateTime> completedLocal = parseLegacyLocal(completedAt);
        if (completedLocal.isPresent()) {
            LocalDateTime local = completedLocal.get();
            return new LegacyTimestamp(
                    local, local.atZone(zone).toInstant(), LegacyTimestamp.Source.COMPLETED_AT);
        }
        return LegacyTimestamp.fallback(fallback);
    }
}
