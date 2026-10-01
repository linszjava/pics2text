package com.pics2text.rendering.domain;

public record PixelRect(int x, int y, int width, int height) {
    public PixelRect {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("像素矩形必须位于非负坐标且尺寸大于0");
        }
    }
    public int maxX() { return Math.addExact(x, width); }
    public int maxY() { return Math.addExact(y, height); }
    public boolean intersects(PixelRect other) {
        return x < other.maxX() && maxX() > other.x && y < other.maxY() && maxY() > other.y;
    }
}
