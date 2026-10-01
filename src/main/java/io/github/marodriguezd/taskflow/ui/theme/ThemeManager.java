package io.github.marodriguezd.taskflow.ui.theme;

import io.github.marodriguezd.taskflow.domain.ThemeMode;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages UI themes and dynamically updates JavaFX Scenes. */
public class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    private final List<Consumer<ThemeMode>> themeListeners = new CopyOnWriteArrayList<>();
    private final List<Scene> registeredScenes = new CopyOnWriteArrayList<>();
    private ThemeMode currentTheme;

    public ThemeManager(ThemeMode initialTheme) {
        this.currentTheme = initialTheme != null ? initialTheme : ThemeMode.DARK;
    }

    public ThemeMode getCurrentTheme() {
        return currentTheme;
    }

    public void setTheme(ThemeMode theme) {
        if (theme == null || theme == currentTheme) {
            return;
        }
        this.currentTheme = theme;
        log.info("Switching theme to {}", currentTheme);
        for (Scene scene : registeredScenes) {
            applyTheme(scene);
        }
        for (Consumer<ThemeMode> listener : themeListeners) {
            listener.accept(currentTheme);
        }
    }

    public void toggleTheme() {
        setTheme(currentTheme.toggle());
    }

    public void registerScene(Scene scene) {
        if (scene != null && !registeredScenes.contains(scene)) {
            registeredScenes.add(scene);
            applyTheme(scene);
        }
    }

    public void unregisterScene(Scene scene) {
        registeredScenes.remove(scene);
    }

    public void applyTheme(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().clear();

        URL baseUrl = getClass().getResource("/css/base.css");
        if (baseUrl != null) {
            scene.getStylesheets().add(baseUrl.toExternalForm());
        }

        String themeCss = currentTheme == ThemeMode.LIGHT ? "/css/light.css" : "/css/dark.css";
        URL themeUrl = getClass().getResource(themeCss);
        if (themeUrl != null) {
            scene.getStylesheets().add(themeUrl.toExternalForm());
        }
    }

    public void addThemeChangeListener(Consumer<ThemeMode> listener) {
        themeListeners.add(listener);
    }
}
