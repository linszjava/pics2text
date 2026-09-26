package com.pics2text.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record User(
        UUID id,
        String username,
        String passwordHash,
        String displayName,
        GlobalRole globalRole,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(globalRole, "globalRole");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean active() {
        return status == UserStatus.ACTIVE;
    }

    public boolean administrator() {
        return globalRole == GlobalRole.ADMIN;
    }
}
