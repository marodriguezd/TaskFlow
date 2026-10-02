package io.github.marodriguezd.taskflow.ui;

import io.github.marodriguezd.taskflow.domain.WindowGeometry;
import io.github.marodriguezd.taskflow.ui.theme.UIConstants;
import java.util.Comparator;
import java.util.List;
import javafx.geometry.Rectangle2D;

/** Selects a safe monitor and clamps persisted window geometry to its visual bounds. */
final class WindowGeometryResolver {

    private static final double MIN_VISIBLE_PIXELS = 50;

    private WindowGeometryResolver() {}

    static WindowGeometry resolve(WindowGeometry saved, List<Rectangle2D> visualBounds) {
        if (visualBounds.isEmpty()) {
            return defaultGeometry(new Rectangle2D(0, 0, 1920, 1080));
        }
        if (saved == null || !isFinite(saved)) {
            return defaultGeometry(visualBounds.get(0));
        }

        Rectangle2D screen =
                visualBounds.stream()
                        .filter(bounds -> intersects(saved, bounds))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        visualBounds.stream()
                                                .max(
                                                        Comparator.comparingDouble(
                                                                bounds ->
                                                                        visibleArea(saved, bounds)))
                                                .orElse(visualBounds.get(0)));
        double width = Math.max(UIConstants.PANEL_MIN_WIDTH, saved.width());
        double height = Math.max(UIConstants.PANEL_MIN_HEIGHT, saved.height());
        double x = clamp(saved.x(), screen.getMinX(), screen.getMaxX() - MIN_VISIBLE_PIXELS);
        double y = clamp(saved.y(), screen.getMinY(), screen.getMaxY() - MIN_VISIBLE_PIXELS);
        return new WindowGeometry(x, y, width, height);
    }

    private static WindowGeometry defaultGeometry(Rectangle2D bounds) {
        double width = UIConstants.DEFAULT_WIDTH;
        double height = UIConstants.DEFAULT_HEIGHT;
        return new WindowGeometry(
                bounds.getMinX() + Math.max(0, (bounds.getWidth() - width) / 2),
                bounds.getMinY() + Math.max(0, (bounds.getHeight() - height) / 2),
                width,
                height);
    }

    private static boolean isFinite(WindowGeometry geometry) {
        return Double.isFinite(geometry.x())
                && Double.isFinite(geometry.y())
                && Double.isFinite(geometry.width())
                && Double.isFinite(geometry.height());
    }

    private static boolean intersects(WindowGeometry geometry, Rectangle2D bounds) {
        return visibleArea(geometry, bounds) >= MIN_VISIBLE_PIXELS * MIN_VISIBLE_PIXELS;
    }

    private static double visibleArea(WindowGeometry geometry, Rectangle2D bounds) {
        double width =
                Math.max(
                        0,
                        Math.min(geometry.x() + geometry.width(), bounds.getMaxX())
                                - Math.max(geometry.x(), bounds.getMinX()));
        double height =
                Math.max(
                        0,
                        Math.min(geometry.y() + geometry.height(), bounds.getMaxY())
                                - Math.max(geometry.y(), bounds.getMinY()));
        return width * height;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)));
    }
}
