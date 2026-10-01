package io.github.marodriguezd.taskflow.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeFormatterTest {

    @ParameterizedTest
    @CsvSource({
        "1500, '25:00'",
        "0, '00:00'",
        "-10, '00:00'",
        "60, '01:00'",
        "65, '01:05'",
        "9, '00:09'",
        "3599, '59:59'",
        "3600, '60:00'",
        "7205, '120:05'"
    })
    @DisplayName("Formats seconds to MM:SS correctly")
    void testFormat(int seconds, String expected) {
        assertThat(TimeFormatter.format(seconds)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Parses minutes to seconds correctly")
    void testParseMinutesToSeconds() {
        assertThat(TimeFormatter.parseMinutesToSeconds(25)).isEqualTo(1500);
        assertThat(TimeFormatter.parseMinutesToSeconds(1)).isEqualTo(60);
        assertThat(TimeFormatter.parseMinutesToSeconds(0)).isEqualTo(60);
    }
}
