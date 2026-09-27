package com.pics2text.layout.application;

public record LayoutTargetSlot(int slotIndex, int row, int column, int x, int y,
        int width, int height, double coverage, boolean edge) {
    public LayoutTargetSlot {
        if (slotIndex < 0 || row < 0 || column < 0 || x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("布局槽位参数无效");
        }
        if (coverage < 0 || coverage > 1) throw new IllegalArgumentException("槽位覆盖率无效");
    }
}
