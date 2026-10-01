package com.pics2text.surface.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public record SurfaceBindingEntity(UUID id, UUID projectId, UUID layoutId, UUID layoutVersionId,
        UUID templateId, double originXmm, double originYmm, double printWidthMm,
        double printHeightMm, double scaleXmmPerPx, double scaleYmmPerPx,
        String validationStatus, int violationCount, String violationsJson,
        String coordinateManifestJson, String manifestHash, UUID validatedBy,
        Instant validatedAt, Instant createdAt, Instant updatedAt) { }
