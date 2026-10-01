package com.pics2text.layout.infrastructure.persistence;

import java.util.UUID;

public record LayoutPreviewContextEntity(UUID previewId, UUID layoutId, UUID versionId,
        UUID projectId, int canvasWidth, int canvasHeight, String status) { }
