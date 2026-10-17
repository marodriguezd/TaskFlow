package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Task;
import java.util.List;

/**
 * Durable provenance for legacy JSON imports.
 *
 * <p>Every successfully imported source is recorded by kind and SHA-256 of its original bytes, and
 * each imported task/history row carries the source id and a canonical content signature.
 * Provenance lives on the row itself, so deleting a row also deletes its provenance and a later
 * valid import is no longer suppressed by a record that no longer exists. Rows created by the Java
 * application keep {@code NULL} provenance and therefore never suppress a legacy record.
 */
public interface LegacyImportRepository {

    String KIND_TASKS = "tasks";
    String KIND_HISTORY = "history";
    String KIND_GEOMETRY = "geometry";
    String KIND_SETTINGS = "settings";

    /** True when this exact source (kind + original bytes) was already imported successfully. */
    boolean isSourceProcessed(String sourceKind, String sourceHash);

    /**
     * Persists the batch rows, their provenance, and the source-completion record in one
     * transaction. If the source is already recorded, nothing is inserted and every candidate is
     * reported as skipped, which makes retries after a failed rename idempotent.
     */
    ImportOutcome importBatch(ImportBatch batch);

    /**
     * Records a singleton source (geometry/settings) as processed. Callers must apply the value
     * first: a re-applied singleton is harmless, a falsely recorded source would be lost forever.
     */
    void markSourceProcessed(
            String sourceKind, String sourceHash, String sourceName, long sourceSize);

    record LegacyTaskCandidate(Task task, String signature) {
        public LegacyTaskCandidate {
            if (task == null || signature == null) {
                throw new IllegalArgumentException("Task candidates need a task and a signature");
            }
        }
    }

    record LegacyHistoryCandidate(
            HistoryItem item, String signature, String canonicalSourceTimestamp) {
        public LegacyHistoryCandidate {
            if (item == null || signature == null) {
                throw new IllegalArgumentException(
                        "History candidates need an item and a signature");
            }
        }
    }

    record ImportBatch(
            String sourceKind,
            String sourceHash,
            String sourceName,
            long sourceSize,
            List<LegacyTaskCandidate> tasks,
            List<LegacyHistoryCandidate> history) {
        public ImportBatch {
            if (sourceKind == null || sourceHash == null || sourceName == null) {
                throw new IllegalArgumentException("Import batches need source identity");
            }
            tasks = List.copyOf(tasks);
            history = List.copyOf(history);
        }
    }

    record ImportOutcome(
            boolean sourceAlreadyProcessed,
            int insertedTasks,
            int skippedTasks,
            int insertedHistory,
            int skippedHistory) {}
}
