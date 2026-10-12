package com.pics2text.api.application;

import com.pics2text.api.common.RetryableCapacityException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class PhotoUploadCompletionLimiter {
    private final Semaphore globalPermits;
    private final int perUserLimit;
    private final int retryAfterSeconds;
    private final ConcurrentHashMap<UUID, AtomicInteger> userInFlight = new ConcurrentHashMap<>();

    PhotoUploadCompletionLimiter(
            @Value("${object-storage.completion-capacity.global:4}") int globalLimit,
            @Value("${object-storage.completion-capacity.per-user:3}") int perUserLimit,
            @Value("${object-storage.completion-capacity.retry-after:1s}") java.time.Duration retryAfter) {
        if (globalLimit < 1 || perUserLimit < 1 || retryAfter.isNegative() || retryAfter.isZero()) {
            throw new IllegalArgumentException("照片验真容量配置必须为正数");
        }
        this.globalPermits = new Semaphore(globalLimit);
        this.perUserLimit = Math.min(perUserLimit, globalLimit);
        this.retryAfterSeconds = Math.max(1, Math.toIntExact(retryAfter.toSeconds()));
    }

    Lease acquire(UUID userId) {
        if (!globalPermits.tryAcquire()) throw exhausted();
        AtomicBoolean userPermit = new AtomicBoolean();
        userInFlight.compute(userId, (ignored, current) -> {
            AtomicInteger counter = current == null ? new AtomicInteger() : current;
            if (counter.get() < perUserLimit) {
                counter.incrementAndGet();
                userPermit.set(true);
            }
            return counter;
        });
        if (!userPermit.get()) {
            globalPermits.release();
            removeUnusedCounter(userId);
            throw exhausted();
        }
        return new Lease(() -> {
            userInFlight.computeIfPresent(userId, (ignored, counter) ->
                    counter.decrementAndGet() == 0 ? null : counter);
            globalPermits.release();
        });
    }

    private void removeUnusedCounter(UUID userId) {
        userInFlight.computeIfPresent(userId, (ignored, counter) -> counter.get() == 0 ? null : counter);
    }

    private RetryableCapacityException exhausted() {
        return new RetryableCapacityException("PHOTO_UPLOAD_CAPACITY_EXCEEDED",
                "照片验真容量已满，请稍后重试", retryAfterSeconds);
    }

    static final class Lease implements AutoCloseable {
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(Runnable release) { this.release = release; }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) release.run();
        }
    }
}
