package io.github.marodriguezd.taskflow.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure clamping math for multi-monitor dialog placement; needs no display. */
class DialogPositioningTest {

    @Test
    @DisplayName("Keeps a dialog on a secondary monitor with negative coordinates")
    void keepsNegativeSecondaryScreenCoordinates() {
        Rectangle2D leftScreen = new Rectangle2D(-1920, 0, 1920, 1080);

        Point2D position = DialogPositioning.clampToBounds(-2110, 60, 320, 260, leftScreen);

        assertThat(position.getX()).isEqualTo(-1920.0);
        assertThat(position.getY()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("Does not move a dialog that already fits on its screen")
    void leavesFittingDialogUntouched() {
        Rectangle2D screen = new Rectangle2D(-1920, 0, 1920, 1080);

        Point2D position = DialogPositioning.clampToBounds(-1800, 60, 320, 260, screen);

        assertThat(position.getX()).isEqualTo(-1800.0);
        assertThat(position.getY()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("Clamps against the right and bottom edges of the host screen")
    void clampsToTrailingEdges() {
        Rectangle2D screen = new Rectangle2D(0, 0, 1920, 1080);

        Point2D position = DialogPositioning.clampToBounds(1900, 1000, 320, 260, screen);

        assertThat(position.getX()).isEqualTo(1600.0);
        assertThat(position.getY()).isEqualTo(820.0);
    }

    @Test
    @DisplayName("Falls back to the screen origin when the dialog is larger than the screen")
    void oversizedDialogPinsToOrigin() {
        Rectangle2D screen = new Rectangle2D(-800, -600, 800, 600);

        Point2D position = DialogPositioning.clampToBounds(10, 10, 900, 700, screen);

        assertThat(position.getX()).isEqualTo(-800.0);
        assertThat(position.getY()).isEqualTo(-600.0);
    }
}
