package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Task;

/**
 * Canonical content signatures for legacy records.
 *
 * <p>Signatures are length-prefixed so that user text containing the separator character cannot
 * collide with a different record. History signatures include the canonical <em>local</em>
 * timestamp rather than an instant, so the identity of a source record does not change with the JVM
 * time zone. Records whose timestamp could not be parsed use an explicit unreliable marker and are
 * only matched as a multiset by their remaining fields.
 */
public final class LegacyRecordSignature {

    private static final String TASK_KIND = "task";
    private static final String HISTORY_KIND = "history";

    /** Marks a history record whose source timestamp was missing or unparsable. */
    private static final String UNRELIABLE_TIMESTAMP = "unreliable";

    private LegacyRecordSignature() {}

    public static String forTask(Task task) {
        StringBuilder signature = new StringBuilder(TASK_KIND);
        append(signature, task.name());
        append(signature, task.priority().name());
        append(signature, Integer.toString(task.totalSeconds()));
        append(signature, Integer.toString(task.remainingSeconds()));
        return signature.toString();
    }

    public static String forHistory(HistoryItem item, String canonicalSourceTimestamp) {
        StringBuilder signature = new StringBuilder(HISTORY_KIND);
        append(signature, item.name());
        append(signature, item.priority().name());
        append(signature, Integer.toString(item.totalSeconds()));
        append(signature, Integer.toString(item.remainingSeconds()));
        append(signature, item.eventType().getCode());
        append(signature, Boolean.toString(item.completedManually()));
        append(
                signature,
                canonicalSourceTimestamp == null ? UNRELIABLE_TIMESTAMP : canonicalSourceTimestamp);
        return signature.toString();
    }

    private static void append(StringBuilder signature, String value) {
        signature.append(value.length()).append(':').append(value).append(';');
    }
}
