package com.pics2text.api.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "photo-relay")
public record PhotoRelayProperties(Path tempDirectory, String nodeId, int concurrency,
        int dispatchQueueCapacity, int maxPendingTasks, long maxStagedBytes, int maxAttempts,
        Duration claimTimeout, Duration retryBaseDelay) { }
