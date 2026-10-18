package io.github.marodriguezd.taskflow.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.ThemeMode;
import io.github.marodriguezd.taskflow.ui.JavaFxTestSupport;
import io.github.marodriguezd.taskflow.ui.theme.ThemeManager;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AboutDialogTest {

    @BeforeEach
    void checkDisplay() {
        Assumptions.assumeTrue(
                JavaFxTestSupport.isAvailable(),
                "JavaFX display not available in this environment");
    }

    @Test
    @DisplayName("Slider change invokes onOpacityChanged callback with normalized value")
    void testSliderChangesOpacity() throws Exception {
        AtomicReference<Double> reportedOpacity = new AtomicReference<>();
        CompletableFuture<Void> future = new CompletableFuture<>();

        Platform.runLater(
                () -> {
                    try {
                        ThemeManager themeManager = new ThemeManager(ThemeMode.DARK);
                        AboutDialog dialog =
                                new AboutDialog(null, themeManager, 0.85, reportedOpacity::set);

                        Field stageField = AboutDialog.class.getDeclaredField("stage");
                        stageField.setAccessible(true);
                        Stage stage = (Stage) stageField.get(dialog);

                        Scene scene = stage.getScene();
                        Slider slider = (Slider) scene.lookup(".opacity-slider");
                        assertThat(slider).isNotNull();
                        assertThat(slider.getValue()).isEqualTo(85.0);

                        // Simulate dragging the slider
                        slider.setValue(60.0);
                        assertThat(reportedOpacity.get()).isEqualTo(0.60);

                        slider.setValue(45.0);
                        assertThat(reportedOpacity.get()).isEqualTo(0.45);

                        future.complete(null);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });

        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Dragging slider updates both percentage label and opacity callback")
    void testSliderUpdatesPercentageLabelAndCallback() throws Exception {
        AtomicReference<Double> currentAppliedOpacity = new AtomicReference<>(0.85);
        CompletableFuture<Void> future = new CompletableFuture<>();

        Platform.runLater(
                () -> {
                    try {
                        ThemeManager themeManager = new ThemeManager(ThemeMode.DARK);
                        AboutDialog dialog =
                                new AboutDialog(
                                        null, themeManager, 0.85, currentAppliedOpacity::set);

                        Field stageField = AboutDialog.class.getDeclaredField("stage");
                        stageField.setAccessible(true);
                        Stage stage = (Stage) stageField.get(dialog);

                        Scene scene = stage.getScene();
                        Slider slider = (Slider) scene.lookup(".opacity-slider");
                        assertThat(slider).isNotNull();

                        // Simulate micro-drag events
                        slider.setValue(70.0);
                        assertThat(currentAppliedOpacity.get()).isEqualTo(0.70);

                        slider.setValue(60.0);
                        assertThat(currentAppliedOpacity.get()).isEqualTo(0.60);

                        slider.setValue(50.0);
                        assertThat(currentAppliedOpacity.get()).isEqualTo(0.50);

                        stage.close();
                        future.complete(null);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });

        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Null onOpacityChanged consumer safely handles slider movements")
    void testNullConsumerSafe() throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Platform.runLater(
                () -> {
                    try {
                        ThemeManager themeManager = new ThemeManager(ThemeMode.DARK);
                        AboutDialog dialog = new AboutDialog(null, themeManager);

                        Field stageField = AboutDialog.class.getDeclaredField("stage");
                        stageField.setAccessible(true);
                        Stage stage = (Stage) stageField.get(dialog);

                        Scene scene = stage.getScene();
                        Slider slider = (Slider) scene.lookup(".opacity-slider");
                        assertThat(slider).isNotNull();

                        // Should not throw NPE
                        slider.setValue(70.0);

                        future.complete(null);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });

        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Escape key and close button trigger stage closure")
    void testDismissalPaths() throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Platform.runLater(
                () -> {
                    try {
                        ThemeManager themeManager = new ThemeManager(ThemeMode.DARK);
                        AboutDialog dialog = new AboutDialog(null, themeManager);

                        Field stageField = AboutDialog.class.getDeclaredField("stage");
                        stageField.setAccessible(true);
                        Stage stage = (Stage) stageField.get(dialog);

                        Scene scene = stage.getScene();

                        // Verify close button exists
                        Button closeButton = (Button) scene.lookup(".close-button");
                        assertThat(closeButton).isNotNull();

                        stage.show();
                        assertThat(stage.isShowing()).isTrue();

                        // Fire ESC key event on scene
                        KeyEvent escapeEvent =
                                new KeyEvent(
                                        KeyEvent.KEY_PRESSED,
                                        "",
                                        "",
                                        KeyCode.ESCAPE,
                                        false,
                                        false,
                                        false,
                                        false);
                        scene.getOnKeyPressed().handle(escapeEvent);
                        assertThat(stage.isShowing()).isFalse();

                        // Re-open and verify close icon button dismissal
                        stage.show();
                        assertThat(stage.isShowing()).isTrue();
                        closeButton.fire();
                        assertThat(stage.isShowing()).isFalse();

                        future.complete(null);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });

        future.get(5, TimeUnit.SECONDS);
    }
}
