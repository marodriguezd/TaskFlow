package io.github.marodriguezd.taskflow.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DateTimeUtilTest {

    @Test
    @DisplayName("Formats and parses legacy dates reliably")
    void testFormatAndParse() {
        Instant now = Instant.now();
        String formatted = DateTimeUtil.format(now);
        assertThat(formatted).contains("—");

        Instant parsed = DateTimeUtil.parseLegacyOrDefault(formatted, Instant.EPOCH);
        assertThat(parsed).isNotEqualTo(Instant.EPOCH);
    }

    @Test
    @DisplayName("Returns default on unparseable legacy date")
    void testParseLegacyFallback() {
        Instant fallback = Instant.ofEpochMilli(123456789L);
        assertThat(DateTimeUtil.parseLegacyOrDefault("invalid-date-string", fallback))
                .isEqualTo(fallback);
        assertThat(DateTimeUtil.parseLegacyOrDefault(null, fallback)).isEqualTo(fallback);
        assertThat(DateTimeUtil.parseLegacyOrDefault("  ", fallback)).isEqualTo(fallback);
    }
}
