package io.github.marodriguezd.taskflow.persistence;

import io.github.marodriguezd.taskflow.domain.UserPreferences;
import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import java.util.Optional;

/** Repository interface for managing user preferences and window geometry persistence. */
public interface PreferenceRepository {

    UserPreferences loadPreferences(boolean defaultAlwaysOnTop);

    void savePreferences(UserPreferences preferences);

    Optional<WindowGeometry> loadGeometry();

    void saveGeometry(WindowGeometry geometry);
}
