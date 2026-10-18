package io.github.marodriguezd.taskflow.ui.theme;

import io.github.marodriguezd.taskflow.domain.ThemeMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages UI themes and dynamically updates JavaFX Scenes. */
public class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    // Resolved once: stylesheet classpath lookups are a per-application constant, not per scene.
    private static final String BASE_CSS = stylesheetUrl("/css/base.css");
    private static final String LIGHT_CSS = stylesheetUrl("/css/light.css");
    private static final String DARK_CSS = stylesheetUrl("/css/dark.css");

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
        // One atomic replacement instead of clear() + add() + add(), which would invalidate the
        // scene's CSS twice per theme switch.
        List<String> stylesheets = new ArrayList<>(2);
        if (BASE_CSS != null) {
            stylesheets.add(BASE_CSS);
        }
        String themeCss = currentTheme == ThemeMode.LIGHT ? LIGHT_CSS : DARK_CSS;
        if (themeCss != null) {
            stylesheets.add(themeCss);
        }
        scene.getStylesheets().setAll(stylesheets);
    }

    private static String stylesheetUrl(String resource) {
        URL url = ThemeManager.class.getResource(resource);
        return url != null ? url.toExternalForm() : null;
    }

    public void addThemeChangeListener(Consumer<ThemeMode> listener) {
        themeListeners.add(listener);
    }
}
