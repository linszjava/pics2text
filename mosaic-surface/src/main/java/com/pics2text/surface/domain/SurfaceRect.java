package com.pics2text.surface.domain;

public record SurfaceRect(double x, double y, double width, double height) {
    private static final double EPSILON = 1e-9;

    public SurfaceRect {
        if (!finite(x) || !finite(y) || !finite(width) || !finite(height)
                || x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("曲面矩形坐标必须为有限的非负毫米值，尺寸必须大于0");
        }
    }

    public double maxX() { return x + width; }
    public double maxY() { return y + height; }

    public boolean intersects(SurfaceRect other) {
        return x < other.maxX() - EPSILON && maxX() > other.x() + EPSILON
                && y < other.maxY() - EPSILON && maxY() > other.y() + EPSILON;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
