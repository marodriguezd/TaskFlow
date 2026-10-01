package io.github.marodriguezd.taskflow.domain;

/** User preferences settings record. */
public record UserPreferences(ThemeMode theme, boolean alwaysOnTop, boolean soundEnabled) {
    public UserPreferences {
        if (theme == null) {
            theme = ThemeMode.DARK;
        }
    }

    public static UserPreferences defaults(boolean defaultAlwaysOnTop) {
        return new UserPreferences(ThemeMode.DARK, defaultAlwaysOnTop, true);
    }

    public UserPreferences withTheme(ThemeMode newTheme) {
        return new UserPreferences(newTheme, alwaysOnTop, soundEnabled);
    }

    public UserPreferences withAlwaysOnTop(boolean newAlwaysOnTop) {
        return new UserPreferences(theme, newAlwaysOnTop, soundEnabled);
    }

    public UserPreferences withSoundEnabled(boolean newSoundEnabled) {
        return new UserPreferences(theme, alwaysOnTop, newSoundEnabled);
    }
}
