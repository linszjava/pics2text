package com.pics2text.api.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        String requestId,
        Instant timestamp,
        List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {
    }
}

