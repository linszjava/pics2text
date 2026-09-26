package com.pics2text.job.application;

import com.pics2text.job.domain.JobType;

import java.util.UUID;

public final class JobExecutionContext {
    private final JobCoordinator coordinator;
    private final UUID jobId;
    private final UUID projectId;
    private final JobType type;
    private final int attempt;
    private final String payloadJson;
    private final String workerId;

    JobExecutionContext(JobCoordinator coordinator, UUID jobId, UUID projectId, JobType type,
            int attempt, String payloadJson, String workerId) {
        this.coordinator = coordinator;
        this.jobId = jobId;
        this.projectId = projectId;
        this.type = type;
        this.attempt = attempt;
        this.payloadJson = payloadJson;
        this.workerId = workerId;
    }

    public UUID jobId() { return jobId; }
    public UUID projectId() { return projectId; }
    public JobType type() { return type; }
    public int attempt() { return attempt; }
    public String payloadJson() { return payloadJson; }
    public void heartbeat(int progress) { coordinator.heartbeat(jobId, attempt, workerId, progress); }
    public boolean cancellationRequested() { return coordinator.cancellationRequested(jobId); }
    public void enterNonCancellablePhase() { coordinator.enterNonCancellablePhase(jobId, workerId); }
}
