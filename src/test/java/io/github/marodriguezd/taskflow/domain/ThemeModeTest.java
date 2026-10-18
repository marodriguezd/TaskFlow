package io.github.marodriguezd.taskflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ThemeModeTest {

    @Test
    @DisplayName("Theme modes have expected metadata and toggle behavior")
    void testThemePropertiesAndToggle() {
        assertThat(ThemeMode.DARK.getCode()).isEqualTo("dark");
        assertThat(ThemeMode.DARK.getIcon()).isEqualTo("🌙");
        assertThat(ThemeMode.DARK.getDescription()).isEqualTo("Dark theme");
        assertThat(ThemeMode.DARK.toggle()).isEqualTo(ThemeMode.LIGHT);

        assertThat(ThemeMode.LIGHT.getCode()).isEqualTo("light");
        assertThat(ThemeMode.LIGHT.getIcon()).isEqualTo("☀");
        assertThat(ThemeMode.LIGHT.getDescription()).isEqualTo("Light theme");
        assertThat(ThemeMode.LIGHT.toggle()).isEqualTo(ThemeMode.DARK);
    }

    @ParameterizedTest
    @CsvSource({
        "dark, DARK",
        "Dark, DARK",
        "DARK, DARK",
        "light, LIGHT",
        "Light, LIGHT",
        "LIGHT, LIGHT"
    })
    @DisplayName("fromCode parses code or name case-insensitively")
    void testFromCodeValid(String input, ThemeMode expected) {
        assertThat(ThemeMode.fromCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "unknown", "invalid", "foo"})
    @DisplayName("fromCode falls back to DARK on unrecognized or blank input")
    void testFromCodeFallback(String input) {
        assertThat(ThemeMode.fromCode(input)).isEqualTo(ThemeMode.DARK);
        assertThat(ThemeMode.fromCode(null)).isEqualTo(ThemeMode.DARK);
    }

    @Test
    @DisplayName("ThemeMode caches values() in a private static final array to avoid allocation")
    void testCachedValuesArray() throws Exception {
        java.lang.reflect.Field field = ThemeMode.class.getDeclaredField("VALUES");
        int modifiers = field.getModifiers();
        assertThat(java.lang.reflect.Modifier.isPrivate(modifiers)).isTrue();
        assertThat(java.lang.reflect.Modifier.isStatic(modifiers)).isTrue();
        assertThat(java.lang.reflect.Modifier.isFinal(modifiers)).isTrue();

        field.setAccessible(true);
        ThemeMode[] cached = (ThemeMode[]) field.get(null);
        assertThat(cached).containsExactly(ThemeMode.values());
    }
}
