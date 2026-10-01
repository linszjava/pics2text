package com.pics2text.layout.infrastructure.persistence;

import java.util.UUID;

public record LayoutEditorAssignmentEntity(UUID ownerId, int slotIndex, UUID photoId,
        int x, int y, int width, int height, double cropX, double cropY,
        double cropWidth, double cropHeight) { }
