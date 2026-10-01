package com.pics2text.layout.infrastructure.persistence;

public record LayoutPreviewPixelEntity(int slotIndex, int x, int y, int width, int height,
        double labL, double labA, double labB) { }
