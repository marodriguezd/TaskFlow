package io.github.marodriguezd.taskflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HistoryEventTypeTest {

    @Test
    @DisplayName("History event types have expected code and label metadata")
    void testHistoryEventTypeProperties() {
        assertThat(HistoryEventType.COMPLETED.getCode()).isEqualTo("completed");
        assertThat(HistoryEventType.COMPLETED.getLabel()).isEqualTo("Completed");

        assertThat(HistoryEventType.DELETED.getCode()).isEqualTo("deleted");
        assertThat(HistoryEventType.DELETED.getLabel()).isEqualTo("Deleted");
    }

    @ParameterizedTest
    @CsvSource({
        "completed, COMPLETED",
        "Completed, COMPLETED",
        "COMPLETED, COMPLETED",
        "deleted, DELETED",
        "Deleted, DELETED",
        "DELETED, DELETED"
    })
    @DisplayName("fromCode parses code or name case-insensitively")
    void testFromCodeValid(String input, HistoryEventType expected) {
        assertThat(HistoryEventType.fromCode(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "unknown", "invalid", "foo"})
    @DisplayName("fromCode falls back to COMPLETED on unrecognized or blank input")
    void testFromCodeFallback(String input) {
        assertThat(HistoryEventType.fromCode(input)).isEqualTo(HistoryEventType.COMPLETED);
        assertThat(HistoryEventType.fromCode(null)).isEqualTo(HistoryEventType.COMPLETED);
    }

    @Test
    @DisplayName(
            "HistoryEventType caches values() in a private static final array to avoid allocation")
    void testCachedValuesArray() throws Exception {
        java.lang.reflect.Field field = HistoryEventType.class.getDeclaredField("VALUES");
        int modifiers = field.getModifiers();
        assertThat(java.lang.reflect.Modifier.isPrivate(modifiers)).isTrue();
        assertThat(java.lang.reflect.Modifier.isStatic(modifiers)).isTrue();
        assertThat(java.lang.reflect.Modifier.isFinal(modifiers)).isTrue();

        field.setAccessible(true);
        HistoryEventType[] cached = (HistoryEventType[]) field.get(null);
        assertThat(cached).containsExactly(HistoryEventType.values());
    }
}
