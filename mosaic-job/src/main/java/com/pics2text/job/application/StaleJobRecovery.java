package com.pics2text.job.application;

import com.pics2text.job.infrastructure.persistence.ProcessingJobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "jobs.recovery.enabled", havingValue = "true")
public class StaleJobRecovery {
    private static final Logger log = LoggerFactory.getLogger(StaleJobRecovery.class);
    private final JobCoordinator coordinator;
    private final Duration heartbeatTimeout;
    private final int batchSize;

    public StaleJobRecovery(JobCoordinator coordinator,
            @Value("${jobs.recovery.heartbeat-timeout:2m}") Duration heartbeatTimeout,
            @Value("${jobs.recovery.batch-size:50}") int batchSize) {
        this.coordinator = coordinator;
        this.heartbeatTimeout = heartbeatTimeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${jobs.recovery.interval:30000}")
    public void recover() {
        Instant deadline = Instant.now().minus(heartbeatTimeout);
        for (ProcessingJobEntity job : coordinator.findStale(deadline, batchSize)) {
            if (coordinator.recoverStale(job, deadline)) {
                log.warn("已接管心跳超时作业 jobId={}, attempt={}", job.getId(), job.getCurrentAttempt());
            }
        }
    }
}
