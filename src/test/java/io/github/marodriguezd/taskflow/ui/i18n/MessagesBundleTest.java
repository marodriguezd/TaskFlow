package io.github.marodriguezd.taskflow.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Headless checks of the message bundles: canonical English key inventory, deterministic fallback,
 * placeholder formatting and pluralization. Cross-locale file parity is asserted in {@code
 * LocaleFilesTest} (added together with the translations).
 */
class MessagesBundleTest {

    /** The canonical key inventory of TaskFlow 1.1.x — every UI string in one place. */
    private static final List<String> CANONICAL_KEYS =
            List.of(
                    "header.tasks.one",
                    "header.tasks.other",
                    "tooltip.pin",
                    "tooltip.history",
                    "tooltip.theme",
                    "tooltip.language",
                    "tooltip.edit",
                    "tooltip.delete",
                    "tooltip.done",
                    "tooltip.about",
                    "empty.title",
                    "empty.hint",
                    "footer.newTask",
                    "dialog.add.title",
                    "dialog.edit.title",
                    "dialog.name.prompt",
                    "dialog.minutes",
                    "dialog.priority",
                    "dialog.cancel",
                    "dialog.add",
                    "dialog.save",
                    "history.title",
                    "history.empty",
                    "history.restore",
                    "history.mode.deleted",
                    "history.mode.manual",
                    "history.mode.timer",
                    "priority.high",
                    "priority.medium",
                    "priority.low",
                    "about.title",
                    "about.app",
                    "about.description",
                    "about.license.label",
                    "about.license.value",
                    "about.copyright",
                    "about.warranty",
                    "about.license.button",
                    "about.close");

    @BeforeEach
    void setUp() {
        Messages.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        Messages.setLocale(Locale.ENGLISH);
    }

    @Test
    @DisplayName("English base bundle contains exactly the canonical key set")
    void testCanonicalKeyInventory() {
        ResourceBundle base =
                ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH, noFallbackControl());
        Enumeration<String> keys = base.getKeys();
        List<String> actual = new ArrayList<>();
        while (keys.hasMoreElements()) {
            actual.add(keys.nextElement());
        }
        assertThat(actual).containsExactlyInAnyOrderElementsOf(CANONICAL_KEYS);
    }

    @Test
    @DisplayName("Every supported language resolves a bundle containing all canonical keys")
    void testAllSupportedBundlesResolve() {
        for (String tag : Languages.SUPPORTED) {
            ResourceBundle bundle =
                    ResourceBundle.getBundle(
                            "i18n.messages",
                            Languages.localeOf(tag),
                            Messages.class.getClassLoader(),
                            noFallbackControl());
            assertThat(bundle).as("bundle for %s", tag).isNotNull();
            for (String key : CANONICAL_KEYS) {
                assertThat(bundle.containsKey(key))
                        .as("key '%s' in bundle '%s'", key, tag)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("Missing keys deterministically return the key itself, never null")
    void testMissingKeyReturnsKeyItself() {
        assertThat(Messages.get("does.not.exist")).isEqualTo("does.not.exist");
        assertThat(Messages.get("")).isEmpty();
        assertThat(Messages.get("some.missing.key", 1)).isEqualTo("some.missing.key");
    }

    @Test
    @DisplayName("English values match the 1.0.0 literals exactly (behavior preservation)")
    void testEnglishValuesPreserved() {
        assertThat(Messages.get("footer.newTask")).isEqualTo("＋  New task");
        assertThat(Messages.get("empty.title")).isEqualTo("No tasks yet");
        assertThat(Messages.get("empty.hint")).isEqualTo("Press + New task to get started");
        assertThat(Messages.get("dialog.add.title")).isEqualTo("New task");
        assertThat(Messages.get("dialog.edit.title")).isEqualTo("Edit task");
        assertThat(Messages.get("dialog.name.prompt")).isEqualTo("What are you going to work on?");
        assertThat(Messages.get("dialog.minutes")).isEqualTo("Minutes");
        assertThat(Messages.get("dialog.priority")).isEqualTo("Priority");
        assertThat(Messages.get("dialog.cancel")).isEqualTo("Cancel");
        assertThat(Messages.get("dialog.add")).isEqualTo("Add");
        assertThat(Messages.get("dialog.save")).isEqualTo("Save");
        assertThat(Messages.get("history.title")).isEqualTo("History");
        assertThat(Messages.get("history.empty")).isEqualTo("No tasks in history");
        assertThat(Messages.get("history.restore")).isEqualTo("Restore");
        assertThat(Messages.get("history.mode.deleted")).isEqualTo("Deleted");
        assertThat(Messages.get("history.mode.manual")).isEqualTo("Completed manually");
        assertThat(Messages.get("history.mode.timer")).isEqualTo("Completed by timer");
        assertThat(Messages.get("tooltip.pin")).isEqualTo("Toggle always on top");
        assertThat(Messages.get("tooltip.history")).isEqualTo("View task history");
        assertThat(Messages.get("tooltip.theme")).isEqualTo("Switch light/dark theme");
        assertThat(Messages.get("tooltip.language")).isEqualTo("Switch language");
        assertThat(Messages.get("tooltip.edit")).isEqualTo("Edit task");
        assertThat(Messages.get("tooltip.delete")).isEqualTo("Delete task");
        assertThat(Messages.get("tooltip.done")).isEqualTo("Mark as done");
        assertThat(Messages.get("priority.high")).isEqualTo("High");
        assertThat(Messages.get("priority.medium")).isEqualTo("Medium");
        assertThat(Messages.get("priority.low")).isEqualTo("Low");
    }

    @Test
    @DisplayName("Pluralization matches 1.0.0 badge behavior without locale grouping")
    void testPluralization() {
        assertThat(Messages.count("header.tasks", 1)).isEqualTo("1 task");
        assertThat(Messages.count("header.tasks", 0)).isEqualTo("0 tasks");
        assertThat(Messages.count("header.tasks", 2)).isEqualTo("2 tasks");
        assertThat(Messages.count("header.tasks", 42)).isEqualTo("42 tasks");
        // Pre-formatted count: no grouping separators are introduced
        assertThat(Messages.count("header.tasks", 1000)).isEqualTo("1000 tasks");
    }

    @Test
    @DisplayName("Placeholders are substituted; arg-less strings are returned verbatim")
    void testPlaceholderFormatting() {
        assertThat(Messages.get("header.tasks.one", "7")).isEqualTo("7 task");
        assertThat(Messages.get("header.tasks.other", "7")).isEqualTo("7 tasks");
        // No args -> no MessageFormat pass (apostrophes safe)
        assertThat(Messages.get("dialog.name.prompt")).isEqualTo("What are you going to work on?");
    }

    private static ResourceBundle.Control noFallbackControl() {
        return new ResourceBundle.Control() {
            @Override
            public Locale getFallbackLocale(String bundleName, Locale locale) {
                return null;
            }
        };
    }
}
