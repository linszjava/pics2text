package com.pics2text.rendering.infrastructure.persistence;

import java.util.UUID;

public record PrintRenderPhotoRow(int slotIndex, UUID photoId, int x, int y, int width, int height,
        double cropX, double cropY, double cropWidth, double cropHeight, String sourceKey,
        String originalSha256, Integer sourceWidth, Integer sourceHeight, String reviewStatus,
        String authorizationStatus) { }
