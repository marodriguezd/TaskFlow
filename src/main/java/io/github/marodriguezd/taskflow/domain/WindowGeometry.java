package io.github.marodriguezd.taskflow.domain;

/** Window position and dimensions record. */
public record WindowGeometry(double x, double y, double width, double height) {
    public WindowGeometry {
        width = Math.max(300, width);
        height = Math.max(420, height);
    }

    public static WindowGeometry defaultGeometry() {
        return new WindowGeometry(100, 100, 340, 560);
    }
}
