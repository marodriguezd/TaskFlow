package io.github.marodriguezd.taskflow.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import io.github.marodriguezd.taskflow.domain.ThemeMode;
import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import io.github.marodriguezd.taskflow.persistence.LegacyImportRepository.ImportBatch;
import io.github.marodriguezd.taskflow.persistence.LegacyImportRepository.ImportOutcome;
import io.github.marodriguezd.taskflow.persistence.LegacyImportRepository.LegacyHistoryCandidate;
import io.github.marodriguezd.taskflow.persistence.LegacyImportRepository.LegacyTaskCandidate;
import io.github.marodriguezd.taskflow.util.DateTimeUtil;
import io.github.marodriguezd.taskflow.util.LegacyTimestamp;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migrates legacy TaskFlow JSON files (Python/PyQt6 implementation) to SQLite.
 *
 * <p>Every discovered source is imported; a non-empty destination table is never a reason to skip a
 * file. Identity is durable rather than inferred from the current table contents:
 *
 * <ul>
 *   <li>The SHA-256 of the original bytes, together with the source kind, is recorded in {@code
 *       legacy_sources} in the same transaction that inserts the rows. A retry after a failed
 *       rename therefore skips the source instead of duplicating it, and the source is renamed only
 *       after that transaction commits.
 *   <li>Each imported task/history row carries the source id and a canonical content signature.
 *       Only legacy-imported rows participate in deduplication: an application-created row is
 *       invisible to the matcher and can never suppress a legacy record. Signatures are consumed as
 *       a multiset, so repeated identical entries inside one source keep their multiplicity and
 *       overlapping sources import each shared record once.
 *   <li>History signatures use the canonical <em>local</em> timestamp from the legacy file, not a
 *       resolved instant, so a retry or restored backup under a different JVM time zone keeps the
 *       same identity. The instant is resolved once with the import-time zone for storage/display.
 * </ul>
 *
 * <p>Singleton sources keep their deterministic precedence: the home root is processed before the
 * application data directory, so a conflicting data-directory geometry/settings file wins. A source
 * already recorded as processed is not re-applied, which protects current user settings from a
 * restored stale backup.
 *
 * <p>Known ambiguity: legacy records carry no stable identifier, so two records with identical
 * fields are indistinguishable. Within one source the multiset policy preserves them; across
 * independently created sources, counts are matched, so an overlap is deduplicated while distinct
 * records are kept. Rows written before schema v2 have unknown provenance and are deliberately not
 * counted: a source restored after upgrading such a database can therefore import one duplicate
 * occurrence if it was previously imported without provenance. That residual limitation cannot be
 * resolved without inventing information the legacy data does not contain.
 */
public class LegacyDataMigrator {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataMigrator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Path dataDirectory;
    private final Path legacyHomeDirectory;
    private final TaskRepository taskRepository;
    private final HistoryRepository historyRepository;
    private final PreferenceRepository preferenceRepository;
    private final LegacyImportRepository legacyImportRepository;
    private final ZoneId zone;

    public LegacyDataMigrator(
            Path dataDirectory,
            TaskRepository taskRepository,
            HistoryRepository historyRepository,
            PreferenceRepository preferenceRepository,
            LegacyImportRepository legacyImportRepository) {
        this(
                dataDirectory,
                Paths.get(System.getProperty("user.home")),
                taskRepository,
                historyRepository,
                preferenceRepository,
                legacyImportRepository,
                ZoneId.systemDefault());
    }

    /** Test seam so migration tests never read the developer's real home directory. */
    LegacyDataMigrator(
            Path dataDirectory,
            Path legacyHomeDirectory,
            TaskRepository taskRepository,
            HistoryRepository historyRepository,
            PreferenceRepository preferenceRepository,
            LegacyImportRepository legacyImportRepository,
            ZoneId zone) {
        this.dataDirectory = dataDirectory;
        this.legacyHomeDirectory = legacyHomeDirectory;
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.preferenceRepository = preferenceRepository;
        this.legacyImportRepository = legacyImportRepository;
        this.zone = zone;
    }

    public void migrateIfNecessary() {
        try {
            migrateHomeRootLegacyFiles();
            migrateDirectoryLegacyFiles();
        } catch (Exception e) {
            log.warn(
                    "Non-fatal exception occurred during legacy data migration: {}",
                    e.getMessage(),
                    e);
        }
    }

    private void migrateHomeRootLegacyFiles() {
        Path homeData = legacyHomeDirectory.resolve(".taskflow_data.json");
        Path homeHistory = legacyHomeDirectory.resolve(".taskflow_history.json");
        Path homeGeometry = legacyHomeDirectory.resolve(".taskflow_geometry.json");

        if (Files.exists(homeData)) {
            migrateTasksFile(homeData.toFile());
        }
        if (Files.exists(homeHistory)) {
            migrateHistoryFile(homeHistory.toFile());
        }
        if (Files.exists(homeGeometry)) {
            migrateGeometryFile(homeGeometry.toFile());
        }
    }

    private void migrateDirectoryLegacyFiles() {
        File dataFile = dataDirectory.resolve("taskflow_data.json").toFile();
        File historyFile = dataDirectory.resolve("taskflow_history.json").toFile();
        File geometryFile = dataDirectory.resolve("taskflow_geometry.json").toFile();
        File settingsFile = dataDirectory.resolve("taskflow_settings.json").toFile();

        if (dataFile.exists()) {
            migrateTasksFile(dataFile);
        }
        if (historyFile.exists()) {
            migrateHistoryFile(historyFile);
        }
        if (geometryFile.exists()) {
            migrateGeometryFile(geometryFile);
        }
        if (settingsFile.exists()) {
            migrateSettingsFile(settingsFile);
        }
    }

    private void migrateTasksFile(File file) {
        try {
            // Read once: the hash that identifies the source and the bytes that are parsed must be
            // the same snapshot.
            byte[] sourceBytes = Files.readAllBytes(file.toPath());
            String sourceHash = SqliteLegacyImportRepository.sha256(sourceBytes);
            if (skipAlreadyImported(file, LegacyImportRepository.KIND_TASKS, sourceHash)) {
                return;
            }

            List<Map<String, Object>> list =
                    objectMapper.readValue(sourceBytes, new TypeReference<>() {});
            List<LegacyTaskCandidate> candidates = new ArrayList<>();
            int skippedCount = 0;
            for (Map<String, Object> map : list) {
                try {
                    // A missing/non-string name falls back to the legacy default, but an explicit
                    // blank name is unusable and is skipped (matching the pre-2.0 importer).
                    Object rawName = map.get("name");
                    String name = rawName instanceof String text ? text.trim() : "Task";
                    if (name.isEmpty()) {
                        skippedCount++;
                        continue;
                    }
                    Priority priority =
                            Priority.fromDisplayName(getString(map, "priority", "Media"));
                    // Durations <= 0 would be rejected by the domain; keep the task instead of
                    // dropping it so a single bad field never loses a user's task.
                    int totalSeconds = Math.max(1, getInt(map, "total_seconds", 1500));
                    int remaining = getInt(map, "remaining", totalSeconds);

                    Task task =
                            Task.create(name, priority, totalSeconds)
                                    .withRemainingSeconds(remaining);
                    candidates.add(
                            new LegacyTaskCandidate(task, LegacyRecordSignature.forTask(task)));
                } catch (RuntimeException e) {
                    // Isolate malformed records: aborting here would leave the file unimported
                    // while earlier rows make every later startup look like a completed import.
                    skippedCount++;
                    log.warn(
                            "Skipping malformed legacy task record in {}: {}",
                            file,
                            e.getMessage());
                }
            }

            ImportOutcome outcome =
                    legacyImportRepository.importBatch(
                            new ImportBatch(
                                    LegacyImportRepository.KIND_TASKS,
                                    sourceHash,
                                    file.getName(),
                                    sourceBytes.length,
                                    candidates,
                                    List.of()));
            log.info(
                    "Migrated {} active tasks from legacy file {} ({} already present, {} invalid records skipped{})",
                    outcome.insertedTasks(),
                    file,
                    outcome.skippedTasks(),
                    skippedCount,
                    outcome.sourceAlreadyProcessed() ? ", source was already processed" : "");
            renameToMigrated(file);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse legacy tasks file {}: {}", file, e.getMessage());
        }
    }

    private void migrateHistoryFile(File file) {
        try {
            byte[] sourceBytes = Files.readAllBytes(file.toPath());
            String sourceHash = SqliteLegacyImportRepository.sha256(sourceBytes);
            if (skipAlreadyImported(file, LegacyImportRepository.KIND_HISTORY, sourceHash)) {
                return;
            }

            List<Map<String, Object>> list =
                    objectMapper.readValue(sourceBytes, new TypeReference<>() {});
            List<LegacyHistoryCandidate> candidates = new ArrayList<>();
            int skippedCount = 0;
            // One fallback for the whole file; it is only a stored display value and is never a
            // match identity.
            Instant fallbackEventAt = Instant.now();
            for (Map<String, Object> map : list) {
                try {
                    Object rawName = map.get("name");
                    String name = rawName instanceof String text ? text.trim() : "Completed task";
                    if (name.isEmpty()) {
                        skippedCount++;
                        continue;
                    }
                    Priority priority =
                            Priority.fromDisplayName(getString(map, "priority", "Media"));
                    int totalSeconds = getInt(map, "total_seconds", 1500);
                    int remaining = getInt(map, "remaining", 0);
                    HistoryEventType eventType =
                            HistoryEventType.fromCode(getString(map, "history_event", "completed"));
                    boolean completedManually = getBoolean(map.get("completed_manually"));

                    // event_at wins when it parses; a missing, blank, or unparsable value must not
                    // hide a usable completed_at. If neither parses, the record is stored with the
                    // fallback instant but marked unreliable for identity purposes.
                    LegacyTimestamp timestamp =
                            DateTimeUtil.parseLegacyTimestamp(
                                    getString(map, "event_at", null),
                                    getString(map, "completed_at", null),
                                    zone,
                                    fallbackEventAt);
                    HistoryItem item =
                            new HistoryItem(
                                    null,
                                    name,
                                    priority,
                                    totalSeconds,
                                    remaining,
                                    eventType,
                                    completedManually,
                                    timestamp.instant());
                    String canonicalTimestamp = timestamp.canonicalLocalTimestamp().orElse(null);
                    candidates.add(
                            new LegacyHistoryCandidate(
                                    item,
                                    LegacyRecordSignature.forHistory(item, canonicalTimestamp),
                                    canonicalTimestamp));
                } catch (RuntimeException e) {
                    skippedCount++;
                    log.warn(
                            "Skipping malformed legacy history record in {}: {}",
                            file,
                            e.getMessage());
                }
            }

            ImportOutcome outcome =
                    legacyImportRepository.importBatch(
                            new ImportBatch(
                                    LegacyImportRepository.KIND_HISTORY,
                                    sourceHash,
                                    file.getName(),
                                    sourceBytes.length,
                                    List.of(),
                                    candidates));
            log.info(
                    "Migrated {} history items from legacy file {} ({} already present, {} invalid records skipped{})",
                    outcome.insertedHistory(),
                    file,
                    outcome.skippedHistory(),
                    skippedCount,
                    outcome.sourceAlreadyProcessed() ? ", source was already processed" : "");
            renameToMigrated(file);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse legacy history file {}: {}", file, e.getMessage());
        }
    }

    private void migrateGeometryFile(File file) {
        try {
            byte[] sourceBytes = Files.readAllBytes(file.toPath());
            String sourceHash = SqliteLegacyImportRepository.sha256(sourceBytes);
            if (skipAlreadyImported(file, LegacyImportRepository.KIND_GEOMETRY, sourceHash)) {
                return;
            }

            JsonNode node = objectMapper.readTree(sourceBytes);
            if (node.has("x") && node.has("y") && node.has("width") && node.has("height")) {
                double x = node.get("x").asDouble(100);
                double y = node.get("y").asDouble(100);
                double width = node.get("width").asDouble(340);
                double height = node.get("height").asDouble(560);
                // Apply first, record afterwards: a crash in between replays an identical
                // singleton value, whereas recording first could lose the value permanently.
                preferenceRepository.saveGeometry(new WindowGeometry(x, y, width, height));
                legacyImportRepository.markSourceProcessed(
                        LegacyImportRepository.KIND_GEOMETRY,
                        sourceHash,
                        file.getName(),
                        sourceBytes.length);
                log.info("Migrated legacy window geometry: [{}, {}, {}, {}]", x, y, width, height);
            } else {
                log.warn(
                        "Legacy geometry file {} has no x/y/width/height; nothing to import", file);
            }
            renameToMigrated(file);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse legacy geometry file {}: {}", file, e.getMessage());
        }
    }

    private void migrateSettingsFile(File file) {
        try {
            byte[] sourceBytes = Files.readAllBytes(file.toPath());
            String sourceHash = SqliteLegacyImportRepository.sha256(sourceBytes);
            if (skipAlreadyImported(file, LegacyImportRepository.KIND_SETTINGS, sourceHash)) {
                return;
            }

            JsonNode node = objectMapper.readTree(sourceBytes);
            ThemeMode theme = ThemeMode.DARK;
            if (node.has("theme")) {
                theme = ThemeMode.fromCode(node.get("theme").asText("dark"));
            }
            boolean alwaysOnTop = false;
            if (node.has("always_on_top") && !node.get("always_on_top").isNull()) {
                alwaysOnTop = node.get("always_on_top").asBoolean(false);
            }
            // Preserve already-stored preferences (language, sound); only legacy-controlled keys
            // are overwritten.
            UserPreferences existing = preferenceRepository.loadPreferences(false);
            preferenceRepository.savePreferences(
                    existing.withTheme(theme).withAlwaysOnTop(alwaysOnTop));
            legacyImportRepository.markSourceProcessed(
                    LegacyImportRepository.KIND_SETTINGS,
                    sourceHash,
                    file.getName(),
                    sourceBytes.length);
            log.info("Migrated legacy user settings: theme={}, alwaysOnTop={}", theme, alwaysOnTop);
            renameToMigrated(file);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse legacy settings file {}: {}", file, e.getMessage());
        }
    }

    /** True when the exact source was already imported; the source is then retired and skipped. */
    private boolean skipAlreadyImported(File file, String kind, String sourceHash) {
        if (!legacyImportRepository.isSourceProcessed(kind, sourceHash)) {
            return false;
        }
        log.info("Legacy {} source {} was already imported; skipping", kind, file);
        renameToMigrated(file);
        return true;
    }

    /**
     * Moves a processed file aside so it imports exactly once. Never overwrites an existing backup:
     * if {@code *.migrated} is already there (a restored backup, or a previous rename that could
     * not move the source) a numbered suffix is used. Leaving the source in place would instead
     * re-import it on every startup.
     */
    private void renameToMigrated(File file) {
        Path source = file.toPath();
        try {
            Path target = source.resolveSibling(source.getFileName() + ".migrated");
            int suffix = 1;
            while (Files.exists(target)) {
                target = source.resolveSibling(source.getFileName() + ".migrated." + suffix);
                suffix++;
            }
            Files.move(source, target);
            log.debug("Renamed {} to {}", source, target);
        } catch (IOException e) {
            log.warn("Could not rename migrated file {}: {}", file, e.getMessage());
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return defaultValue;
    }

    private boolean getBoolean(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim()) || "1".equals(text.trim());
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return false;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
