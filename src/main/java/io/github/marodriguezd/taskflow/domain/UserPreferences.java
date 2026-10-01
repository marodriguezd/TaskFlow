package io.github.marodriguezd.taskflow.domain;

/**
 * User preferences settings record.
 *
 * <p>{@code language} is the persisted UI language as a locale-independent BCP 47 tag ({@code en},
 * {@code es}, {@code de}, {@code it}, {@code zh-Hans}). Blank means "never chosen" and triggers
 * first-run OS detection in the composition root — the domain never interprets it. It must stay a
 * plain {@link String} (not an enum) precisely so "unset" remains distinguishable from "en".
 */
public record UserPreferences(
        ThemeMode theme, boolean alwaysOnTop, boolean soundEnabled, String language) {
    public UserPreferences {
        if (theme == null) {
            theme = ThemeMode.DARK;
        }
        if (language == null) {
            language = "";
        }
    }

    public static UserPreferences defaults(boolean defaultAlwaysOnTop) {
        return new UserPreferences(ThemeMode.DARK, defaultAlwaysOnTop, true, "");
    }

    public UserPreferences withTheme(ThemeMode newTheme) {
        return new UserPreferences(newTheme, alwaysOnTop, soundEnabled, language);
    }

    public UserPreferences withAlwaysOnTop(boolean newAlwaysOnTop) {
        return new UserPreferences(theme, newAlwaysOnTop, soundEnabled, language);
    }

    public UserPreferences withSoundEnabled(boolean newSoundEnabled) {
        return new UserPreferences(theme, alwaysOnTop, newSoundEnabled, language);
    }

    public UserPreferences withLanguage(String newLanguage) {
        return new UserPreferences(theme, alwaysOnTop, soundEnabled, newLanguage);
    }
}
