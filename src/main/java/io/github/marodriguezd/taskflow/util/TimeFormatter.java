package io.github.marodriguezd.taskflow.util;

/** Utility class for formatting duration in seconds to standard clock displays. */
public final class TimeFormatter {

    private TimeFormatter() {
        // Prevent instantiation
    }

    /**
     * Formats seconds into MM:SS display string. E.g. 1500 -> "25:00", 65 -> "01:05", 0 -> "00:00".
     */
    public static String format(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        int minutes = safeSeconds / 60;
        int remainingSecs = safeSeconds % 60;
        return String.format("%02d:%02d", minutes, remainingSecs);
    }

    /** Parses MM:SS or integer minute strings into seconds. */
    public static int parseMinutesToSeconds(int minutes) {
        return Math.max(1, minutes) * 60;
    }
}
