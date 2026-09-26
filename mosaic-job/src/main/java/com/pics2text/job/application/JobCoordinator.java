package com.pics2text.job.application;

import com.pics2text.job.domain.JobStatus;
import com.pics2text.job.domain.JobType;
import com.pics2text.job.infrastructure.persistence.ConsumedMessageMapper;
import com.pics2text.job.infrastructure.persistence.JobAttemptMapper;
import com.pics2text.job.infrastructure.persistence.OutboxEventMapper;
import com.pics2text.job.infrastructure.persistence.ProcessingJobEntity;
import com.pics2text.job.infrastructure.persistence.ProcessingJobMapper;
import com.pics2text.job.messaging.JobMessage;
import com.pics2text.job.messaging.JobRabbitNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Service
public class JobCoordinator {
    private final ProcessingJobMapper jobMapper;
    private final JobAttemptMapper attemptMapper;
    private final ConsumedMessageMapper consumedMessageMapper;
    private final OutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;
    private final int manualRetryAttempts;

    public JobCoordinator(ProcessingJobMapper jobMapper, JobAttemptMapper attemptMapper,
            ConsumedMessageMapper consumedMessageMapper, OutboxEventMapper outboxMapper,
            ObjectMapper objectMapper,
            @Value("${jobs.retry.base-delay:5s}") Duration retryBaseDelay,
            @Value("${jobs.retry.max-delay:5m}") Duration retryMaxDelay,
            @Value("${jobs.retry.manual-attempts:5}") int manualRetryAttempts) {
        this.jobMapper = jobMapper;
        this.attemptMapper = attemptMapper;
        this.consumedMessageMapper = consumedMessageMapper;
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
        this.manualRetryAttempts = manualRetryAttempts;
    }

    @Transactional
    public ProcessingJobEntity enqueue(UUID projectId, UUID createdBy, JobType type,
            String resourceType, UUID resourceId, String payloadJson, int maxAttempts, boolean cancellable) {
        Instant now = Instant.now();
        UUID jobId = UUID.randomUUID();
        String normalizedPayload = payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson;
        jobMapper.insertJob(jobId, projectId, resourceType, resourceId, type.name(), maxAttempts,
                cancellable, normalizedPayload, createdBy, now);
        enqueueMessage(jobId, projectId, type, 1, normalizedPayload, false, 0, now);
        return jobMapper.selectById(jobId);
    }

    @Transactional
    public BeginResult begin(JobMessage message, String consumerName, String workerId) {
        if (consumedMessageMapper.exists(consumerName, message.eventId())) {
            return new BeginResult(BeginStatus.ALREADY_HANDLED, null);
        }
        Instant now = Instant.now();
        int claimed = jobMapper.claim(message.jobId(), message.attempt(), workerId, now);
        if (claimed == 0) {
            consumedMessageMapper.insert(consumerName, message.eventId(), message.jobId(), now);
            return new BeginResult(BeginStatus.NOT_RUNNABLE, null);
        }
        attemptMapper.insert(message.jobId(), message.attempt(), message.eventId(), workerId, now);
        return new BeginResult(BeginStatus.STARTED,
                new JobExecutionContext(this, message.jobId(), message.projectId(), message.type(),
                        message.attempt(), message.payloadJson(), workerId));
    }

    @Transactional
    public void succeed(JobMessage message, String consumerName, String workerId, String resultJson) {
        Instant now = Instant.now();
        jobMapper.succeed(message.jobId(), workerId, emptyJson(resultJson), now);
        attemptMapper.complete(message.jobId(), message.attempt(), workerId, "SUCCEEDED", null, now);
        consumedMessageMapper.insert(consumerName, message.eventId(), message.jobId(), now);
    }

    @Transactional
    public void cancel(JobMessage message, String consumerName, String workerId) {
        Instant now = Instant.now();
        jobMapper.completeCancellation(message.jobId(), workerId, now);
        attemptMapper.complete(message.jobId(), message.attempt(), workerId, "CANCELLED", null, now);
        consumedMessageMapper.insert(consumerName, message.eventId(), message.jobId(), now);
    }

    @Transactional
    public void fail(JobMessage message, String consumerName, String workerId, Throwable failure) {
        ProcessingJobEntity job = jobMapper.selectById(message.jobId());
        if (job == null) {
            return;
        }
        Instant now = Instant.now();
        boolean permanent = failure instanceof PermanentJobException;
        boolean exhausted = message.attempt() >= job.getMaxAttempts();
        String error = truncate(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        if (!permanent && !exhausted) {
            long delay = retryDelayMillis(message.attempt());
            jobMapper.fail(message.jobId(), workerId, JobStatus.RETRY_WAIT.name(), "RETRYABLE_FAILURE",
                    error, now.plusMillis(delay), false, now);
            attemptMapper.complete(message.jobId(), message.attempt(), workerId, "FAILED", error, now);
            enqueueMessage(message.jobId(), message.projectId(), message.type(), message.attempt() + 1,
                    message.payloadJson(), true, delay, now);
        } else {
            JobStatus status = exhausted ? JobStatus.DEAD_LETTERED : JobStatus.FAILED;
            jobMapper.fail(message.jobId(), workerId, status.name(),
                    permanent ? "PERMANENT_FAILURE" : "RETRIES_EXHAUSTED", error, null, true, now);
            attemptMapper.complete(message.jobId(), message.attempt(), workerId, status.name(), error, now);
            enqueueMessage(message.jobId(), message.projectId(), message.type(), message.attempt(),
                    message.payloadJson(), false, 0, now, JobRabbitNames.DEAD_LETTER_EXCHANGE,
                    message.type().routingKey(), "JobDeadLettered");
        }
        consumedMessageMapper.insert(consumerName, message.eventId(), message.jobId(), now);
    }

    @Transactional
    public boolean requestCancellation(UUID jobId) {
        return jobMapper.requestCancellation(jobId, Instant.now()) > 0;
    }

    @Transactional
    public boolean manualRetry(UUID jobId) {
        ProcessingJobEntity job = jobMapper.selectById(jobId);
        if (job == null || jobMapper.resetForManualRetry(jobId,
                job.getCurrentAttempt() + manualRetryAttempts, Instant.now()) == 0) {
            return false;
        }
        enqueueMessage(jobId, job.getProjectId(), JobType.valueOf(job.getType()), job.getCurrentAttempt() + 1,
                job.getPayloadJson(), false, 0, Instant.now());
        return true;
    }

    @Transactional
    public void heartbeat(UUID jobId, int attempt, String workerId, int progress) {
        Instant now = Instant.now();
        jobMapper.heartbeat(jobId, workerId, Math.max(0, Math.min(progress, 99)), now);
        attemptMapper.heartbeat(jobId, attempt, workerId, now);
    }

    public boolean cancellationRequested(UUID jobId) {
        ProcessingJobEntity job = jobMapper.selectById(jobId);
        return job != null && JobStatus.CANCELLATION_REQUESTED.name().equals(job.getStatus());
    }

    @Transactional
    public void enterNonCancellablePhase(UUID jobId, String workerId) {
        if (jobMapper.enterNonCancellablePhase(jobId, workerId, Instant.now()) == 0) {
            throw new IllegalStateException("作业当前不能进入不可取消阶段：" + jobId);
        }
    }

    public List<ProcessingJobEntity> findStale(Instant deadline, int limit) {
        return jobMapper.findStale(deadline, limit);
    }

    @Transactional
    public boolean recoverStale(ProcessingJobEntity job, Instant deadline) {
        Instant now = Instant.now();
        if (JobStatus.CANCELLATION_REQUESTED.name().equals(job.getStatus())) {
            int changed = jobMapper.recoverStaleCancellation(job.getId(), deadline, now);
            if (changed > 0) {
                attemptMapper.complete(job.getId(), job.getCurrentAttempt(), job.getWorkerId(),
                        JobStatus.CANCELLED.name(), "工作节点心跳超时", now);
            }
            return changed > 0;
        }
        boolean exhausted = job.getCurrentAttempt() >= job.getMaxAttempts();
        long delay = exhausted ? 0 : retryDelayMillis(job.getCurrentAttempt());
        int changed = jobMapper.recoverStale(job.getId(), deadline,
                exhausted ? JobStatus.DEAD_LETTERED.name() : JobStatus.RETRY_WAIT.name(),
                exhausted ? null : now.plusMillis(delay), exhausted, now);
        if (changed == 0) {
            return false;
        }
        attemptMapper.complete(job.getId(), job.getCurrentAttempt(), job.getWorkerId(),
                exhausted ? JobStatus.DEAD_LETTERED.name() : "FAILED", "工作节点心跳超时", now);
        JobType type = JobType.valueOf(job.getType());
        if (exhausted) {
            enqueueMessage(job.getId(), job.getProjectId(), type, job.getCurrentAttempt(), job.getPayloadJson(),
                    false, 0, now, JobRabbitNames.DEAD_LETTER_EXCHANGE, type.routingKey(), "JobDeadLettered");
        } else {
            enqueueMessage(job.getId(), job.getProjectId(), type, job.getCurrentAttempt() + 1,
                    job.getPayloadJson(), true, delay, now);
        }
        return true;
    }

    private void enqueueMessage(UUID jobId, UUID projectId, JobType type, int attempt,
            String payloadJson, boolean retry, long delayMillis, Instant now) {
        enqueueMessage(jobId, projectId, type, attempt, payloadJson, retry, delayMillis, now,
                retry ? JobRabbitNames.RETRY_EXCHANGE : JobRabbitNames.MAIN_EXCHANGE,
                retry ? type.routingKey() + ".retry" : type.routingKey(), "JobRequested");
    }

    private void enqueueMessage(UUID jobId, UUID projectId, JobType type, int attempt,
            String payloadJson, boolean retry, long delayMillis, Instant now,
            String exchange, String routingKey, String eventType) {
        UUID eventId = UUID.randomUUID();
        JobMessage message = new JobMessage(eventId, jobId, projectId, type, attempt, payloadJson);
        try {
            outboxMapper.insert(eventId, jobId, eventType, exchange, routingKey,
                    objectMapper.writeValueAsString(message), delayMillis, now);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化作业消息", exception);
        }
    }

    private long retryDelayMillis(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        return Math.min(retryBaseDelay.toMillis() * multiplier, retryMaxDelay.toMillis());
    }

    private static String emptyJson(String json) {
        return json == null || json.isBlank() ? "{}" : json;
    }

    private static String truncate(String value) {
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    public enum BeginStatus { STARTED, ALREADY_HANDLED, NOT_RUNNABLE }

    public record BeginResult(BeginStatus status, JobExecutionContext context) {
    }
}
