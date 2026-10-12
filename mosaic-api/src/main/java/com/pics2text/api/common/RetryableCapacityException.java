package com.pics2text.api.common;

import org.springframework.http.HttpStatus;

public class RetryableCapacityException extends ApiException {
    private final int retryAfterSeconds;

    public RetryableCapacityException(String code, String message, int retryAfterSeconds) {
        super(code, message, HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() { return retryAfterSeconds; }
}
