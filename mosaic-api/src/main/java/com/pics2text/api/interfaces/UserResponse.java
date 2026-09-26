package com.pics2text.api.interfaces;

import com.pics2text.identity.domain.GlobalRole;
import com.pics2text.identity.domain.User;
import com.pics2text.identity.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String displayName,
        GlobalRole globalRole,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.globalRole(),
                user.status(),
                user.createdAt(),
                user.updatedAt());
    }
}
