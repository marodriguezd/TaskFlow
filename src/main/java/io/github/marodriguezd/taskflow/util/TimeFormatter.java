package io.github.marodriguezd.taskflow.util;

/** Utility class for formatting duration in seconds to standard clock displays. */
public final class TimeFormatter {

    private TimeFormatter() {}

    /**
     * Formats seconds into MM:SS display string. E.g. 1500 -> "25:00", 65 -> "01:05", 0 -> "00:00".
     */
    public static String format(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        int minutes = safeSeconds / 60;
        int remainingSecs = safeSeconds % 60;

        StringBuilder sb = new StringBuilder(minutes >= 100 ? 7 : 5);
        if (minutes < 10) {
            sb.append('0');
        }
        sb.append(minutes).append(':');
        if (remainingSecs < 10) {
            sb.append('0');
        }
        sb.append(remainingSecs);
        return sb.toString();
    }

    /**
     * Converts a whole number of minutes into seconds, clamping non-positive input to one minute.
     *
     * @param minutes duration in minutes
     * @return the equivalent duration in seconds ({@code minutes * 60}, at least 60)
     */
    public static int parseMinutesToSeconds(int minutes) {
        return Math.max(1, minutes) * 60;
    }
}
