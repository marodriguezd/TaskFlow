package io.github.marodriguezd.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import io.github.marodriguezd.taskflow.persistence.DatabaseManager;
import io.github.marodriguezd.taskflow.persistence.PersistenceException;
import io.github.marodriguezd.taskflow.persistence.SqlitePreferenceRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmpiricalStressM1Test {

    @Nested
    @DisplayName("1. Path Traversal & XDG Compliance Empirical Tests")
    class PathSecurityAndXdgTests {

        @TempDir Path tempDir;

        @ParameterizedTest(name = "Relative path rejected: \"{0}\"")
        @ValueSource(
                strings = {
                    "foo",
                    "../../data",
                    "./bar",
                    "../",
                    "~/.local/share",
                    "AppData\\Roaming",
                    "..\\AppData",
                    "subdir/path",
                    ".TaskFlow",
                    "   relative/with/spaces   "
                })
        void relativePathsAreRejected(String relativePath) {
            Path result = PlatformService.validateAndNormalizeDataHome(relativePath);
            assertThat(result)
                    .as("Relative path should be rejected and return null: " + relativePath)
                    .isNull();
        }

        @ParameterizedTest(name = "Blank or null path rejected: \"{0}\"")
        @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n"})
        void blankPathsAreRejected(String blankPath) {
            assertThat(PlatformService.validateAndNormalizeDataHome(blankPath)).isNull();
        }

        @Test
        @DisplayName("Null raw path returns null")
        void nullPathReturnsNull() {
            assertThat(PlatformService.validateAndNormalizeDataHome(null)).isNull();
        }

        @Test
        @DisplayName("Paths with invalid characters (e.g. null bytes) return null without crashing")
        void invalidCharactersReturnNull() {
            // Null bytes cause InvalidPathException in Java NIO
            assertThat(PlatformService.validateAndNormalizeDataHome("/tmp/invalid\0path")).isNull();
            assertThat(PlatformService.validateAndNormalizeDataHome("\0")).isNull();
        }

        @Test
        @DisplayName("Valid absolute path with spaces is accepted and normalized")
        void absolutePathWithSpacesAccepted() {
            Path customDir = tempDir.resolve("path with spaces/custom data");
            String raw = customDir.toAbsolutePath().toString();

            Path validated = PlatformService.validateAndNormalizeDataHome(raw);
            assertThat(validated).isNotNull();
            assertThat(validated).isEqualTo(customDir.toAbsolutePath().normalize());
        }

        @Test
        @DisplayName("Absolute path with redundant traversal components normalizes safely")
        void absolutePathWithTraversalNormalizes() {
            Path customDir = tempDir.resolve("a/b/../../c");
            String raw = customDir.toAbsolutePath().toString();

            Path validated = PlatformService.validateAndNormalizeDataHome(raw);
            assertThat(validated).isNotNull();
            assertThat(validated).isEqualTo(tempDir.resolve("c").toAbsolutePath().normalize());
            assertThat(validated.toString()).doesNotContain("..");
        }

        @ParameterizedTest(name = "Linux XDG fallback for invalid env: \"{0}\"")
        @ValueSource(
                strings = {
                    "foo",
                    "../../data",
                    "./bar",
                    "   ",
                    "/tmp/invalid\0path",
                    "AppData\\Roaming"
                })
        void linuxXdgFallsBackToUserHome(String invalidEnv) {
            Path userHome = tempDir.resolve("home");
            Path nonExistentLegacy = tempDir.resolve("nonexistent_legacy");

            Path resolved =
                    PlatformService.resolveDataDirectory(
                            PlatformService.OS.LINUX,
                            nonExistentLegacy,
                            env -> invalidEnv,
                            userHome.toString());

            assertThat(resolved)
                    .as("Must reliably fall back to ~/.TaskFlow under user home")
                    .isEqualTo(userHome.resolve(".TaskFlow"));
        }

        @Test
        @DisplayName("Linux valid absolute XDG_DATA_HOME with spaces resolves to TaskFlow subdir")
        void linuxValidXdgWithSpacesResolvesSubdir() {
            Path userHome = tempDir.resolve("home");
            Path nonExistentLegacy = tempDir.resolve("nonexistent_legacy");
            Path xdgDir = tempDir.resolve("My Custom Data/Share");

            Path resolved =
                    PlatformService.resolveDataDirectory(
                            PlatformService.OS.LINUX,
                            nonExistentLegacy,
                            env -> xdgDir.toAbsolutePath().toString(),
                            userHome.toString());

            assertThat(resolved).isEqualTo(xdgDir.toAbsolutePath().resolve("TaskFlow"));
        }

        @Test
        @DisplayName(
                "Existing legacy directory always takes precedence regardless of XDG_DATA_HOME")
        void existingLegacyDirectoryTakesPrecedence() throws IOException {
            Path legacyDir = tempDir.resolve(".TaskFlow");
            Files.createDirectories(legacyDir);
            Path validXdg = tempDir.resolve("valid_xdg");

            Path resolved =
                    PlatformService.resolveDataDirectory(
                            PlatformService.OS.LINUX,
                            legacyDir,
                            env -> validXdg.toAbsolutePath().toString(),
                            tempDir.toString());

            assertThat(resolved).isEqualTo(legacyDir);
        }

        @Test
        @DisplayName("Null or blank userHome falls back to System user.home safely")
        void nullOrBlankUserHomeFallback() {
            Path nonExistentLegacy = tempDir.resolve("nonexistent_legacy");
            Path resolvedNullHome =
                    PlatformService.resolveDataDirectory(
                            PlatformService.OS.LINUX,
                            nonExistentLegacy,
                            env -> "foo", // relative -> fallback
                            null);

            Path expectedDefault = Paths.get(System.getProperty("user.home", "."), ".TaskFlow");
            assertThat(resolvedNullHome).isEqualTo(expectedDefault);

            Path resolvedBlankHome =
                    PlatformService.resolveDataDirectory(
                            PlatformService.OS.LINUX, nonExistentLegacy, env -> "foo", "   ");
            assertThat(resolvedBlankHome).isEqualTo(expectedDefault);
        }
    }

    @Nested
    @DisplayName("2. Statement Batching & Atomic Rollback Empirical Tests")
    class StatementBatchingTests {

        @Test
        @DisplayName("Trigger abort on any batch item rolls back entire geometry write on fresh DB")
        void batchInsertRollbackOnTriggerAbort() throws Exception {
            String[] targetKeys = {"geo_x", "geo_y", "geo_width", "geo_height"};

            for (String failKey : targetKeys) {
                try (DatabaseManager db = DatabaseManager.inMemory()) {
                    SqlitePreferenceRepository repo = new SqlitePreferenceRepository(db);

                    // Install trigger to abort on the specific key
                    try (Connection conn = db.getConnection();
                            Statement stmt = conn.createStatement()) {
                        stmt.execute(
                                "CREATE TRIGGER fail_key_"
                                        + failKey
                                        + " BEFORE INSERT ON preferences "
                                        + "WHEN NEW.pref_key = '"
                                        + failKey
                                        + "' "
                                        + "BEGIN SELECT RAISE(ABORT, 'simulated failure on "
                                        + failKey
                                        + "'); END;");
                    }

                    // Attempt saveGeometry
                    assertThatThrownBy(
                                    () ->
                                            repo.saveGeometry(
                                                    new WindowGeometry(100.0, 200.0, 300.0, 400.0)))
                            .as("saveGeometry should fail when " + failKey + " triggers abort")
                            .isInstanceOf(PersistenceException.class);

                    // Verify NOTHING was written to preferences table
                    try (Connection conn = db.getConnection();
                            Statement stmt = conn.createStatement();
                            ResultSet rs =
                                    stmt.executeQuery(
                                            "SELECT COUNT(*) FROM preferences WHERE pref_key LIKE 'geo_%'")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1))
                                .as("Zero geometry rows should exist after rollback on " + failKey)
                                .isZero();
                    }
                    assertThat(repo.loadGeometry()).isEmpty();
                }
            }
        }

        @Test
        @DisplayName(
                "Trigger abort on UPDATE rolls back to exact pre-existing values without partial overwrite")
        void batchUpdateRollbackPreservesExistingGeometry() throws Exception {
            String[] targetKeys = {"geo_x", "geo_y", "geo_width", "geo_height"};

            for (String failKey : targetKeys) {
                try (DatabaseManager db = DatabaseManager.inMemory()) {
                    SqlitePreferenceRepository repo = new SqlitePreferenceRepository(db);

                    // Initial valid geometry
                    WindowGeometry initial = new WindowGeometry(111.0, 222.0, 333.0, 444.0);
                    repo.saveGeometry(initial);
                    assertThat(repo.loadGeometry()).contains(initial);

                    // Install trigger on UPDATE of the specific key
                    try (Connection conn = db.getConnection();
                            Statement stmt = conn.createStatement()) {
                        stmt.execute(
                                "CREATE TRIGGER fail_update_"
                                        + failKey
                                        + " BEFORE UPDATE ON preferences "
                                        + "WHEN NEW.pref_key = '"
                                        + failKey
                                        + "' "
                                        + "BEGIN SELECT RAISE(ABORT, 'simulated update failure on "
                                        + failKey
                                        + "'); END;");
                    }

                    // Attempt update with all new values
                    WindowGeometry updated = new WindowGeometry(999.0, 888.0, 777.0, 666.0);
                    assertThatThrownBy(() -> repo.saveGeometry(updated))
                            .as("saveGeometry update should fail on " + failKey)
                            .isInstanceOf(PersistenceException.class);

                    // Verify all 4 keys retain their exact original values
                    WindowGeometry loadedAfterFail = repo.loadGeometry().orElseThrow();
                    assertThat(loadedAfterFail.x())
                            .as("geo_x should not be corrupted when " + failKey + " fails")
                            .isEqualTo(111.0);
                    assertThat(loadedAfterFail.y())
                            .as("geo_y should not be corrupted when " + failKey + " fails")
                            .isEqualTo(222.0);
                    assertThat(loadedAfterFail.width())
                            .as("geo_width should not be corrupted when " + failKey + " fails")
                            .isEqualTo(333.0);
                    assertThat(loadedAfterFail.height())
                            .as("geo_height should not be corrupted when " + failKey + " fails")
                            .isEqualTo(444.0);
                }
            }
        }

        @Test
        @DisplayName("Batch save succeeds and loads extreme geometry values")
        void batchSaveExtremeGeometryValues() {
            try (DatabaseManager db = DatabaseManager.inMemory()) {
                SqlitePreferenceRepository repo = new SqlitePreferenceRepository(db);

                WindowGeometry extreme = new WindowGeometry(-9999.5, -8888.25, 0.0, 1000000.75);
                repo.saveGeometry(extreme);

                WindowGeometry loaded = repo.loadGeometry().orElseThrow();
                assertThat(loaded.x()).isEqualTo(-9999.5);
                assertThat(loaded.y()).isEqualTo(-8888.25);
                // WindowGeometry constructor clamps width to min 300
                assertThat(loaded.width()).isEqualTo(300.0);
                assertThat(loaded.height()).isEqualTo(1000000.75);
            }
        }
    }

    @Nested
    @DisplayName("3. Sound Service Bare Relative Paths Empirical Tests")
    class SoundServiceBarePathTests {

        @Test
        @DisplayName(
                "SoundService handles bare path with null parent without NPE during provisioning")
        void barePathProvisioningDoesNotThrowNpe() throws IOException {
            Path barePath = Paths.get("empirical-test-bell-" + System.nanoTime() + ".mp3");
            assertThat(barePath.getParent()).as("Bare path should have null parent").isNull();

            try {
                // Must not throw NullPointerException
                assertThatCode(() -> new SoundService(barePath, path -> {}, true))
                        .doesNotThrowAnyException();
                assertThat(Files.exists(barePath))
                        .as("Provisioning should have created the file in CWD")
                        .isTrue();
            } finally {
                Files.deleteIfExists(barePath);
            }
        }

        @Test
        @DisplayName(
                "SoundService handles bare path when sound does not exist and provisioning is false")
        void barePathMissingFilePlaybackDoesNotThrowNpe() {
            Path nonExistentBarePath = Paths.get("non-existent-" + System.nanoTime() + ".mp3");
            assertThat(nonExistentBarePath.getParent()).isNull();
            assertThat(Files.exists(nonExistentBarePath)).isFalse();

            AtomicReference<Path> dispatchedPath = new AtomicReference<>();
            SoundService service =
                    new SoundService(nonExistentBarePath, dispatchedPath::set, false);
            service.setSoundEnabled(true);

            // resolveSoundPath returns null because file does not exist; playbackDispatcher accepts
            // null
            assertThatCode(service::playCompletionSound).doesNotThrowAnyException();
            assertThat(dispatchedPath.get()).isNull();
        }

        @Test
        @DisplayName("SoundService plays bare path correctly when file exists")
        void barePathExistingFilePlaybackDispatchesBarePath() throws IOException {
            Path barePath = Paths.get("existing-bare-" + System.nanoTime() + ".mp3");
            Files.writeString(barePath, "dummy-content");

            try {
                AtomicReference<Path> dispatchedPath = new AtomicReference<>();
                SoundService service = new SoundService(barePath, dispatchedPath::set, false);
                service.setSoundEnabled(true);

                service.playCompletionSound();
                assertThat(dispatchedPath.get()).isEqualTo(barePath);
            } finally {
                Files.deleteIfExists(barePath);
            }
        }

        @Test
        @DisplayName("Repeated provisioning on bare path does not fail or overwrite existing file")
        void repeatedProvisioningOnBarePathIsIdempotent() throws IOException {
            Path barePath = Paths.get("idempotent-bare-" + System.nanoTime() + ".mp3");
            try {
                SoundService service = new SoundService(barePath, path -> {}, true);
                assertThat(Files.exists(barePath)).isTrue();
                long initialSize = Files.size(barePath);

                // Second call should return immediately without error
                assertThatCode(service::ensureDefaultSoundExists).doesNotThrowAnyException();
                assertThat(Files.size(barePath)).isEqualTo(initialSize);
            } finally {
                Files.deleteIfExists(barePath);
            }
        }
    }
}
