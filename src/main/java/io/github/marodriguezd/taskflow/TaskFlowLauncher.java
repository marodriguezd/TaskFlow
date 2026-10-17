package io.github.marodriguezd.taskflow;

import java.util.Locale;

/**
 * Standard JVM entrypoint separating the launcher from JavaFX Application class. This guarantees
 * reliable invocation via java -jar and native jpackage bundles.
 */
public final class TaskFlowLauncher {

    private TaskFlowLauncher() {}

    private static final String SMOKE_TEST_ARGUMENT = "--smoke-test=";

    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.startsWith(SMOKE_TEST_ARGUMENT)) {
                // Explicit opt-in only: packaged smoke tests need a startup signal, while normal
                // launches must keep their regular lifecycle.
                System.setProperty(
                        SmokeTestSupport.MARKER_PROPERTY,
                        arg.substring(SMOKE_TEST_ARGUMENT.length()));
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            // Ensure optimal GTK3 glass backend support for Linux window managers (Wayland / X11)
            if (System.getProperty("jdk.gtk.version") == null) {
                System.setProperty("jdk.gtk.version", "3");
            }
        }
        TaskFlowApp.main(args);
    }
}
