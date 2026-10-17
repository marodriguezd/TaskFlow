package io.github.marodriguezd.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.persistence.DatabaseManager;
import io.github.marodriguezd.taskflow.persistence.SqlitePreferenceRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SoundServiceTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("Persisted disabled preference suppresses runtime playback dispatch")
    void disabledPreferenceSuppressesRuntimePlayback() {
        DatabaseManager databaseManager = DatabaseManager.inMemory();
        try {
            SqlitePreferenceRepository preferences =
                    new SqlitePreferenceRepository(databaseManager);
            preferences.savePreferences(UserPreferences.defaults(false).withSoundEnabled(false));
            PlatformService platformService = new PlatformService();
            List<Path> dispatched = new ArrayList<>();
            SoundService service = new SoundService(platformService, dispatched::add, false);
            service.setSoundEnabled(preferences.loadPreferences(false).soundEnabled());
            service.playCompletionSound();
            assertThat(dispatched).isEmpty();
        } finally {
            databaseManager.close();
        }
    }

    @Test
    @DisplayName("Disabled sound never dispatches audio playback")
    void disabledSoundSkipsPlaybackDispatch() {
        PlatformService platformService = new PlatformService();
        List<Path> dispatched = new ArrayList<>();
        SoundService service = new SoundService(platformService, dispatched::add, false);

        service.setSoundEnabled(false);
        service.playCompletionSound();

        assertThat(dispatched).isEmpty();
    }

    @Test
    @DisplayName("Enabled sound dispatches the configured user sound path")
    void enabledSoundDispatchesPlayback() throws IOException {
        Path userSound = tempDir.resolve("TaskFlow/bell.mp3");
        Files.createDirectories(userSound.getParent());
        Files.write(userSound, new byte[] {0x49, 0x44, 0x33});
        List<Path> dispatched = new ArrayList<>();
        SoundService service = new SoundService(userSound, dispatched::add, false);

        service.setSoundEnabled(true);
        service.playCompletionSound();

        assertThat(dispatched).containsExactly(userSound);
    }

    @Test
    @DisplayName("The bundled notification sound is present in the application resources")
    void bundledSoundResourceIsReadable() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/assets/bell.mp3")) {
            assertThat(in).as("bundled /assets/bell.mp3").isNotNull();
            byte[] bytes = in.readAllBytes();
            assertThat(bytes.length).isGreaterThan(1_000);
            // MP3 frames start with 0xFF, or an ID3 tag starts with "ID3".
            assertThat((bytes[0] & 0xFF) == 0xFF || (bytes[0] == 'I' && bytes[1] == 'D')).isTrue();
        }
    }

    @Test
    @DisplayName("Default sound provisioning copies the bundled resource into the data directory")
    void provisioningCopiesBundledSound() throws IOException {
        Path userSound = tempDir.resolve("TaskFlow/bell.mp3");
        SoundService service = new SoundService(userSound, path -> {}, true);

        assertThat(Files.exists(userSound)).isTrue();
        try (InputStream expected = getClass().getResourceAsStream("/assets/bell.mp3")) {
            assertThat(expected).isNotNull();
            assertThat(Files.size(userSound)).isEqualTo(expected.readAllBytes().length);
        }
    }

    @Test
    @DisplayName("Playback failures never escape the service")
    void playbackFailureDoesNotEscape() {
        SoundService service =
                new SoundService(
                        tempDir.resolve("missing/bell.mp3"),
                        path -> {
                            throw new IllegalArgumentException("simulated missing audio device");
                        },
                        false);
        service.setSoundEnabled(true);

        assertThatCode(service::playCompletionSound).doesNotThrowAnyException();
    }
}
