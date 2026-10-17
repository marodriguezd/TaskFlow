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
import io.github.marodriguezd.taskflow.util.DateTimeUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migrates legacy TaskFlow JSON files (Python/PyQt6 implementation) to SQLite.
 *
 * <p>Legacy task and history records have no stable identifier, so the same logical record can be
 * seen more than once: the file may exist in both supported locations, a rename may have failed, or
 * a backup may have been restored. Every discovered source is therefore imported and records are
 * merged by content identity rather than by "the destination table is non-empty, skip this file". A
 * candidate is skipped only while an identical row still exists in the destination table; identity
 * counts are consumed as a multiset, so distinct records — including repeated identical entries
 * within one file — are never collapsed.
 *
 * <p>Known ambiguity: a task created inside the Java application with exactly the same name,
 * priority and durations as a legacy record is indistinguishable from that legacy record. In that
 * case one legacy occurrence is treated as already present instead of being duplicated.
 */
public class LegacyDataMigrator {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataMigrator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Path dataDirectory;
    private final Path legacyHomeDirectory;
    private final TaskRepository taskRepository;
    private final HistoryRepository historyRepository;
    private final PreferenceRepository preferenceRepository;

    public LegacyDataMigrator(
            Path dataDirectory,
            TaskRepository taskRepository,
            HistoryRepository historyRepository,
            PreferenceRepository preferenceRepository) {
        this(
                dataDirectory,
                Paths.get(System.getProperty("user.home")),
                taskRepository,
                historyRepository,
                preferenceRepository);
    }

    /** Test seam so migration tests never read the developer's real home directory. */
    LegacyDataMigrator(
            Path dataDirectory,
            Path legacyHomeDirectory,
            TaskRepository taskRepository,
            HistoryRepository historyRepository,
            PreferenceRepository preferenceRepository) {
        this.dataDirectory = dataDirectory;
        this.legacyHomeDirectory = legacyHomeDirectory;
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.preferenceRepository = preferenceRepository;
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
            List<Map<String, Object>> list = objectMapper.readValue(file, new TypeReference<>() {});
            Map<TaskIdentity, Integer> alreadyStored = existingTaskIdentities();
            List<Task> pending = new ArrayList<>();
            int skippedCount = 0;
            int duplicateCount = 0;
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
                    TaskIdentity identity = TaskIdentity.of(task);
                    int stored = alreadyStored.getOrDefault(identity, 0);
                    if (stored > 0) {
                        alreadyStored.put(identity, stored - 1);
                        duplicateCount++;
                        continue;
                    }
                    pending.add(task);
                } catch (RuntimeException e) {
                    // Isolate malformed records: aborting here would leave the file unimported
                    // while the rows already inserted make every later startup skip it.
                    skippedCount++;
                    log.warn(
                            "Skipping malformed legacy task record in {}: {}",
                            file,
                            e.getMessage());
                }
            }
            // One transaction for the whole file: an interrupted import leaves no rows behind, so
            // the next startup retries the file instead of skipping it as "already imported".
            taskRepository.saveAll(pending);
            log.info(
                    "Migrated {} active tasks from legacy file {} ({} already present, {} invalid records skipped)",
                    pending.size(),
                    file,
                    duplicateCount,
                    skippedCount);
            renameToMigrated(file);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse legacy tasks file {}: {}", file, e.getMessage());
        }
    }

    private void migrateHistoryFile(File file) {
        try {
            List<Map<String, Object>> list = objectMapper.readValue(file, new TypeReference<>() {});
            List<ParsedHistory> parsed = new ArrayList<>();
            int skippedCount = 0;
            // One fallback for the whole file; it is only a stored value, never a match identity.
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

                    String dateStr = getString(map, "event_at", null);
                    if (dateStr == null) {
                        dateStr = getString(map, "completed_at", null);
                    }
                    // Missing, blank, and unparsable timestamps are all unreliable: they get a
                    // display fallback but must not be treated as exact identities.
                    Optional<Instant> sourceEventAt = DateTimeUtil.parseLegacy(dateStr);

                    HistoryItem item =
                            new HistoryItem(
                                    null,
                                    name,
                                    priority,
                                    totalSeconds,
                                    remaining,
                                    eventType,
                                    completedManually,
                                    sourceEventAt.orElse(fallbackEventAt));
                    parsed.add(new ParsedHistory(item, sourceEventAt.isPresent()));
                } catch (RuntimeException e) {
                    skippedCount++;
                    log.warn(
                            "Skipping malformed legacy history record in {}: {}",
                            file,
                            e.getMessage());
                }
            }

            // Reserve exact timestamp matches first: an untimestamped record earlier in the file
            // must not consume a row needed by a timestamped record that follows it.
            HistoryBaseline alreadyStored = HistoryBaseline.from(historyRepository.findAll());
            List<ParsedHistory> unmatched = new ArrayList<>();
            int duplicateCount = 0;
            for (ParsedHistory record : parsed) {
                if (record.hasReliableTimestamp() && alreadyStored.consumeExact(record.item())) {
                    duplicateCount++;
                } else {
                    unmatched.add(record);
                }
            }
            List<HistoryItem> pending = new ArrayList<>();
            for (ParsedHistory record : unmatched) {
                if (!record.hasReliableTimestamp()
                        && alreadyStored.consumeByContent(record.item())) {
                    duplicateCount++;
                } else {
                    pending.add(record.item());
                }
            }

            historyRepository.saveAll(pending);
            log.info(
                    "Migrated {} history items from legacy file {} ({} already present, {} invalid records skipped)",
                    pending.size(),
                    file,
                    duplicateCount,
                    skippedCount);
            renameToMigrated(file);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse legacy history file {}: {}", file, e.getMessage());
        }
    }

    private void migrateGeometryFile(File file) {
        try {
            JsonNode node = objectMapper.readTree(file);
            if (node.has("x") && node.has("y") && node.has("width") && node.has("height")) {
                double x = node.get("x").asDouble(100);
                double y = node.get("y").asDouble(100);
                double width = node.get("width").asDouble(340);
                double height = node.get("height").asDouble(560);
                preferenceRepository.saveGeometry(new WindowGeometry(x, y, width, height));
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
            JsonNode node = objectMapper.readTree(file);
            ThemeMode theme = ThemeMode.DARK;
            if (node.has("theme")) {
                theme = ThemeMode.fromCode(node.get("theme").asText("dark"));
            }
            boolean alwaysOnTop = false;
            if (node.has("always_on_top") && !node.get("always_on_top").isNull()) {
                alwaysOnTop = node.get("always_on_top").asBoolean(false);
            }
            // Preserve already-stored preferences (language, sound); only legacy-controlled
            // keys are overwritten. Fresh databases load defaults, matching prior behavior.
            UserPreferences existing = preferenceRepository.loadPreferences(false);
            preferenceRepository.savePreferences(
                    existing.withTheme(theme).withAlwaysOnTop(alwaysOnTop));
            log.info("Migrated legacy user settings: theme={}, alwaysOnTop={}", theme, alwaysOnTop);
            renameToMigrated(file);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse legacy settings file {}: {}", file, e.getMessage());
        }
    }

    private Map<TaskIdentity, Integer> existingTaskIdentities() {
        Map<TaskIdentity, Integer> counts = new HashMap<>();
        for (Task task : taskRepository.findAll()) {
            counts.merge(TaskIdentity.of(task), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Moves a processed file aside so it imports exactly once. Never overwrites an existing backup:
     * if {@code *.migrated} is already there (a restored backup, or a previous rename that could
     * not move the source) a numbered suffix is used. Leaving the source in place would instead
     * re-import it on every startup and silently overwrite newer preferences.
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

    /** Legacy tasks have no identifier; content is the only available identity. */
    private record TaskIdentity(
            String name, Priority priority, int totalSeconds, int remainingSeconds) {

        static TaskIdentity of(Task task) {
            return new TaskIdentity(
                    task.name(), task.priority(), task.totalSeconds(), task.remainingSeconds());
        }
    }

    /** History identity without the event time, used for records that carry no timestamp. */
    private record HistoryIdentity(
            String name,
            Priority priority,
            int totalSeconds,
            int remainingSeconds,
            HistoryEventType eventType,
            boolean completedManually) {

        static HistoryIdentity of(HistoryItem item) {
            return new HistoryIdentity(
                    item.name(),
                    item.priority(),
                    item.totalSeconds(),
                    item.remainingSeconds(),
                    item.eventType(),
                    item.completedManually());
        }
    }

    /** A parsed history record plus whether its source timestamp could actually be parsed. */
    private record ParsedHistory(HistoryItem item, boolean hasReliableTimestamp) {}

    /** Exact identity of an existing history row, including its event time. */
    private record HistoryExactKey(HistoryIdentity identity, Instant eventAt) {}

    /**
     * Consumable snapshot of the history rows that already exist. Exact timestamp matches are
     * consumed in a first pass, so untimestamped records can only use the content fallback
     * afterwards; the outcome therefore does not depend on the order of records in the source file.
     * Each existing row is consumed at most once, preserving duplicate entries.
     */
    private static final class HistoryBaseline {

        private final Map<HistoryExactKey, Integer> exactCounts = new HashMap<>();
        private final Map<HistoryIdentity, Integer> contentCounts = new HashMap<>();

        static HistoryBaseline from(List<HistoryItem> items) {
            HistoryBaseline baseline = new HistoryBaseline();
            for (HistoryItem item : items) {
                HistoryIdentity identity = HistoryIdentity.of(item);
                baseline.contentCounts.merge(identity, 1, Integer::sum);
                baseline.exactCounts.merge(
                        new HistoryExactKey(identity, item.eventAt()), 1, Integer::sum);
            }
            return baseline;
        }

        boolean consumeExact(HistoryItem item) {
            HistoryIdentity identity = HistoryIdentity.of(item);
            HistoryExactKey key = new HistoryExactKey(identity, item.eventAt());
            int available = exactCounts.getOrDefault(key, 0);
            if (available == 0) {
                return false;
            }
            exactCounts.put(key, available - 1);
            // The exact row is also one of the content matches, so an untimestamped record later
            // in the file must not reuse it.
            contentCounts.computeIfPresent(identity, (ignored, count) -> count - 1);
            return true;
        }

        boolean consumeByContent(HistoryItem item) {
            HistoryIdentity identity = HistoryIdentity.of(item);
            int available = contentCounts.getOrDefault(identity, 0);
            if (available == 0) {
                return false;
            }
            contentCounts.put(identity, available - 1);
            return true;
        }
    }
}
