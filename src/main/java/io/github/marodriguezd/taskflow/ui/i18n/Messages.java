package io.github.marodriguezd.taskflow.ui.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Facade over the {@code i18n/messages} resource bundles — the single access point for all
 * translatable UI text.
 *
 * <p>Lookup rules (deterministic):
 *
 * <ul>
 *   <li>Keys resolve through the bundle parent chain for the current locale and always fall back to
 *       the English base bundle ({@code messages.properties}) when a translation is missing.
 *   <li>The JVM default locale is never consulted as a fallback (see {@link #CONTROL}), so an
 *       unsupported OS language can never inject an unshipped translation.
 *   <li>A key missing from the entire chain is returned as-is and logged once — never {@code null},
 *       never an exception.
 * </ul>
 *
 * <p>{@link MessageFormat} is applied only when arguments are passed, so plain strings containing
 * apostrophes (e.g. Italian) are never reinterpreted. Numeric arguments should be pre-formatted by
 * callers (see {@link #count}) to avoid locale grouping surprises.
 *
 * <p>Process-wide state: the app is single-window with modal dialogs, so one current locale is
 * sufficient. Set before any view constructs; swapped by {@code LocaleManager} at runtime. No
 * JavaFX dependency — headless-testable.
 */
public final class Messages {

    private static final Logger log = LoggerFactory.getLogger(Messages.class);

    private static final String BASE_NAME = "i18n.messages";

    /** Keys already reported as missing, so each is logged at most once per session. */
    private static final Set<String> MISSING_KEYS = ConcurrentHashMap.newKeySet();

    /**
     * Disables the default-locale fallback of {@link ResourceBundle#getBundle}: only the requested
     * locale's chain (ending in the English base bundle) is searched.
     */
    private static final ResourceBundle.Control CONTROL =
            new ResourceBundle.Control() {
                @Override
                public Locale getFallbackLocale(String bundleName, Locale locale) {
                    return null;
                }
            };

    private static volatile Locale currentLocale = Locale.ENGLISH;
    private static volatile ResourceBundle bundle = load(Locale.ENGLISH);

    private Messages() {
        // Prevent instantiation
    }

    /** Switches the active locale, reloading the bundle. {@code null} falls back to English. */
    public static synchronized void setLocale(Locale locale) {
        Locale target = locale != null ? locale : Locale.ENGLISH;
        if (target.equals(currentLocale)) {
            return;
        }
        currentLocale = target;
        bundle = load(target);
        log.debug("UI messages locale set to {}", target);
    }

    /** Returns the locale currently used for message lookup. */
    public static Locale currentLocale() {
        return currentLocale;
    }

    /** Returns the raw translated string for a key (no placeholder formatting). */
    public static String get(String key) {
        return lookup(key);
    }

    /** Returns the translated string with {@link MessageFormat} placeholders substituted. */
    public static String get(String key, Object... args) {
        String pattern = lookup(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, currentLocale).format(args);
    }

    /**
     * Returns a pluralized message for {@code baseKey}, selecting {@code baseKey.one} when {@code
     * count == 1} and {@code baseKey.other} otherwise (0 and large values use {@code .other} in all
     * five supported languages; Simplified Chinese ships identical forms). The count is passed as a
     * pre-formatted string so no locale grouping is applied.
     */
    public static String count(String baseKey, int count) {
        return get(baseKey + (count == 1 ? ".one" : ".other"), String.valueOf(count));
    }

    private static String lookup(String key) {
        ResourceBundle current = bundle;
        if (current.containsKey(key)) {
            return current.getString(key);
        }
        if (MISSING_KEYS.add(key)) {
            log.warn("Missing i18n key '{}' — returning the key itself", key);
        }
        return key;
    }

    private static ResourceBundle load(Locale locale) {
        return ResourceBundle.getBundle(
                BASE_NAME, locale, Messages.class.getClassLoader(), CONTROL);
    }
}
