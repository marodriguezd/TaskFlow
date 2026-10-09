package io.github.marodriguezd.taskflow.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
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
        this.currentOs = detectOs();
        this.legacyDirectory = Paths.get(System.getProperty("user.home"), ".TaskFlow");
        this.dataDirectory = resolveDataDirectory();
        ensureDirectoryExists(dataDirectory);
        log.info(
                "Initialized PlatformService. OS: {}, Data directory: {}",
                currentOs,
                dataDirectory);
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

    private OS detectOs() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return OS.WINDOWS;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return OS.MACOS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return OS.LINUX;
        }
        return OS.OTHER;
    }

    private Path resolveDataDirectory() {
        // If legacy ~/.TaskFlow already exists, prioritize keeping data in the established location
        if (Files.exists(legacyDirectory) && Files.isDirectory(legacyDirectory)) {
            return legacyDirectory;
        }

        String userHome = System.getProperty("user.home");
        return switch (currentOs) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                yield (appData != null && !appData.isBlank())
                        ? Paths.get(appData, "TaskFlow")
                        : Paths.get(userHome, ".TaskFlow");
            }
            case MACOS -> Paths.get(userHome, "Library", "Application Support", "TaskFlow");
            case LINUX -> {
                String xdgData = System.getenv("XDG_DATA_HOME");
                yield (xdgData != null && !xdgData.isBlank())
                        ? Paths.get(xdgData, "TaskFlow")
                        : Paths.get(userHome, ".TaskFlow");
            }
            case OTHER -> Paths.get(userHome, ".TaskFlow");
        };
    }

    private void ensureDirectoryExists(Path dir) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            log.error("Failed to create application data directory: {}", dir, e);
        }
    }
}
