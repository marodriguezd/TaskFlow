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
        ThemeMode theme,
        boolean alwaysOnTop,
        boolean soundEnabled,
        String language,
        double glassOpacity) {

    public static final double DEFAULT_GLASS_OPACITY = 0.80;
    public static final double MIN_GLASS_OPACITY = 0.40;
    public static final double MAX_GLASS_OPACITY = 1.00;

    public UserPreferences {
        if (theme == null) {
            theme = ThemeMode.DARK;
        }
        if (language == null) {
            language = "";
        }
        if (glassOpacity < MIN_GLASS_OPACITY || glassOpacity > MAX_GLASS_OPACITY) {
            glassOpacity = DEFAULT_GLASS_OPACITY;
        }
    }

    public UserPreferences(
            ThemeMode theme, boolean alwaysOnTop, boolean soundEnabled, String language) {
        this(theme, alwaysOnTop, soundEnabled, language, DEFAULT_GLASS_OPACITY);
    }

    public static UserPreferences defaults(boolean defaultAlwaysOnTop) {
        return new UserPreferences(
                ThemeMode.DARK, defaultAlwaysOnTop, true, "", DEFAULT_GLASS_OPACITY);
    }

    public UserPreferences withTheme(ThemeMode newTheme) {
        return new UserPreferences(newTheme, alwaysOnTop, soundEnabled, language, glassOpacity);
    }

    public UserPreferences withAlwaysOnTop(boolean newAlwaysOnTop) {
        return new UserPreferences(theme, newAlwaysOnTop, soundEnabled, language, glassOpacity);
    }

    public UserPreferences withSoundEnabled(boolean newSoundEnabled) {
        return new UserPreferences(theme, alwaysOnTop, newSoundEnabled, language, glassOpacity);
    }

    public UserPreferences withLanguage(String newLanguage) {
        return new UserPreferences(theme, alwaysOnTop, soundEnabled, newLanguage, glassOpacity);
    }

    public UserPreferences withGlassOpacity(double newGlassOpacity) {
        return new UserPreferences(theme, alwaysOnTop, soundEnabled, language, newGlassOpacity);
    }
}
