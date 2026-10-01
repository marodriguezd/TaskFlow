package io.github.marodriguezd.taskflow.ui.i18n;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of the supported UI languages and system-locale detection for first run.
 *
 * <p>TaskFlow 1.1.0 supports EXACTLY five languages, identified by BCP 47 tags: {@code en}, {@code
 * es}, {@code de}, {@code it}, {@code zh-Hans}. French and Portuguese are deliberately not
 * supported. The set is pinned by {@code LanguagesTest}; adding a language means adding a tag here,
 * a native name, and a {@code messages_<tag>.properties} bundle — no architectural change.
 *
 * <p>Tags are locale-independent persisted values (stored in the {@code preferences} table). This
 * class is pure logic with no JavaFX dependency so it stays headless-testable. Only {@code ui} may
 * depend on it.
 */
public final class Languages {

    public static final String EN = "en";
    public static final String ES = "es";
    public static final String DE = "de";
    public static final String IT = "it";
    public static final String ZH_HANS = "zh-Hans";

    /** Exactly five supported languages, in menu display order. */
    public static final List<String> SUPPORTED = List.of(EN, ES, DE, IT, ZH_HANS);

    private static final Map<String, String> NATIVE_NAMES = buildNativeNames();

    private Languages() {
        // Prevent instantiation
    }

    private static Map<String, String> buildNativeNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(EN, "English");
        names.put(ES, "Español");
        names.put(DE, "Deutsch");
        names.put(IT, "Italiano");
        names.put(ZH_HANS, "中文（简体）");
        return Map.copyOf(names);
    }

    /** Returns whether the given BCP 47 tag is one of the five supported languages. */
    public static boolean isSupported(String tag) {
        if (tag == null) {
            return false;
        }
        return SUPPORTED.contains(tag.trim());
    }

    /**
     * Returns the menu display name of a language, always written in the language itself. Unknown
     * tags fall back to the raw tag so nothing ever renders blank.
     */
    public static String nativeName(String tag) {
        if (tag == null) {
            return "";
        }
        return NATIVE_NAMES.getOrDefault(tag.trim(), tag.trim());
    }

    /** Maps a supported tag to the {@link Locale} used for bundle lookup and formatting. */
    public static Locale localeOf(String tag) {
        if (tag == null || tag.isBlank()) {
            return Locale.ENGLISH;
        }
        return Locale.forLanguageTag(tag.trim());
    }

    /**
     * Resolves a persisted language value at startup: a blank value (first run) is detected from
     * the OS locale, a supported tag is kept as-is, and any unknown value deterministically falls
     * back to English.
     */
    public static String resolveStored(String storedTag) {
        if (storedTag == null || storedTag.isBlank()) {
            return systemDefaultCode();
        }
        String trimmed = storedTag.trim();
        return SUPPORTED.contains(trimmed) ? trimmed : EN;
    }

    /**
     * Detects the UI language from the JVM default (OS) locale on first run. Any locale that is not
     * one of the five supported languages — including French and Portuguese — resolves to English.
     * For Chinese, only Simplified is shipped: Traditional script variants (TW/HK/MO) fall back to
     * English rather than receiving Simplified text.
     */
    public static String systemDefaultCode() {
        Locale system = Locale.getDefault();
        String language = system.getLanguage();
        return switch (language) {
            case "en", "es", "de", "it" -> language;
            case "zh" -> isTraditionalChinese(system) ? EN : ZH_HANS;
            default -> EN;
        };
    }

    private static boolean isTraditionalChinese(Locale locale) {
        return "Hant".equals(locale.getScript())
                || "TW".equals(locale.getCountry())
                || "HK".equals(locale.getCountry())
                || "MO".equals(locale.getCountry());
    }
}
