package com.pics2text.project.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProjectMembership(
        UUID projectId,
        UUID userId,
        ProjectRole role,
        UUID createdBy,
        Instant createdAt) {

    public ProjectMembership {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
