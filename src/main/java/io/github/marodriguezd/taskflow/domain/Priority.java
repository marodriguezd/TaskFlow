package io.github.marodriguezd.taskflow.domain;

import java.util.Locale;

/** Task priority levels with display metadata and ordering. */
public enum Priority {
    HIGH("High", 0, "#ff5e78", "#2a0a0a"),
    MEDIUM("Medium", 1, "#ffb340", "#2a1a00"),
    LOW("Low", 2, "#3ddc84", "#021a0c");

    private final String displayName;
    private final int order;
    private final String hexColor;
    private final String pillColor;

    Priority(String displayName, int order, String hexColor, String pillColor) {
        this.displayName = displayName;
        this.order = order;
        this.hexColor = hexColor;
        this.pillColor = pillColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getOrder() {
        return order;
    }

    public String getHexColor() {
        return hexColor;
    }

    public String getPillColor() {
        return pillColor;
    }

    public static Priority fromDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return MEDIUM;
        }
        String trimmed = name.trim();
        for (Priority p : values()) {
            if (p.displayName.equalsIgnoreCase(trimmed) || p.name().equalsIgnoreCase(trimmed)) {
                return p;
            }
        }
        // Legacy labels from pre-2.0 (Spanish) data files and databases
        return switch (trimmed.toLowerCase(Locale.ROOT)) {
            case "alta" -> HIGH;
            case "media" -> MEDIUM;
            case "baja" -> LOW;
            default -> MEDIUM;
        };
    }
}
