package com.pics2text.layout.application;

public record TextMaskSlot(int index, int row, int column, int x, int y,
        int width, int height, double coverage, boolean edge) {
}
