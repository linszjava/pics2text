package com.pics2text.layout.infrastructure.persistence;

import java.util.UUID;

public record LayoutPhotoPreviewEntity(UUID photoId, String previewKey) { }
