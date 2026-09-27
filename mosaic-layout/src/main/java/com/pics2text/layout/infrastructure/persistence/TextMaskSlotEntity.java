package com.pics2text.layout.infrastructure.persistence;

import java.util.UUID;

public record TextMaskSlotEntity(UUID maskId, int slotIndex, int rowIndex, int columnIndex,
        int x, int y, int width, int height, double coverage, boolean edge) {
}
