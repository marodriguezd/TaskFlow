package io.github.marodriguezd.taskflow.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.Priority;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Headless coverage for localized priority presentation. Verifies English rendering is identical to
 * 1.0.0, that displayed labels round-trip back to the correct enum (combo-box safety), and that the
 * legacy Spanish parse path via {@link Priority#fromDisplayName} still works unchanged.
 */
class PriorityLabelsTest {

    @BeforeEach
    void setUp() {
        Messages.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        Messages.setLocale(Locale.ENGLISH);
    }

    @Test
    @DisplayName("English labels are identical to 1.0.0 display names")
    void testEnglishLabelsPreserved() {
        assertThat(PriorityLabels.label(Priority.HIGH)).isEqualTo("High");
        assertThat(PriorityLabels.label(Priority.MEDIUM)).isEqualTo("Medium");
        assertThat(PriorityLabels.label(Priority.LOW)).isEqualTo("Low");
        assertThat(PriorityLabels.label(null)).isEmpty();
    }

    @Test
    @DisplayName("Keys are locale-independent enum-derived identifiers")
    void testKeys() {
        assertThat(PriorityLabels.key(Priority.HIGH)).isEqualTo("priority.high");
        assertThat(PriorityLabels.key(Priority.MEDIUM)).isEqualTo("priority.medium");
        assertThat(PriorityLabels.key(Priority.LOW)).isEqualTo("priority.low");
    }

    @Test
    @DisplayName("Displayed labels round-trip back to the correct priority")
    void testRoundTrip() {
        for (Priority priority : Priority.values()) {
            assertThat(PriorityLabels.parse(PriorityLabels.label(priority))).isEqualTo(priority);
        }
    }

    @Test
    @DisplayName("parse handles English, enum names, legacy Spanish data and junk")
    void testParseFallbacks() {
        // English labels and enum names (existing 1.0.0 data paths)
        assertThat(PriorityLabels.parse("High")).isEqualTo(Priority.HIGH);
        assertThat(PriorityLabels.parse("high")).isEqualTo(Priority.HIGH);
        assertThat(PriorityLabels.parse("LOW")).isEqualTo(Priority.LOW);
        assertThat(PriorityLabels.parse("MEDIUM")).isEqualTo(Priority.MEDIUM);
        // Legacy pre-2.0 Spanish labels stored in old data files/databases
        assertThat(PriorityLabels.parse("alta")).isEqualTo(Priority.HIGH);
        assertThat(PriorityLabels.parse("Media")).isEqualTo(Priority.MEDIUM);
        assertThat(PriorityLabels.parse("baja")).isEqualTo(Priority.LOW);
        // Unknown / empty input defaults to MEDIUM, never throws
        assertThat(PriorityLabels.parse("no-such-priority")).isEqualTo(Priority.MEDIUM);
        assertThat(PriorityLabels.parse("")).isEqualTo(Priority.MEDIUM);
        assertThat(PriorityLabels.parse("   ")).isEqualTo(Priority.MEDIUM);
        assertThat(PriorityLabels.parse(null)).isEqualTo(Priority.MEDIUM);
    }

    @Test
    @DisplayName("Localized labels parse back correctly in every supported language")
    void testParseInAllSupportedLanguages() {
        for (String tag : Languages.SUPPORTED) {
            Messages.setLocale(Languages.localeOf(tag));
            for (Priority priority : Priority.values()) {
                assertThat(PriorityLabels.parse(PriorityLabels.label(priority)))
                        .as("priority %s in locale %s", priority, tag)
                        .isEqualTo(priority);
            }
        }
    }
}
