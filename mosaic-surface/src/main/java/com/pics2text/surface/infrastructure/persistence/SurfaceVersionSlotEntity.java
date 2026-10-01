package com.pics2text.surface.infrastructure.persistence;

import java.util.UUID;

public record SurfaceVersionSlotEntity(int slotIndex, UUID photoId, int x, int y, int width,
        int height, double cropX, double cropY, double cropWidth, double cropHeight) { }
