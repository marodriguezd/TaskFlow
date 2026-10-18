package io.github.marodriguezd.taskflow.ui.dialog;

import java.util.List;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Positions transient dialogs next to their owner window.
 *
 * <p>Clamping is done against the visual bounds of the screen that hosts the owner, never against
 * absolute zero: multi-monitor desktops may place secondary screens at negative coordinates, and
 * clamping to {@code 0} would teleport dialogs back to the primary monitor.
 */
final class DialogPositioning {

    private DialogPositioning() {}

    /**
     * Places {@code stage} at the given offset from its owner, kept inside the owner's screen.
     *
     * @param stage dialog stage whose owner is already set
     * @param offsetX horizontal offset from the owner's left edge (negative = left of the owner)
     * @param offsetY vertical offset from the owner's top edge
     * @param width expected dialog width used for clamping
     * @param height expected dialog height used for clamping
     */
    static void placeNearOwner(
            Stage stage, double offsetX, double offsetY, double width, double height) {
        Window owner = stage.getOwner();
        if (owner == null) {
            return;
        }
        Point2D position =
                clampToBounds(
                        owner.getX() + offsetX,
                        owner.getY() + offsetY,
                        width,
                        height,
                        screenBoundsFor(owner));
        stage.setX(position.getX());
        stage.setY(position.getY());
    }

    /** Returns the visual bounds of the screen containing the owner window. */
    private static Rectangle2D screenBoundsFor(Window owner) {
        List<Screen> screens =
                Screen.getScreensForRectangle(
                        owner.getX(),
                        owner.getY(),
                        Math.max(1.0, owner.getWidth()),
                        Math.max(1.0, owner.getHeight()));
        Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.getFirst();
        return screen.getVisualBounds();
    }

    /** Clamps a desired top-left position so the dialog stays inside {@code bounds}. */
    static Point2D clampToBounds(
            double x, double y, double width, double height, Rectangle2D bounds) {
        double maxX = bounds.getMaxX() - width;
        double maxY = bounds.getMaxY() - height;
        double clampedX =
                maxX <= bounds.getMinX()
                        ? bounds.getMinX()
                        : Math.min(Math.max(x, bounds.getMinX()), maxX);
        double clampedY =
                maxY <= bounds.getMinY()
                        ? bounds.getMinY()
                        : Math.min(Math.max(y, bounds.getMinY()), maxY);
        return new Point2D(clampedX, clampedY);
    }
}
