package com.pics2text.layout.infrastructure.persistence;

import java.util.UUID;

public record LayoutReplacementPhotoEntity(UUID photoId, double cropX, double cropY,
        double cropWidth, double cropHeight) { }
