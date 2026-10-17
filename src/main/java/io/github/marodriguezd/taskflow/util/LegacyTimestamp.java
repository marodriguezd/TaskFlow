package io.github.marodriguezd.taskflow.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * A legacy history timestamp, preserving both the wall-clock value written by the source file and
 * the instant resolved for storage.
 *
 * <p>Legacy files contain local wall-clock strings without a zone identifier. The local value is
 * therefore the stable source identity: it does not change when the JVM time zone changes. The
 * instant is only the storage/display value, resolved with the time zone that was in effect when
 * the source was first imported. A fallback value (both legacy fields missing, blank, or
 * unparsable) has no source local value and must never be used to identify a record.
 */
public record LegacyTimestamp(LocalDateTime localDateTime, Instant instant, Source source) {

    /** Which legacy field supplied the parsed value. */
    public enum Source {
        EVENT_AT,
        COMPLETED_AT,
        FALLBACK
    }

    public LegacyTimestamp {
        Objects.requireNonNull(instant, "instant must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (source == Source.FALLBACK && localDateTime != null) {
            throw new IllegalArgumentException(
                    "A fallback timestamp carries no source local value");
        }
        if (source != Source.FALLBACK && localDateTime == null) {
            throw new IllegalArgumentException("A parsed timestamp requires a local value");
        }
    }

    /** Whether a source field actually parsed; only these values may identify a record. */
    public boolean isReliable() {
        return source != Source.FALLBACK;
    }

    /**
     * Canonical, time-zone-independent representation of the source timestamp as an ISO local
     * date-time (for example {@code 2026-10-01T10:00}). Empty for fallback values, which are not
     * match identities.
     */
    public Optional<String> canonicalLocalTimestamp() {
        return Optional.ofNullable(localDateTime).map(LocalDateTime::toString);
    }

    /**
     * Creates an unreliable storage-only timestamp for records whose source value did not parse.
     */
    public static LegacyTimestamp fallback(Instant instant) {
        return new LegacyTimestamp(null, instant, Source.FALLBACK);
    }
}
