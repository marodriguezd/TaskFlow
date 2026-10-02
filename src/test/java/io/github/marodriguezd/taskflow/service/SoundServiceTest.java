package io.github.marodriguezd.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.persistence.DatabaseManager;
import io.github.marodriguezd.taskflow.persistence.SqlitePreferenceRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SoundServiceTest {

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
    @DisplayName("Enabled sound dispatches completion playback")
    void enabledSoundDispatchesPlayback() {
        PlatformService platformService = new PlatformService();
        List<Path> dispatched = new ArrayList<>();
        SoundService service = new SoundService(platformService, dispatched::add, false);

        service.setSoundEnabled(true);
        service.playCompletionSound();

        assertThat(dispatched).hasSize(1);
    }
}
