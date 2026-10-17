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
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Migrates legacy TaskFlow JSON files (Python/PyQt6 implementation) to SQLite. */
public class LegacyDataMigrator {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataMigrator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Path dataDirectory;
    private final TaskRepository taskRepository;
    private final HistoryRepository historyRepository;
    private final PreferenceRepository preferenceRepository;

    public LegacyDataMigrator(
            Path dataDirectory,
            TaskRepository taskRepository,
            HistoryRepository historyRepository,
            PreferenceRepository preferenceRepository) {
        this.dataDirectory = dataDirectory;
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
        String userHome = System.getProperty("user.home");
        Path homeData = Paths.get(userHome, ".taskflow_data.json");
        Path homeHistory = Paths.get(userHome, ".taskflow_history.json");
        Path homeGeometry = Paths.get(userHome, ".taskflow_geometry.json");

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
        if (taskRepository.count() > 0) {
            log.info("Active tasks already exist in database; skipping import from {}", file);
            renameToMigrated(file);
            return;
        }

        try {
            List<Map<String, Object>> list = objectMapper.readValue(file, new TypeReference<>() {});
            List<Task> pending = new ArrayList<>();
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

                    pending.add(
                            Task.create(name, priority, totalSeconds)
                                    .withRemainingSeconds(remaining));
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
                    "Migrated {} active tasks from legacy file {} ({} invalid records skipped)",
                    pending.size(),
                    file,
                    skippedCount);
            renameToMigrated(file);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse legacy tasks file {}: {}", file, e.getMessage());
        }
    }

    private void migrateHistoryFile(File file) {
        if (historyRepository.count() > 0) {
            log.info("History records already exist in database; skipping import from {}", file);
            renameToMigrated(file);
            return;
        }

        try {
            List<Map<String, Object>> list = objectMapper.readValue(file, new TypeReference<>() {});
            List<HistoryItem> pending = new ArrayList<>();
            int skippedCount = 0;
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
                    Instant eventAt = DateTimeUtil.parseLegacyOrDefault(dateStr, Instant.now());

                    pending.add(
                            new HistoryItem(
                                    null,
                                    name,
                                    priority,
                                    totalSeconds,
                                    remaining,
                                    eventType,
                                    completedManually,
                                    eventAt));
                } catch (RuntimeException e) {
                    skippedCount++;
                    log.warn(
                            "Skipping malformed legacy history record in {}: {}",
                            file,
                            e.getMessage());
                }
            }
            historyRepository.saveAll(pending);
            log.info(
                    "Migrated {} history items from legacy file {} ({} invalid records skipped)",
                    pending.size(),
                    file,
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
}
