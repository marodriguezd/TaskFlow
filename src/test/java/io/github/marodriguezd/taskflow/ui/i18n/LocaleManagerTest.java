package io.github.marodriguezd.taskflow.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Headless tests for runtime language switching. No JavaFX toolkit required — LocaleManager is pure
 * state + listeners, mirroring the ThemeManager pattern.
 */
class LocaleManagerTest {

    private final List<String> received = new ArrayList<>();

    @BeforeEach
    void setUp() {
        received.clear();
        Messages.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        Messages.setLocale(Locale.ENGLISH);
    }

    @Test
    @DisplayName("Constructor initializes the message bundle for a supported tag")
    void testConstructorInitializesBundle() {
        LocaleManager manager = new LocaleManager("de");
        manager.addLocaleChangeListener(received::add);

        assertThat(manager.getCurrentTag()).isEqualTo("de");
        assertThat(Messages.currentLocale().getLanguage()).isEqualTo("de");
        assertThat(Messages.get("dialog.cancel")).isNotBlank();
        assertThat(received).isEmpty(); // construction is not a change event
    }

    @Test
    @DisplayName("Constructor falls back to English for unknown/null tags (corrupt data safe)")
    void testConstructorFallsBackOnUnknownTag() {
        LocaleManager manager = new LocaleManager("klingon");
        assertThat(manager.getCurrentTag()).isEqualTo("en");
        assertThat(Messages.currentLocale()).isEqualTo(Locale.ENGLISH);

        LocaleManager nullManager = new LocaleManager(null);
        assertThat(nullManager.getCurrentTag()).isEqualTo("en");
    }

    @Test
    @DisplayName("setLocale switches bundle and notifies listeners (runtime switch, no restart)")
    void testSetLocaleSwitchesAndNotifies() {
        LocaleManager manager = new LocaleManager("en");
        manager.addLocaleChangeListener(received::add);

        assertThat(manager.setLocale("zh-Hans")).isTrue();
        assertThat(manager.getCurrentTag()).isEqualTo("zh-Hans");
        assertThat(Messages.currentLocale().getLanguage()).isEqualTo("zh");
        assertThat(Messages.currentLocale().getScript()).isEqualTo("Hans");
        assertThat(Messages.get("dialog.cancel")).isNotBlank();
        assertThat(received).containsExactly("zh-Hans");

        assertThat(manager.setLocale("it")).isTrue();
        assertThat(Messages.currentLocale().getLanguage()).isEqualTo("it");
        assertThat(received).containsExactly("zh-Hans", "it");
    }

    @Test
    @DisplayName("Unsupported tags are rejected without state or bundle changes")
    void testUnsupportedTagRejected() {
        LocaleManager manager = new LocaleManager("en");
        manager.addLocaleChangeListener(received::add);

        assertThat(manager.setLocale("fr")).isFalse();
        assertThat(manager.setLocale("pt")).isFalse();
        assertThat(manager.setLocale("pt-BR")).isFalse();
        assertThat(manager.setLocale("zh")).isFalse();
        assertThat(manager.setLocale(null)).isFalse();

        assertThat(manager.getCurrentTag()).isEqualTo("en");
        assertThat(Messages.currentLocale()).isEqualTo(Locale.ENGLISH);
        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("Switching to the current language is a successful no-op")
    void testSameTagIsNoOp() {
        LocaleManager manager = new LocaleManager("es");
        manager.addLocaleChangeListener(received::add);

        assertThat(manager.setLocale("es")).isTrue();
        assertThat(received).isEmpty();
        assertThat(manager.getCurrentTag()).isEqualTo("es");
    }

    @Test
    @DisplayName("Full first-run flow: resolve OS locale, construct, switch, all five languages")
    void testFirstRunResolutionAndAllLanguages() {
        // Simulated composition root: blank persisted value -> OS detection -> LocaleManager
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("it-IT"));
            String resolved = Languages.resolveStored("");
            assertThat(resolved).isEqualTo("it");

            LocaleManager manager = new LocaleManager(resolved);
            manager.addLocaleChangeListener(received::add);
            assertThat(Messages.currentLocale().getLanguage()).isEqualTo("it");
            assertThat(Messages.get("empty.title")).isNotBlank();

            // Cycle through every supported language exactly as the menu does
            for (String tag : Languages.SUPPORTED) {
                assertThat(manager.setLocale(tag)).as("switching to %s", tag).isTrue();
                assertThat(manager.getCurrentTag()).isEqualTo(tag);
                assertThat(Messages.get("dialog.cancel")).isNotBlank();
            }
            assertThat(manager.getCurrentTag()).isEqualTo("zh-Hans");
            assertThat(received).hasSize(Languages.SUPPORTED.size());
        } finally {
            Locale.setDefault(original);
        }
    }
}
