package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.job.application.JobCoordinator;
import com.pics2text.job.domain.JobType;
import com.pics2text.job.infrastructure.persistence.ProcessingJobEntity;
import com.pics2text.job.infrastructure.persistence.ProcessingJobMapper;
import com.pics2text.project.domain.ProjectPermission;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JobManagementService {
    private final ProjectAccessService projectAccessService;
    private final JobCoordinator coordinator;
    private final ProcessingJobMapper jobMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public JobManagementService(ProjectAccessService projectAccessService, JobCoordinator coordinator,
            ProcessingJobMapper jobMapper, AuditService auditService, ObjectMapper objectMapper) {
        this.projectAccessService = projectAccessService;
        this.coordinator = coordinator;
        this.jobMapper = jobMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public ProcessingJobEntity createProbe(UUID projectId, int failUntilAttempt, long durationMillis,
            int maxAttempts) {
        var user = projectAccessService.require(projectId, ProjectPermission.MANAGE_PROJECT);
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "failUntilAttempt", failUntilAttempt,
                    "durationMillis", durationMillis));
            ProcessingJobEntity job = coordinator.enqueue(projectId, user.id(), JobType.INFRASTRUCTURE_PROBE,
                    "PROJECT", projectId, payload, maxAttempts, true);
            auditService.record(user.id(), "JOB_PROBE_CREATE", "PROCESSING_JOB", job.getId().toString(), true);
            return job;
        } catch (JacksonException exception) {
            throw new ApiException("JOB_PAYLOAD_INVALID", "无法创建探针作业", HttpStatus.BAD_REQUEST, exception);
        }
    }

    public List<ProcessingJobEntity> list(UUID projectId, int page, int size) {
        projectAccessService.require(projectId, ProjectPermission.VIEW);
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(page, 0);
        return jobMapper.listByProject(projectId, safeSize, (long) safePage * safeSize);
    }

    public ProcessingJobEntity get(UUID projectId, UUID jobId) {
        projectAccessService.require(projectId, ProjectPermission.VIEW);
        ProcessingJobEntity job = jobMapper.selectById(jobId);
        if (job == null || !projectId.equals(job.getProjectId())) {
            throw new ApiException("JOB_NOT_FOUND", "作业不存在", HttpStatus.NOT_FOUND);
        }
        return job;
    }

    public ProcessingJobEntity cancel(UUID projectId, UUID jobId) {
        var user = projectAccessService.require(projectId, ProjectPermission.MANAGE_PROJECT);
        ProcessingJobEntity job = getInProject(projectId, jobId);
        if (!Boolean.TRUE.equals(job.getCancellable())) {
            throw new ApiException("JOB_NOT_CANCELLABLE", "作业已进入不可取消阶段", HttpStatus.CONFLICT);
        }
        if (!coordinator.requestCancellation(jobId)) {
            throw new ApiException("JOB_STATE_CONFLICT", "当前作业状态不能取消", HttpStatus.CONFLICT);
        }
        auditService.record(user.id(), "JOB_CANCEL", "PROCESSING_JOB", jobId.toString(), true);
        return jobMapper.selectById(jobId);
    }

    public ProcessingJobEntity retry(UUID projectId, UUID jobId) {
        var user = projectAccessService.require(projectId, ProjectPermission.MANAGE_PROJECT);
        getInProject(projectId, jobId);
        if (!coordinator.manualRetry(jobId)) {
            throw new ApiException("JOB_STATE_CONFLICT", "仅失败、死信或已取消的作业可以手动重试", HttpStatus.CONFLICT);
        }
        auditService.record(user.id(), "JOB_MANUAL_RETRY", "PROCESSING_JOB", jobId.toString(), true);
        return jobMapper.selectById(jobId);
    }

    private ProcessingJobEntity getInProject(UUID projectId, UUID jobId) {
        ProcessingJobEntity job = jobMapper.selectById(jobId);
        if (job == null || !projectId.equals(job.getProjectId())) {
            throw new ApiException("JOB_NOT_FOUND", "作业不存在", HttpStatus.NOT_FOUND);
        }
        return job;
    }
}
