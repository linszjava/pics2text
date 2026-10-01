package com.pics2text.surface.infrastructure.persistence;

import java.util.UUID;

public record SurfacePreviewAssetEntity(UUID previewId, String status, String objectKey,
        Integer width, Integer height) { }
