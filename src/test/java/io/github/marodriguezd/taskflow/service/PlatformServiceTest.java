package io.github.marodriguezd.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlatformServiceTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("Initializes data directory and detects current platform")
    void testPlatformService() {
        PlatformService platformService = new PlatformService();

        assertThat(platformService.getCurrentOs()).isNotNull();
        assertThat(platformService.getDataDirectory()).isNotNull();
        assertThat(platformService.getDatabasePath()).isNotNull();
        assertThat(platformService.getUserSoundPath()).isNotNull();

        if (platformService.isWindows()) {
            assertThat(platformService.getDefaultAlwaysOnTop()).isFalse();
            assertThat(platformService.usesFramelessWindow()).isTrue();
        } else if (platformService.isLinux()) {
            assertThat(platformService.getDefaultAlwaysOnTop()).isTrue();
            assertThat(platformService.usesFramelessWindow()).isTrue();
        }
    }

    @Test
    @DisplayName("Detects OS from operating system name string accurately")
    void detectsOsFromSystemName() {
        assertThat(PlatformService.detectOs("Windows 11")).isEqualTo(PlatformService.OS.WINDOWS);
        assertThat(PlatformService.detectOs("Windows Server 2022"))
                .isEqualTo(PlatformService.OS.WINDOWS);
        assertThat(PlatformService.detectOs("Mac OS X")).isEqualTo(PlatformService.OS.MACOS);
        assertThat(PlatformService.detectOs("Darwin")).isEqualTo(PlatformService.OS.MACOS);
        assertThat(PlatformService.detectOs("Linux")).isEqualTo(PlatformService.OS.LINUX);
        assertThat(PlatformService.detectOs("AIX")).isEqualTo(PlatformService.OS.LINUX);
        assertThat(PlatformService.detectOs("FreeBSD")).isEqualTo(PlatformService.OS.OTHER);
        assertThat(PlatformService.detectOs(null)).isEqualTo(PlatformService.OS.OTHER);
        assertThat(PlatformService.detectOs("")).isEqualTo(PlatformService.OS.OTHER);
    }

    @Test
    @DisplayName("Validates and normalizes environment directory paths")
    void validatesAndNormalizesDataHome() {
        Path validAbsolute = tempDir.toAbsolutePath();
        assertThat(PlatformService.validateAndNormalizeDataHome(validAbsolute.toString()))
                .isEqualTo(validAbsolute);

        // Path with redundant components is normalized
        Path unnormalized = validAbsolute.resolve("sub/../sub");
        assertThat(PlatformService.validateAndNormalizeDataHome(unnormalized.toString()))
                .isEqualTo(validAbsolute.resolve("sub"));

        // Relative paths must be rejected and return null
        assertThat(PlatformService.validateAndNormalizeDataHome("relative/path")).isNull();
        assertThat(PlatformService.validateAndNormalizeDataHome(".local/share")).isNull();
        assertThat(PlatformService.validateAndNormalizeDataHome("")).isNull();
        assertThat(PlatformService.validateAndNormalizeDataHome("   ")).isNull();
        assertThat(PlatformService.validateAndNormalizeDataHome(null)).isNull();
        assertThat(PlatformService.validateAndNormalizeDataHome("invalid\0path")).isNull();
    }

    @Test
    @DisplayName(
            "Linux resolves XDG_DATA_HOME if absolute and falls back to user home if relative or unset")
    void linuxDataDirectoryResolution() {
        Path customData = tempDir.resolve("custom_share");
        Path userHome = tempDir.resolve("home");
        Path nonExistentLegacy = tempDir.resolve("nonexistent_legacy");

        // 1. Valid absolute path
        Path resolvedValid =
                PlatformService.resolveDataDirectory(
                        PlatformService.OS.LINUX,
                        nonExistentLegacy,
                        env -> customData.toAbsolutePath().toString(),
                        userHome.toString());
        assertThat(resolvedValid).isEqualTo(customData.toAbsolutePath().resolve("TaskFlow"));

        // 2. Relative path rejected per XDG Base Directory specification -> falls back to
        // ~/.TaskFlow
        Path resolvedRelative =
                PlatformService.resolveDataDirectory(
                        PlatformService.OS.LINUX,
                        nonExistentLegacy,
                        env -> "relative/share",
                        userHome.toString());
        assertThat(resolvedRelative).isEqualTo(userHome.resolve(".TaskFlow"));

        // 3. Unset / blank path -> falls back to ~/.TaskFlow
        Path resolvedUnset =
                PlatformService.resolveDataDirectory(
                        PlatformService.OS.LINUX,
                        nonExistentLegacy,
                        env -> "",
                        userHome.toString());
        assertThat(resolvedUnset).isEqualTo(userHome.resolve(".TaskFlow"));
    }

    @Test
    @DisplayName(
            "Windows resolves APPDATA if valid and falls back to user home if relative or unset")
    void windowsDataDirectoryResolution() {
        Path userHome = tempDir.resolve("home");
        Path nonExistentLegacy = tempDir.resolve("nonexistent_legacy");

        // Relative path rejected -> falls back to ~/.TaskFlow
        Path resolvedRelative =
                PlatformService.resolveDataDirectory(
                        PlatformService.OS.WINDOWS,
                        nonExistentLegacy,
                        env -> "AppData\\Roaming",
                        userHome.toString());
        assertThat(resolvedRelative).isEqualTo(userHome.resolve(".TaskFlow"));

        // Blank -> falls back to ~/.TaskFlow
        Path resolvedBlank =
                PlatformService.resolveDataDirectory(
                        PlatformService.OS.WINDOWS,
                        nonExistentLegacy,
                        env -> "   ",
                        userHome.toString());
        assertThat(resolvedBlank).isEqualTo(userHome.resolve(".TaskFlow"));
    }

    @Test
    @DisplayName(
            "Existing legacy ~/.TaskFlow directory takes precedence over environment variables")
    void legacyDirectoryTakesPrecedence() throws IOException {
        Path legacyDir = tempDir.resolve(".TaskFlow");
        Files.createDirectories(legacyDir);
        Path customData = tempDir.resolve("custom_xdg");

        Path resolved =
                PlatformService.resolveDataDirectory(
                        PlatformService.OS.LINUX,
                        legacyDir,
                        env -> customData.toAbsolutePath().toString(),
                        tempDir.toString());

        assertThat(resolved).isEqualTo(legacyDir);
    }

    @Test
    @DisplayName("macOS and Other OS resolve to standard platform paths")
    void macOSAndOtherResolution() {
        Path userHome = tempDir.resolve("home");
        Path nonExistentLegacy = tempDir.resolve("nonexistent_legacy");

        Path macResolved =
                PlatformService.resolveDataDirectory(
                        PlatformService.OS.MACOS,
                        nonExistentLegacy,
                        env -> null,
                        userHome.toString());
        assertThat(macResolved).isEqualTo(userHome.resolve("Library/Application Support/TaskFlow"));

        Path otherResolved =
                PlatformService.resolveDataDirectory(
                        PlatformService.OS.OTHER,
                        nonExistentLegacy,
                        env -> null,
                        userHome.toString());
        assertThat(otherResolved).isEqualTo(userHome.resolve(".TaskFlow"));
    }

    @Test
    @DisplayName("PlatformService constructor seam verifies OS-specific defaults across platforms")
    void platformDefaultsAcrossOsTypes() {
        PlatformService winService =
                new PlatformService(
                        PlatformService.OS.WINDOWS,
                        tempDir.resolve("legacy"),
                        env -> null,
                        tempDir.toString(),
                        false);
        assertThat(winService.isWindows()).isTrue();
        assertThat(winService.getDefaultAlwaysOnTop()).isFalse();
        assertThat(winService.usesFramelessWindow()).isTrue();

        PlatformService linuxService =
                new PlatformService(
                        PlatformService.OS.LINUX,
                        tempDir.resolve("legacy"),
                        env -> null,
                        tempDir.toString(),
                        false);
        assertThat(linuxService.isLinux()).isTrue();
        assertThat(linuxService.getDefaultAlwaysOnTop()).isTrue();
        assertThat(linuxService.usesFramelessWindow()).isTrue();

        PlatformService macService =
                new PlatformService(
                        PlatformService.OS.MACOS,
                        tempDir.resolve("legacy"),
                        env -> null,
                        tempDir.toString(),
                        false);
        assertThat(macService.isMac()).isTrue();
        assertThat(macService.getDefaultAlwaysOnTop()).isTrue();
        assertThat(macService.usesFramelessWindow()).isTrue();
    }
}
