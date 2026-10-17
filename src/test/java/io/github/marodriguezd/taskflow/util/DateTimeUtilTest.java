package io.github.marodriguezd.taskflow.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DateTimeUtilTest {

    private static final String LEGACY_TIMESTAMP = "10:00  —  01/10/26";
    private static final LocalDateTime SOURCE_LOCAL = LocalDateTime.of(2026, 10, 1, 10, 0);

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
    @DisplayName("Distinguishes parsed legacy dates from missing or invalid ones")
    void testParseLegacyDistinguishesReliableTimestamps() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L).truncatedTo(ChronoUnit.MINUTES);
        assertThat(DateTimeUtil.parseLegacy(DateTimeUtil.format(now))).contains(now);
        assertThat(DateTimeUtil.parseLegacy("invalid-date-string")).isEmpty();
        assertThat(DateTimeUtil.parseLegacy(null)).isEmpty();
        assertThat(DateTimeUtil.parseLegacy("  ")).isEmpty();
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

    @Test
    @DisplayName("Parsing the same wall-clock value in different zones yields different instants")
    void testExplicitZoneResolution() {
        Instant tokyo =
                DateTimeUtil.parseLegacy(LEGACY_TIMESTAMP, ZoneId.of("Asia/Tokyo")).orElseThrow();
        Instant losAngeles =
                DateTimeUtil.parseLegacy(LEGACY_TIMESTAMP, ZoneId.of("America/Los_Angeles"))
                        .orElseThrow();

        assertThat(tokyo).isEqualTo(SOURCE_LOCAL.atZone(ZoneId.of("Asia/Tokyo")).toInstant());
        assertThat(losAngeles)
                .isEqualTo(SOURCE_LOCAL.atZone(ZoneId.of("America/Los_Angeles")).toInstant());
        assertThat(tokyo).isNotEqualTo(losAngeles);
    }

    @Test
    @DisplayName("The canonical legacy value is the local wall clock, independent of the zone")
    void testCanonicalTimestampIsZoneIndependent() {
        Instant fallback = Instant.EPOCH;
        LegacyTimestamp tokyo =
                DateTimeUtil.parseLegacyTimestamp(
                        LEGACY_TIMESTAMP, null, ZoneId.of("Asia/Tokyo"), fallback);
        LegacyTimestamp losAngeles =
                DateTimeUtil.parseLegacyTimestamp(
                        LEGACY_TIMESTAMP, null, ZoneId.of("America/Los_Angeles"), fallback);

        assertThat(tokyo.source()).isEqualTo(LegacyTimestamp.Source.EVENT_AT);
        assertThat(losAngeles.source()).isEqualTo(LegacyTimestamp.Source.EVENT_AT);
        assertThat(tokyo.isReliable()).isTrue();
        assertThat(tokyo.canonicalLocalTimestamp()).contains("2026-10-01T10:00");
        assertThat(losAngeles.canonicalLocalTimestamp()).isEqualTo(tokyo.canonicalLocalTimestamp());
        assertThat(losAngeles.instant()).isNotEqualTo(tokyo.instant());
    }

    @Test
    @DisplayName(
            "event_at has precedence and completed_at covers missing, blank, or invalid values")
    void testFieldPrecedenceAndCompletedAtFallback() {
        ZoneId zone = ZoneId.of("Asia/Tokyo");
        Instant fallback = Instant.EPOCH;

        LegacyTimestamp both =
                DateTimeUtil.parseLegacyTimestamp(
                        LEGACY_TIMESTAMP, "09:00  —  01/10/26", zone, fallback);
        assertThat(both.source()).isEqualTo(LegacyTimestamp.Source.EVENT_AT);
        assertThat(both.canonicalLocalTimestamp()).contains("2026-10-01T10:00");

        for (String unusable : new String[] {null, "", "   ", "not-a-timestamp"}) {
            LegacyTimestamp completed =
                    DateTimeUtil.parseLegacyTimestamp(
                            unusable, "09:30  —  01/10/26", zone, fallback);
            assertThat(completed.source())
                    .as("completed_at fallback for event_at=%s", unusable)
                    .isEqualTo(LegacyTimestamp.Source.COMPLETED_AT);
            assertThat(completed.isReliable()).isTrue();
            assertThat(completed.canonicalLocalTimestamp()).contains("2026-10-01T09:30");
            assertThat(completed.instant())
                    .isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 30).atZone(zone).toInstant());
        }
    }

    @Test
    @DisplayName("Neither timestamp parsing yields an unreliable storage fallback")
    void testFallbackWhenNeitherTimestampParses() {
        Instant fallback = Instant.ofEpochMilli(123456789L);
        LegacyTimestamp result =
                DateTimeUtil.parseLegacyTimestamp(
                        "not-a-timestamp", "also-bad", ZoneId.of("Asia/Tokyo"), fallback);

        assertThat(result.source()).isEqualTo(LegacyTimestamp.Source.FALLBACK);
        assertThat(result.isReliable()).isFalse();
        assertThat(result.canonicalLocalTimestamp()).isEmpty();
        assertThat(result.instant()).isEqualTo(fallback);
    }
}
