package com.pics2text.layout.domain;

public record CropBox(double x, double y, double width, double height) {
    private static final double EPSILON = 0.000001;

    public CropBox {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                || !Double.isFinite(height) || x < 0 || y < 0 || width <= 0 || height <= 0
                || width > 1 || height > 1 || x + width > 1 + EPSILON
                || y + height > 1 + EPSILON) {
            throw new IllegalArgumentException("裁切区域必须位于归一化图片范围内");
        }
    }
}
