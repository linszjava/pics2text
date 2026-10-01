package com.pics2text.surface.application;

import com.pics2text.surface.domain.SurfaceRect;

public record SurfaceMapping(int canvasWidthPx, int canvasHeightPx, SurfaceRect printAreaMm) {
    public SurfaceMapping {
        if (canvasWidthPx <= 0 || canvasHeightPx <= 0) {
            throw new IllegalArgumentException("布局画布像素尺寸必须大于0");
        }
    }
    public double scaleXmmPerPixel() { return printAreaMm.width() / canvasWidthPx; }
    public double scaleYmmPerPixel() { return printAreaMm.height() / canvasHeightPx; }
    public SurfaceRect map(int x, int y, int width, int height) {
        return new SurfaceRect(printAreaMm.x() + x * scaleXmmPerPixel(),
                printAreaMm.y() + y * scaleYmmPerPixel(), width * scaleXmmPerPixel(),
                height * scaleYmmPerPixel());
    }
}
