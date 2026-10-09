package io.github.marodriguezd.taskflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marodriguezd.taskflow.domain.ThemeMode;
import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SqlitePreferenceRepositoryTest {

    private DatabaseManager databaseManager;
    private SqlitePreferenceRepository repository;

    @BeforeEach
    void setUp() {
        databaseManager = DatabaseManager.inMemory();
        repository = new SqlitePreferenceRepository(databaseManager);
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    @DisplayName("Saves and loads user preferences")
    void testSaveAndLoadPreferences() {
        UserPreferences initial = repository.loadPreferences(false);
        assertThat(initial.theme()).isEqualTo(ThemeMode.DARK);
        assertThat(initial.alwaysOnTop()).isFalse();

        UserPreferences updated = new UserPreferences(ThemeMode.LIGHT, true, false, "");
        repository.savePreferences(updated);

        UserPreferences loaded = repository.loadPreferences(false);
        assertThat(loaded.theme()).isEqualTo(ThemeMode.LIGHT);
        assertThat(loaded.alwaysOnTop()).isTrue();
        assertThat(loaded.soundEnabled()).isFalse();
    }

    @Test
    @DisplayName("Language preference persists; unset means blank (first run)")
    void testSaveAndLoadLanguage() {
        // Fresh database: language was never chosen -> blank, not "en"
        UserPreferences initial = repository.loadPreferences(false);
        assertThat(initial.language()).isEmpty();

        repository.savePreferences(initial.withLanguage("de"));
        assertThat(repository.loadPreferences(false).language()).isEqualTo("de");

        repository.savePreferences(repository.loadPreferences(false).withLanguage("zh-Hans"));
        UserPreferences reloaded = repository.loadPreferences(false);
        assertThat(reloaded.language()).isEqualTo("zh-Hans");
        // Other preferences untouched by a language-only update
        assertThat(reloaded.theme()).isEqualTo(ThemeMode.DARK);
    }

    @Test
    @DisplayName("Glass opacity preference persists and clamps within valid range")
    void testSaveAndLoadGlassOpacity() {
        UserPreferences initial = repository.loadPreferences(false);
        assertThat(initial.glassOpacity()).isEqualTo(0.80);

        repository.savePreferences(initial.withGlassOpacity(0.95));
        assertThat(repository.loadPreferences(false).glassOpacity()).isEqualTo(0.95);

        // Clamping check in domain record
        UserPreferences low = new UserPreferences(ThemeMode.DARK, false, true, "", 0.10);
        assertThat(low.glassOpacity()).isEqualTo(UserPreferences.DEFAULT_GLASS_OPACITY);

        UserPreferences high = new UserPreferences(ThemeMode.DARK, false, true, "", 1.50);
        assertThat(high.glassOpacity()).isEqualTo(UserPreferences.DEFAULT_GLASS_OPACITY);
    }

    @Test
    @DisplayName("Database read failures are not returned as default preferences")
    void propagatesPreferenceReadFailure() throws Exception {
        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE preferences");
        }

        assertThatThrownBy(() -> repository.loadPreferences(false))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("Could not read preferences");
    }

    @Test
    @DisplayName("Geometry values are written atomically")
    void geometrySaveRollsBackOnFailure() throws Exception {
        try (var connection = databaseManager.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TRIGGER fail_geo_y BEFORE INSERT ON preferences "
                            + "WHEN NEW.pref_key = 'geo_y' "
                            + "BEGIN SELECT RAISE(ABORT, 'simulated geometry failure'); END");
        }

        assertThatThrownBy(() -> repository.saveGeometry(new WindowGeometry(10, 20, 360, 600)))
                .isInstanceOf(PersistenceException.class);
        assertThat(repository.loadGeometry()).isEmpty();
    }

    @Test
    @DisplayName("Null language normalizes to blank in the domain record")
    void testNullLanguageNormalized() {
        UserPreferences prefs = new UserPreferences(ThemeMode.DARK, false, true, null);
        assertThat(prefs.language()).isEmpty();
        assertThat(UserPreferences.defaults(false).language()).isEmpty();
    }

    @Test
    @DisplayName("Saves and loads window geometry")
    void testSaveAndLoadGeometry() {
        assertThat(repository.loadGeometry()).isEmpty();

        WindowGeometry geometry = new WindowGeometry(150.0, 250.0, 360.0, 600.0);
        repository.saveGeometry(geometry);

        Optional<WindowGeometry> loaded = repository.loadGeometry();
        assertThat(loaded).isPresent();
        assertThat(loaded.get().x()).isEqualTo(150.0);
        assertThat(loaded.get().y()).isEqualTo(250.0);
        assertThat(loaded.get().width()).isEqualTo(360.0);
        assertThat(loaded.get().height()).isEqualTo(600.0);
    }
}
