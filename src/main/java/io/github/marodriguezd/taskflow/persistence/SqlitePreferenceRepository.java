package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.ThemeMode;
import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SQLite implementation of PreferenceRepository. */
public class SqlitePreferenceRepository implements PreferenceRepository {

    private static final Logger log = LoggerFactory.getLogger(SqlitePreferenceRepository.class);
    private final DatabaseManager databaseManager;

    public SqlitePreferenceRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public UserPreferences loadPreferences(boolean defaultAlwaysOnTop) {
        Map<String, String> map = getAllPreferences();
        String themeStr = map.getOrDefault("theme", "dark");
        ThemeMode theme = ThemeMode.fromCode(themeStr);

        boolean alwaysOnTop = defaultAlwaysOnTop;
        if (map.containsKey("always_on_top")) {
            alwaysOnTop = Boolean.parseBoolean(map.get("always_on_top"));
        }

        boolean soundEnabled = true;
        if (map.containsKey("sound_enabled")) {
            soundEnabled = Boolean.parseBoolean(map.get("sound_enabled"));
        }

        // UI language tag as stored ("" = never chosen; first-run detection happens upstream).
        // The repository never interprets the value — it stays bundle-free.
        String language = map.getOrDefault("language", "");

        return new UserPreferences(theme, alwaysOnTop, soundEnabled, language);
    }

    @Override
    public void savePreferences(UserPreferences preferences) {
        setPreference("theme", preferences.theme().getCode());
        setPreference("always_on_top", String.valueOf(preferences.alwaysOnTop()));
        setPreference("sound_enabled", String.valueOf(preferences.soundEnabled()));
        setPreference("language", preferences.language());
    }

    @Override
    public Optional<WindowGeometry> loadGeometry() {
        Map<String, String> map = getAllPreferences();
        if (map.containsKey("geo_x")
                && map.containsKey("geo_y")
                && map.containsKey("geo_width")
                && map.containsKey("geo_height")) {
            try {
                double x = Double.parseDouble(map.get("geo_x"));
                double y = Double.parseDouble(map.get("geo_y"));
                double width = Double.parseDouble(map.get("geo_width"));
                double height = Double.parseDouble(map.get("geo_height"));
                return Optional.of(new WindowGeometry(x, y, width, height));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse window geometry from database: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    @Override
    public void saveGeometry(WindowGeometry geometry) {
        setPreference("geo_x", String.valueOf(geometry.x()));
        setPreference("geo_y", String.valueOf(geometry.y()));
        setPreference("geo_width", String.valueOf(geometry.width()));
        setPreference("geo_height", String.valueOf(geometry.height()));
    }

    private Map<String, String> getAllPreferences() {
        Map<String, String> result = new HashMap<>();
        String sql = "SELECT pref_key, pref_value FROM preferences";
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("pref_key"), rs.getString("pref_value"));
            }
        } catch (SQLException e) {
            log.error("Error reading preferences from database", e);
        }
        return result;
    }

    private void setPreference(String key, String value) {
        String sql =
                """
                INSERT INTO preferences (pref_key, pref_value)
                VALUES (?, ?)
                ON CONFLICT(pref_key) DO UPDATE SET pref_value = excluded.pref_value
                """;
        try (Connection conn = databaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error saving preference {}={}", key, value, e);
        }
    }
}
