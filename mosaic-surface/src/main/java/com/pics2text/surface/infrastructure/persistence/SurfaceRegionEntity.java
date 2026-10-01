package com.pics2text.surface.infrastructure.persistence;

import java.util.UUID;

public record SurfaceRegionEntity(UUID templateId, int regionIndex, String regionType, String name,
        double xMm, double yMm, double widthMm, double heightMm, boolean blocking,
        String metadataJson) { }
