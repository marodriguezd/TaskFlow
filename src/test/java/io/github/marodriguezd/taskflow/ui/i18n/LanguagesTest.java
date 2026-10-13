package io.github.marodriguezd.taskflow.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LanguagesTest {

    private Locale originalDefault;

    @BeforeEach
    void setUp() {
        originalDefault = Locale.getDefault();
        Messages.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(originalDefault);
        Messages.setLocale(Locale.ENGLISH);
    }

    @Test
    @DisplayName("Exactly six supported languages: en, es, de, fr, it, zh-Hans")
    void testExactlyFiveSupportedLanguages() {
        assertThat(Languages.SUPPORTED)
                .containsExactly("en", "es", "de", "fr", "it", "zh-Hans")
                .hasSize(6);
    }

    @Test
    @DisplayName("Portuguese is not supported (French is supported since 1.2)")
    void testFrenchAndPortugueseExcluded() {
        assertThat(Languages.SUPPORTED).doesNotContain("pt");
        assertThat(Languages.isSupported("fr")).isTrue();
        assertThat(Languages.isSupported("pt")).isFalse();
        assertThat(Languages.isSupported("pt-BR")).isFalse();
    }

    @Test
    @DisplayName("Each supported language has a unique, non-blank native name")
    void testNativeNames() {
        Set<String> names = new HashSet<>();
        for (String tag : Languages.SUPPORTED) {
            String nativeName = Languages.nativeName(tag);
            assertThat(nativeName).isNotBlank();
            assertThat(names.add(nativeName)).isTrue();
        }
        assertThat(Languages.nativeName("en")).isEqualTo("English");
        assertThat(Languages.nativeName("es")).isEqualTo("Español");
        assertThat(Languages.nativeName("de")).isEqualTo("Deutsch");
        assertThat(Languages.nativeName("fr")).isEqualTo("Français");
        assertThat(Languages.nativeName("it")).isEqualTo("Italiano");
        assertThat(Languages.nativeName("zh-Hans")).isEqualTo("中文（简体）");
        // Unknown/null tags never render blank
        assertThat(Languages.nativeName("xx")).isEqualTo("xx");
        assertThat(Languages.nativeName(null)).isEmpty();
    }

    @Test
    @DisplayName("isSupported accepts only the six canonical tags")
    void testIsSupported() {
        assertThat(Languages.isSupported("en")).isTrue();
        assertThat(Languages.isSupported("es")).isTrue();
        assertThat(Languages.isSupported("de")).isTrue();
        assertThat(Languages.isSupported("fr")).isTrue();
        assertThat(Languages.isSupported("it")).isTrue();
        assertThat(Languages.isSupported("zh-Hans")).isTrue();
        assertThat(Languages.isSupported(" zh-Hans ")).isTrue();
        assertThat(Languages.isSupported("zh")).isFalse();
        assertThat(Languages.isSupported("zh-CN")).isFalse();
        assertThat(Languages.isSupported("en-US")).isFalse();
        assertThat(Languages.isSupported(null)).isFalse();
        assertThat(Languages.isSupported("")).isFalse();
    }

    @Test
    @DisplayName("resolveStored keeps supported values, detects first run, falls back to English")
    void testResolveStored() {
        assertThat(Languages.resolveStored("de")).isEqualTo("de");
        assertThat(Languages.resolveStored("fr")).isEqualTo("fr");
        assertThat(Languages.resolveStored("zh-Hans")).isEqualTo("zh-Hans");
        assertThat(Languages.resolveStored(" unknown ")).isEqualTo("en");
        assertThat(Languages.resolveStored("pt")).isEqualTo("en");

        // Blank (first run) resolves from the OS locale
        Locale.setDefault(Locale.forLanguageTag("it-CH"));
        assertThat(Languages.resolveStored("")).isEqualTo("it");
        assertThat(Languages.resolveStored(null)).isEqualTo("it");
        assertThat(Languages.resolveStored("   ")).isEqualTo("it");

        Locale.setDefault(Locale.forLanguageTag("fr-FR"));
        assertThat(Languages.resolveStored("")).isEqualTo("fr");
    }

    @Test
    @DisplayName("System detection maps supported OS locales and falls back to English otherwise")
    void testSystemDefaultCode() {
        Locale.setDefault(Locale.forLanguageTag("en-US"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("en");
        Locale.setDefault(Locale.forLanguageTag("es-MX"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("es");
        Locale.setDefault(Locale.forLanguageTag("de-AT"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("de");
        Locale.setDefault(Locale.forLanguageTag("fr-FR"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("fr");
        Locale.setDefault(Locale.forLanguageTag("it-CH"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("it");

        // Chinese: Simplified is shipped, Traditional falls back to English
        Locale.setDefault(Locale.forLanguageTag("zh-CN"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("zh-Hans");
        Locale.setDefault(Locale.forLanguageTag("zh"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("zh-Hans");
        Locale.setDefault(Locale.forLanguageTag("zh-SG"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("zh-Hans");
        Locale.setDefault(Locale.forLanguageTag("zh-Hant-TW"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("en");
        Locale.setDefault(Locale.forLanguageTag("zh-TW"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("en");

        // Unsupported languages, including Portuguese, detect as English
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("en");
        Locale.setDefault(Locale.forLanguageTag("pl-PL"));
        assertThat(Languages.systemDefaultCode()).isEqualTo("en");
    }

    @Test
    @DisplayName("localeOf maps tags to Java locales used for bundle lookup")
    void testLocaleOf() {
        assertThat(Languages.localeOf("en")).isEqualTo(Locale.ENGLISH);
        assertThat(Languages.localeOf("de").getLanguage()).isEqualTo("de");
        assertThat(Languages.localeOf("zh-Hans").getScript()).isEqualTo("Hans");
        assertThat(Languages.localeOf("zh-Hans").getLanguage()).isEqualTo("zh");
        assertThat(Languages.localeOf(null)).isEqualTo(Locale.ENGLISH);
        assertThat(Languages.localeOf("  ")).isEqualTo(Locale.ENGLISH);
    }
}
