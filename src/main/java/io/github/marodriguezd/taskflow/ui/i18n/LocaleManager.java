package io.github.marodriguezd.taskflow.ui.i18n;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the current UI language and notifies listeners on change — the language counterpart of
 * {@code ui.theme.ThemeManager}: state and notifications only, no persistence.
 *
 * <p>Persistence follows the established theme pattern: the caller (MainWindow) saves the updated
 * {@code UserPreferences} after a successful switch, so a single owner keeps the whole record in
 * sync. The composition root (TaskFlowApp) resolves and persists the first-run language before this
 * manager is constructed.
 *
 * <p>Switching swaps the {@link Messages} bundle synchronously, then notifies listeners on the
 * JavaFX thread so registered views can re-render immediately — no restart required. Unsupported
 * tags are rejected, so a bad value can never render or persist. No JavaFX imports: headless
 * testable.
 */
public class LocaleManager {

    private static final Logger log = LoggerFactory.getLogger(LocaleManager.class);

    private final List<Consumer<String>> localeListeners = new CopyOnWriteArrayList<>();
    private String currentTag;

    /**
     * Creates the manager for a resolved language tag. Unknown/null tags fall back to English so a
     * corrupt persisted value can never break startup.
     */
    public LocaleManager(String initialTag) {
        String resolved = Languages.isSupported(initialTag) ? initialTag.trim() : Languages.EN;
        this.currentTag = resolved;
        Messages.setLocale(Languages.localeOf(resolved));
        log.info("UI language initialized: {}", resolved);
    }

    /** Returns the current language tag (always one of {@link Languages#SUPPORTED}). */
    public String getCurrentTag() {
        return currentTag;
    }

    /**
     * Switches the UI language: swaps the message bundle and notifies listeners. Returns {@code
     * true} when the language is now active (including no-op switches to the current language),
     * {@code false} when the tag is unsupported — in which case nothing changes.
     */
    public boolean setLocale(String tag) {
        if (tag == null) {
            log.warn("Rejected null UI language tag");
            return false;
        }
        String candidate = tag.trim();
        if (!Languages.isSupported(candidate)) {
            log.warn("Rejected unsupported UI language '{}'", tag);
            return false;
        }
        if (candidate.equals(currentTag)) {
            return true;
        }
        currentTag = candidate;
        Messages.setLocale(Languages.localeOf(candidate));
        log.info("UI language switched to {}", candidate);
        for (Consumer<String> listener : localeListeners) {
            listener.accept(candidate);
        }
        return true;
    }

    /** Registers a listener invoked with the new tag after every successful language change. */
    public void addLocaleChangeListener(Consumer<String> listener) {
        localeListeners.add(listener);
    }
}
