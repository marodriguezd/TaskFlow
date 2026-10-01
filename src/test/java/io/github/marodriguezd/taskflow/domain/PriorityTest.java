package io.github.marodriguezd.taskflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PriorityTest {

    @Test
    @DisplayName("Priority levels have expected order and visual properties")
    void testPriorityOrderingAndColors() {
        assertThat(Priority.HIGH.getOrder()).isEqualTo(0);
        assertThat(Priority.MEDIUM.getOrder()).isEqualTo(1);
        assertThat(Priority.LOW.getOrder()).isEqualTo(2);

        assertThat(Priority.HIGH.getDisplayName()).isEqualTo("Alta");
        assertThat(Priority.MEDIUM.getDisplayName()).isEqualTo("Media");
        assertThat(Priority.LOW.getDisplayName()).isEqualTo("Baja");

        assertThat(Priority.HIGH.getHexColor()).isEqualTo("#ff5e78");
        assertThat(Priority.MEDIUM.getHexColor()).isEqualTo("#ffb340");
        assertThat(Priority.LOW.getHexColor()).isEqualTo("#3ddc84");
    }

    @ParameterizedTest
    @CsvSource({
        "Alta, HIGH",
        "alta, HIGH",
        "ALTA, HIGH",
        "HIGH, HIGH",
        "Media, MEDIUM",
        "media, MEDIUM",
        "MEDIUM, MEDIUM",
        "Baja, LOW",
        "baja, LOW",
        "LOW, LOW"
    })
    @DisplayName("Priority correctly parses from Spanish or English names")
    void testFromDisplayNameValid(String input, Priority expected) {
        assertThat(Priority.fromDisplayName(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "unknown", "invalid", "foo"})
    @DisplayName("Priority falls back to MEDIUM on unrecognized input")
    void testFromDisplayNameFallback(String input) {
        assertThat(Priority.fromDisplayName(input)).isEqualTo(Priority.MEDIUM);
        assertThat(Priority.fromDisplayName(null)).isEqualTo(Priority.MEDIUM);
    }
}
