package io.github.marodriguezd.taskflow.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import java.util.List;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WindowGeometryResolverTest {

    private static final Rectangle2D PRIMARY = new Rectangle2D(0, 0, 1920, 1080);
    private static final Rectangle2D SECONDARY = new Rectangle2D(1920, 0, 1280, 1024);

    @Test
    @DisplayName("Normal saved geometry on a secondary monitor is preserved")
    void keepsValidMultiMonitorPosition() {
        WindowGeometry saved = new WindowGeometry(2100, 120, 420, 650);
        assertThat(WindowGeometryResolver.resolve(saved, List.of(PRIMARY, SECONDARY)))
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("Geometry from a removed monitor is moved onto the remaining display")
    void clampsRemovedMonitorGeometry() {
        WindowGeometry restored =
                WindowGeometryResolver.resolve(
                        new WindowGeometry(4200, 230, 420, 650), List.of(PRIMARY));
        assertThat(restored.x()).isEqualTo(1870);
        assertThat(restored.y()).isEqualTo(230);
        assertThat(restored.width()).isEqualTo(420);
        assertThat(restored.height()).isEqualTo(650);
    }

    @Test
    @DisplayName("Non-finite saved values fall back to centered default geometry")
    void invalidGeometryUsesDefault() {
        WindowGeometry restored =
                WindowGeometryResolver.resolve(
                        new WindowGeometry(Double.NaN, Double.POSITIVE_INFINITY, 400, 600),
                        List.of(PRIMARY));
        assertThat(restored).isEqualTo(new WindowGeometry(790, 260, 340, 560));
    }
}
