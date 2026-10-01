package io.github.marodriguezd.taskflow;

/**
 * Standard JVM entrypoint separating the launcher from JavaFX Application class. This guarantees
 * reliable invocation via java -jar and native jpackage bundles.
 */
public final class TaskFlowLauncher {

    private TaskFlowLauncher() {
        // Prevent instantiation
    }

    public static void main(String[] args) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            // Ensure optimal GTK3 glass backend support for Linux window managers (Wayland / X11)
            if (System.getProperty("jdk.gtk.version") == null) {
                System.setProperty("jdk.gtk.version", "3");
            }
        }
        TaskFlowApp.main(args);
    }
}
