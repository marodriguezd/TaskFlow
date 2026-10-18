package io.github.marodriguezd.taskflow.ui;

import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

/**
 * Shared utility for safely initializing the JavaFX platform in tests. Prevents deadlocks on
 * CountDownLatch in headless CI when multiple test classes attempt initialization after a display
 * failure.
 */
public final class JavaFxTestSupport {

    private static final AtomicBoolean INITIALIZATION_ATTEMPTED = new AtomicBoolean(false);
    private static volatile boolean javaFxAvailable = false;

    private JavaFxTestSupport() {}

    public static synchronized boolean isAvailable() {
        if (!INITIALIZATION_ATTEMPTED.getAndSet(true)) {
            try {
                Platform.startup(() -> {});
                Platform.setImplicitExit(false);
                javaFxAvailable = true;
            } catch (Throwable t) {
                // UnsupportedOperationException: Unable to open DISPLAY, or any init failure
                javaFxAvailable = false;
            }
        }
        return javaFxAvailable;
    }
}
