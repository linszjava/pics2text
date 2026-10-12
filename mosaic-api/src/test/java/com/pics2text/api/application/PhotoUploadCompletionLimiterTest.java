package com.pics2text.api.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pics2text.api.common.RetryableCapacityException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhotoUploadCompletionLimiterTest {
    @Test
    void enforcesPerUserLimitWithoutConsumingTheReleasedGlobalPermit() {
        var limiter = new PhotoUploadCompletionLimiter(2, 1, Duration.ofSeconds(3));
        UUID firstUser = UUID.randomUUID();
        try (var ignored = limiter.acquire(firstUser)) {
            assertThatThrownBy(() -> limiter.acquire(firstUser))
                    .isInstanceOf(RetryableCapacityException.class);
            assertThatCode(() -> {
                try (var second = limiter.acquire(UUID.randomUUID())) { }
            }).doesNotThrowAnyException();
        }
    }

    @Test
    void releasesPermitExactlyOnceEvenWhenCloseIsRepeated() {
        var limiter = new PhotoUploadCompletionLimiter(1, 1, Duration.ofSeconds(1));
        var lease = limiter.acquire(UUID.randomUUID());
        lease.close();
        lease.close();
        assertThatCode(() -> {
            try (var next = limiter.acquire(UUID.randomUUID())) { }
        }).doesNotThrowAnyException();
    }
}
