package com.pics2text.api.application;

import com.pics2text.api.config.PhotoRelayProperties;
import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskEntity;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskMapper;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PhotoRelayUploadWorker {
    private static final Logger log = LoggerFactory.getLogger(PhotoRelayUploadWorker.class);

    private final PhotoRelayPersistenceService persistence;
    private final PhotoRelayUploadTaskMapper tasks;
    private final PhotoAssetMapper photos;
    private final ObjectStorageGateway storage;
    private final PhotoAssetService completion;
    private final PhotoRelayUploadService relay;
    private final PhotoRelayProperties properties;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService leaseKeeper;

    public PhotoRelayUploadWorker(PhotoRelayPersistenceService persistence,
            PhotoRelayUploadTaskMapper tasks,
            PhotoAssetMapper photos,
            ObjectStorageGateway storage,
            PhotoAssetService completion,
            PhotoRelayUploadService relay,
            PhotoRelayProperties properties) {
        this.persistence = persistence;
        this.tasks = tasks;
        this.photos = photos;
        this.storage = storage;
        this.completion = completion;
        this.relay = relay;
        this.properties = properties;

        // A fixed worker count plus a bounded handoff queue prevents unbounded in-memory work.
        this.executor = new ThreadPoolExecutor(
                properties.concurrency(),
                properties.concurrency(),
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.dispatchQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "photo-relay-upload");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.leaseKeeper = Executors.newScheduledThreadPool(
                Math.min(2, properties.concurrency()),
                runnable -> {
                    Thread thread = new Thread(runnable, "photo-relay-lease");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Scheduled(fixedDelayString = "${photo-relay.dispatch-interval:500}")
    public void dispatch() {
        Instant now = Instant.now();
        tasks.recoverReceived(properties.nodeId(), now);
        tasks.recoverStale(properties.nodeId(), now.minus(properties.claimTimeout()), now);
        for (PhotoRelayUploadTaskEntity terminal : tasks.findTerminalCleanup(properties.nodeId(), 100)) {
            cleanup(terminal);
        }

        int freeQueueSlots = properties.dispatchQueueCapacity() - executor.getQueue().size();
        if (freeQueueSlots <= 0) return;
        int claimLimit = Math.min(freeQueueSlots, properties.concurrency());
        for (PhotoRelayUploadTaskEntity task : persistence.claimBatch(claimLimit)) {
            try {
                executor.execute(() -> run(task));
            } catch (RejectedExecutionException exception) {
                tasks.releaseClaim(task.getId(), task.getClaimToken(), Instant.now());
            }
        }
    }

    private void run(PhotoRelayUploadTaskEntity task) {
        Path path = relay.resolve(task.getRelativePath());
        AtomicBoolean ownsClaim = new AtomicBoolean(heartbeat(task));
        if (!ownsClaim.get()) return;

        long heartbeatMillis = Math.max(100, properties.claimTimeout().toMillis() / 3);
        ScheduledFuture<?> heartbeat = leaseKeeper.scheduleAtFixedRate(() -> {
            if (!heartbeat(task)) ownsClaim.set(false);
        }, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        try {
            PhotoAssetEntity photo = photos.selectById(task.getPhotoId());
            if (photo == null) throw new IllegalStateException("photo missing");
            if (!Files.isRegularFile(path) || Files.size(path) != task.getByteSize()) {
                throw new IOException("staged file missing or truncated");
            }

            // A crash after photo finalization but before task completion must not overwrite the object.
            if (isCompleted(photo)) {
                if (tasks.succeed(task.getId(), task.getClaimToken(), Instant.now()) == 1) cleanup(task);
                return;
            }

            storage.putFile(photo.getOriginalKey(),
                photo.getDeclaredContentType(),
                photo.getByteSize(),
                path);
            if (!ownsClaim.get() || !heartbeat(task)) return;
            completion.completeRelayed(photo.getId(), task.getUploaderId());
            if (tasks.succeed(task.getId(), task.getClaimToken(), Instant.now()) == 1) cleanup(task);
        } catch (RuntimeException | IOException exception) {
            if (!ownsClaim.get()) return;
            failAttempt(task, exception);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private boolean heartbeat(PhotoRelayUploadTaskEntity task) {
        return tasks.heartbeat(task.getId(), task.getClaimToken(), Instant.now()) == 1;
    }

    private boolean isCompleted(PhotoAssetEntity photo) {
        return PhotoAssetStatus.AVAILABLE.name().equals(photo.getStatus())
                || PhotoAssetStatus.DUPLICATE.name().equals(photo.getStatus());
    }

    private void failAttempt(PhotoRelayUploadTaskEntity task, Exception exception) {
        boolean terminal = task.getAttemptCount() + 1 >= task.getMaxAttempts();
        Instant now = Instant.now();
        long multiplier = 1L << Math.min(task.getAttemptCount(), 10);
        Instant nextAttempt = now.plus(properties.retryBaseDelay().multipliedBy(multiplier));
        int changed = tasks.failAttempt(
                task.getId(),
                task.getClaimToken(),
                terminal ? "FAILED" : "RETRY_WAIT",
                nextAttempt,
                "PHOTO_RELAY_UPLOAD_FAILED",
                safeMessage(exception.getMessage()),
                now);
        if (terminal && changed == 1) cleanup(task);
        log.warn("relay upload attempt failed taskId={} terminal={}", task.getId(), terminal, exception);
    }

    private String safeMessage(String message) {
        String value = message == null ? "后端上传失败" : message;
        return value.substring(0, Math.min(500, value.length()));
    }

    private void cleanup(PhotoRelayUploadTaskEntity task) {
        Path path = relay.resolve(task.getRelativePath());
        try {
            Files.deleteIfExists(path);
            tasks.markTempDeleted(task.getId(), Instant.now());
        } catch (IOException exception) {
            log.warn("cannot clean relay temp {}", path, exception);
        }
    }

    @PreDestroy
    void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        leaseKeeper.shutdown();
    }

    int activeUploads() {
        return executor.getActiveCount();
    }
}
