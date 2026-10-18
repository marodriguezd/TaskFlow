package io.github.marodriguezd.taskflow.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgressBarViewTest {

    @Test
    @DisplayName("Precalculates baseColor and darkerColor and reuses them in updateProgress")
    void testPrecalculatedColorsReused() throws Exception {
        ProgressBarView bar = new ProgressBarView(100, 50, "#ff5e78");

        Field baseColorField = ProgressBarView.class.getDeclaredField("baseColor");
        baseColorField.setAccessible(true);
        Color baseColor = (Color) baseColorField.get(bar);

        Field darkerColorField = ProgressBarView.class.getDeclaredField("darkerColor");
        darkerColorField.setAccessible(true);
        Color darkerColor = (Color) darkerColorField.get(bar);

        assertThat(baseColor).isEqualTo(Color.web("#ff5e78"));
        assertThat(darkerColor).isEqualTo(baseColor.deriveColor(0, 1.0, 0.7, 1.0));

        Field fillRectField = ProgressBarView.class.getDeclaredField("fillRect");
        fillRectField.setAccessible(true);
        Rectangle fillRect = (Rectangle) fillRectField.get(bar);

        assertThat(fillRect.isVisible()).isTrue();
        assertThat(fillRect.getFill()).isInstanceOf(LinearGradient.class);

        LinearGradient gradient = (LinearGradient) fillRect.getFill();
        assertThat(gradient.getStops().get(0).getColor()).isSameAs(darkerColor);
        assertThat(gradient.getStops().get(1).getColor()).isSameAs(baseColor);

        assertThat(gradient.isProportional()).isTrue();

        // Update progress and verify the cached gradient instance is reused (no per-tick churn)
        bar.setProgress(80, 100);
        LinearGradient updatedGradient = (LinearGradient) fillRect.getFill();
        assertThat(updatedGradient).isSameAs(gradient);
        assertThat(updatedGradient.getStops().get(0).getColor()).isSameAs(darkerColor);
        assertThat(updatedGradient.getStops().get(1).getColor()).isSameAs(baseColor);
    }

    @Test
    @DisplayName("Handles null, blank, and invalid priority color hex with default purple")
    void testInvalidAndBlankColorHexDefaults() throws Exception {
        for (String invalid : new String[] {null, "", "   ", "not-a-color", "#12345"}) {
            ProgressBarView bar = new ProgressBarView(100, 50, invalid);

            Field baseColorField = ProgressBarView.class.getDeclaredField("baseColor");
            baseColorField.setAccessible(true);
            Color baseColor = (Color) baseColorField.get(bar);

            assertThat(baseColor).isEqualTo(Color.web("#7c6af7"));
        }
    }

    @Test
    @DisplayName("Hides fill rectangle when progress is zero")
    void testZeroProgressHidesFill() throws Exception {
        ProgressBarView bar = new ProgressBarView(100, 0, "#3ddc84");

        Field fillRectField = ProgressBarView.class.getDeclaredField("fillRect");
        fillRectField.setAccessible(true);
        Rectangle fillRect = (Rectangle) fillRectField.get(bar);

        assertThat(fillRect.isVisible()).isFalse();
    }
}
