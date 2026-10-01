package com.pics2text.layout.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public record LayoutVersionEntity(UUID id, UUID layoutId, int versionNumber, UUID parentVersionId,
        String reason, UUID clientBatchId, long sourceRevision, String contentHash,
        int assignmentCount, String note, UUID createdBy, Instant createdAt) { }
