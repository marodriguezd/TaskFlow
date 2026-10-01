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
            int migratedCount = 0;
            for (Map<String, Object> map : list) {
                String name = (String) map.getOrDefault("name", "Task");
                if (name == null || name.isBlank()) {
                    continue;
                }
                String priorityStr = (String) map.getOrDefault("priority", "Media");
                Priority priority = Priority.fromDisplayName(priorityStr);
                int totalSeconds = getInt(map, "total_seconds", 1500);
                int remaining = getInt(map, "remaining", totalSeconds);

                Task task =
                        Task.create(name, priority, totalSeconds).withRemainingSeconds(remaining);
                taskRepository.save(task);
                migratedCount++;
            }
            log.info("Migrated {} active tasks from legacy file {}", migratedCount, file);
            renameToMigrated(file);
        } catch (IOException e) {
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
            int migratedCount = 0;
            for (Map<String, Object> map : list) {
                String name = (String) map.getOrDefault("name", "Completed task");
                if (name == null || name.isBlank()) {
                    continue;
                }
                String priorityStr = (String) map.getOrDefault("priority", "Media");
                Priority priority = Priority.fromDisplayName(priorityStr);
                int totalSeconds = getInt(map, "total_seconds", 1500);
                int remaining = getInt(map, "remaining", 0);
                String eventCode = (String) map.getOrDefault("history_event", "completed");
                HistoryEventType eventType = HistoryEventType.fromCode(eventCode);
                boolean completedManually = Boolean.TRUE.equals(map.get("completed_manually"));

                String dateStr = (String) map.getOrDefault("event_at", map.get("completed_at"));
                Instant eventAt = DateTimeUtil.parseLegacyOrDefault(dateStr, Instant.now());

                HistoryItem item =
                        new HistoryItem(
                                null,
                                name,
                                priority,
                                totalSeconds,
                                remaining,
                                eventType,
                                completedManually,
                                eventAt);
                historyRepository.save(item);
                migratedCount++;
            }
            log.info("Migrated {} history items from legacy file {}", migratedCount, file);
            renameToMigrated(file);
        } catch (IOException e) {
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
            }
            renameToMigrated(file);
        } catch (IOException e) {
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
            preferenceRepository.savePreferences(new UserPreferences(theme, alwaysOnTop, true));
            log.info("Migrated legacy user settings: theme={}, alwaysOnTop={}", theme, alwaysOnTop);
            renameToMigrated(file);
        } catch (IOException e) {
            log.warn("Failed to parse legacy settings file {}: {}", file, e.getMessage());
        }
    }

    private void renameToMigrated(File file) {
        try {
            File backup = new File(file.getAbsolutePath() + ".migrated");
            if (!backup.exists()) {
                boolean renamed = file.renameTo(backup);
                if (renamed) {
                    log.debug("Renamed {} to {}", file, backup);
                }
            }
        } catch (Exception e) {
            log.warn("Could not rename migrated file {}: {}", file, e.getMessage());
        }
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
