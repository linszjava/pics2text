package com.pics2text.photoasset.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public record PhotoVariantEntity(UUID photoId, String variantType, String objectKey,
        int width, int height, long byteSize, String contentType, Instant createdAt) {
}
