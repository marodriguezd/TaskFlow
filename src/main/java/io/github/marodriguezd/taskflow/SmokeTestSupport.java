package io.github.marodriguezd.taskflow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packaged-application smoke-test signal.
 *
 * <p>This seam is inert unless {@code TaskFlowLauncher} receives the explicit {@code
 * --smoke-test=<marker-file>} argument. A normal launch never sets the property, so production
 * startup, shutdown, and command-line behavior are unchanged. When enabled, the application writes
 * the marker only after the primary stage is showing, then requests the same orderly {@link
 * Platform#exit()} used by the window close button, so {@code TaskFlowApp.stop()} still releases
 * the database and timers. The external harness supplies the timeout: it treats a missing marker or
 * a non-zero exit as failure, never "still alive after N seconds" as success.
 */
final class SmokeTestSupport {

    static final String MARKER_PROPERTY = "taskflow.smokeTest.marker";
    private static final String MARKER_CONTENT = "started\n";

    private static final Logger log = LoggerFactory.getLogger(SmokeTestSupport.class);

    private SmokeTestSupport() {}

    static void signalStartupSuccess(Stage stage) {
        String marker = System.getProperty(MARKER_PROPERTY);
        if (marker == null || marker.isBlank()) {
            return;
        }
        if (!stage.isShowing()) {
            throw new IllegalStateException(
                    "Smoke test requested but the primary stage is not showing");
        }

        Path markerPath = Paths.get(marker);
        try {
            Path parent = markerPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    markerPath,
                    MARKER_CONTENT,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("Smoke-test startup signal could not be written to {}", markerPath, e);
            // Exit so the harness fails on the missing marker instead of waiting for a timeout.
            Platform.exit();
            return;
        }

        log.info("Smoke-test startup signal written to {}", markerPath);
        Platform.runLater(Platform::exit);
    }
}
