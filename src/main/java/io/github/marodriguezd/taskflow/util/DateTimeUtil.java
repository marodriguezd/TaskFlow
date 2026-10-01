package io.github.marodriguezd.taskflow.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Utility for formatting and parsing dates across the application. */
public final class DateTimeUtil {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm  —  dd/MM/yy");

    private DateTimeUtil() {
        // Prevent instantiation
    }

    /**
     * Formats an Instant using the application's standard display format (matching legacy
     * TaskFlow).
     */
    public static String format(Instant instant) {
        if (instant == null) {
            return "Desconocido";
        }
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return DISPLAY_FORMATTER.format(localDateTime);
    }

    /** Formats the current time using the standard display format. */
    public static String nowFormatted() {
        return format(Instant.now());
    }

    /** Attempts to parse a legacy string representation or returns now if unparseable. */
    public static Instant parseLegacyOrDefault(String dateStr, Instant defaultInstant) {
        if (dateStr == null || dateStr.isBlank()) {
            return defaultInstant;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(dateStr.trim(), DISPLAY_FORMATTER);
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            return defaultInstant;
        }
    }
}
