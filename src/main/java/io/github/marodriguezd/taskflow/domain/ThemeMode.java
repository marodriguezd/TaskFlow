package io.github.marodriguezd.taskflow.domain;

/** Supported UI visual themes. */
public enum ThemeMode {
    DARK("dark", "🌙", "Dark theme"),
    LIGHT("light", "☀", "Light theme");

    private final String code;
    private final String icon;
    private final String description;

    ThemeMode(String code, String icon, String description) {
        this.code = code;
        this.icon = icon;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public ThemeMode toggle() {
        return this == DARK ? LIGHT : DARK;
    }

    public static ThemeMode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return DARK;
        }
        String trimmed = code.trim();
        for (ThemeMode mode : values()) {
            if (mode.code.equalsIgnoreCase(trimmed) || mode.name().equalsIgnoreCase(trimmed)) {
                return mode;
            }
        }
        return DARK;
    }
}
