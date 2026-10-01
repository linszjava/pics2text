package com.pics2text.rendering.domain;

public record RenderDimensions(double printWidthMm, double printHeightMm, double bleedMm,
        int dpi, int contentWidthPx, int contentHeightPx, int bleedPx,
        int outputWidthPx, int outputHeightPx) {
    public static final int MAX_DIMENSION_PX = 1_000_000_000;

    public static RenderDimensions of(double widthMm, double heightMm, double bleedMm, int dpi) {
        if (!Double.isFinite(widthMm) || !Double.isFinite(heightMm) || !Double.isFinite(bleedMm)
                || widthMm <= 0 || heightMm <= 0 || bleedMm < 0 || dpi < 72 || dpi > 2400) {
            throw new IllegalArgumentException("印刷尺寸、出血或DPI无效");
        }
        int width = pixels(widthMm, dpi); int height = pixels(heightMm, dpi); int bleed = pixels(bleedMm, dpi);
        int outputWidth = Math.addExact(width, Math.multiplyExact(bleed, 2));
        int outputHeight = Math.addExact(height, Math.multiplyExact(bleed, 2));
        if (outputWidth > MAX_DIMENSION_PX || outputHeight > MAX_DIMENSION_PX) {
            throw new IllegalArgumentException("输出像素尺寸超出渲染上限");
        }
        return new RenderDimensions(widthMm, heightMm, bleedMm, dpi, width, height,
                bleed, outputWidth, outputHeight);
    }

    public double pixelsPerMm() { return dpi / 25.4; }
    private static int pixels(double millimeters, int dpi) {
        double value = millimeters * dpi / 25.4;
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("输出像素尺寸超出整数范围");
        return Math.max(0, (int) Math.round(value));
    }
}
