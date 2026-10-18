package io.github.marodriguezd.taskflow.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service managing operating system detection and platform-specific behavior (per-user data
 * directories, window styling conventions, defaults).
 */
public class PlatformService {

    private static final Logger log = LoggerFactory.getLogger(PlatformService.class);

    public enum OS {
        WINDOWS,
        LINUX,
        MACOS,
        OTHER
    }

    private final OS currentOs;
    private final Path dataDirectory;
    private final Path legacyDirectory;

    public PlatformService() {
        this(
                detectOs(),
                Paths.get(System.getProperty("user.home", "."), ".TaskFlow"),
                System::getenv,
                System.getProperty("user.home"),
                true);
        log.info(
                "Initialized PlatformService. OS: {}, Data directory: {}",
                currentOs,
                dataDirectory);
    }

    /** Test seam: explicit OS, legacy directory, environment lookup, and user home. */
    PlatformService(
            OS os,
            Path legacyDirectory,
            Function<String, String> envLookup,
            String userHome,
            boolean ensureDirectory) {
        this.currentOs = os != null ? os : detectOs();
        this.legacyDirectory =
                legacyDirectory != null
                        ? legacyDirectory
                        : Paths.get(
                                (userHome != null && !userHome.isBlank())
                                        ? userHome
                                        : System.getProperty("user.home", "."),
                                ".TaskFlow");
        this.dataDirectory =
                resolveDataDirectory(this.currentOs, this.legacyDirectory, envLookup, userHome);
        if (ensureDirectory) {
            ensureDirectoryExists(this.dataDirectory);
        }
    }

    public OS getCurrentOs() {
        return currentOs;
    }

    public boolean isWindows() {
        return currentOs == OS.WINDOWS;
    }

    public boolean isLinux() {
        return currentOs == OS.LINUX;
    }

    public boolean isMac() {
        return currentOs == OS.MACOS;
    }

    public boolean getDefaultAlwaysOnTop() {
        // Windows defaults to standard windowing; Linux/Wayland defaults to pinned/floating panel
        return !isWindows();
    }

    public boolean usesFramelessWindow() {
        // Floating liquid glass panel uses frameless transparent styling with custom drag header
        return true;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public Path getLegacyDirectory() {
        return legacyDirectory;
    }

    public Path getDatabasePath() {
        return dataDirectory.resolve("taskflow.db");
    }

    public Path getUserSoundPath() {
        return dataDirectory.resolve("bell.mp3");
    }

    static OS detectOs() {
        return detectOs(System.getProperty("os.name", ""));
    }

    static OS detectOs(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return OS.MACOS;
        } else if (normalized.contains("win")) {
            return OS.WINDOWS;
        } else if (normalized.contains("nix")
                || normalized.contains("nux")
                || normalized.contains("aix")) {
            return OS.LINUX;
        }
        return OS.OTHER;
    }

    private Path resolveDataDirectory() {
        return resolveDataDirectory(
                currentOs, legacyDirectory, System::getenv, System.getProperty("user.home"));
    }

    static Path resolveDataDirectory(
            OS os, Path legacyDirectory, Function<String, String> envLookup, String userHome) {
        // If legacy ~/.TaskFlow already exists, prioritize keeping data in the established location
        if (legacyDirectory != null
                && Files.exists(legacyDirectory)
                && Files.isDirectory(legacyDirectory)) {
            return legacyDirectory;
        }

        String safeHome =
                (userHome != null && !userHome.isBlank())
                        ? userHome
                        : System.getProperty("user.home", ".");
        return switch (os) {
            case WINDOWS -> {
                String appData = envLookup != null ? envLookup.apply("APPDATA") : null;
                Path validAppData = validateAndNormalizeDataHome(appData);
                yield (validAppData != null)
                        ? validAppData.resolve("TaskFlow")
                        : Paths.get(safeHome, ".TaskFlow");
            }
            case MACOS -> Paths.get(safeHome, "Library", "Application Support", "TaskFlow");
            case LINUX -> {
                String xdgData = envLookup != null ? envLookup.apply("XDG_DATA_HOME") : null;
                Path validXdgData = validateAndNormalizeDataHome(xdgData);
                yield (validXdgData != null)
                        ? validXdgData.resolve("TaskFlow")
                        : Paths.get(safeHome, ".TaskFlow");
            }
            case OTHER -> Paths.get(safeHome, ".TaskFlow");
        };
    }

    /**
     * Validates that an environment variable directory path is non-blank, absolute, and normalized.
     * If relative or invalid, logs a warning and returns {@code null} to trigger fallback to
     * ~/.TaskFlow per the XDG Base Directory specification.
     */
    static Path validateAndNormalizeDataHome(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        try {
            Path path = Paths.get(rawPath);
            if (!path.isAbsolute()) {
                log.warn("Ignored non-absolute environment directory path: {}", rawPath);
                return null;
            }
            return path.normalize();
        } catch (Exception e) {
            log.warn("Ignored invalid environment directory path: {}", rawPath, e);
            return null;
        }
    }

    private void ensureDirectoryExists(Path dir) {
        try {
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (Exception e) {
            log.error("Failed to create application data directory: {}", dir, e);
        }
    }
}
