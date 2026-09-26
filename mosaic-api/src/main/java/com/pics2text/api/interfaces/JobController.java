package com.pics2text.api.interfaces;

import com.pics2text.api.application.JobEventStreamService;
import com.pics2text.api.application.JobManagementService;
import com.pics2text.job.infrastructure.persistence.ProcessingJobEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/jobs")
public class JobController {
    private final JobManagementService service;
    private final JobEventStreamService streamService;

    public JobController(JobManagementService service, JobEventStreamService streamService) {
        this.service = service;
        this.streamService = streamService;
    }

    @PostMapping("/probes")
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createProbe(@PathVariable UUID projectId,
            @Valid @RequestBody ProbeRequest request) {
        return JobResponse.from(service.createProbe(projectId, request.failUntilAttempt(),
                request.durationMillis(), request.maxAttempts()));
    }

    @GetMapping
    public List<JobResponse> list(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(projectId, page, size).stream().map(JobResponse::from).toList();
    }

    @GetMapping("/{jobId}")
    public JobResponse get(@PathVariable UUID projectId, @PathVariable UUID jobId) {
        return JobResponse.from(service.get(projectId, jobId));
    }

    @PostMapping("/{jobId}/cancel")
    public JobResponse cancel(@PathVariable UUID projectId, @PathVariable UUID jobId) {
        return JobResponse.from(service.cancel(projectId, jobId));
    }

    @PostMapping("/{jobId}/retry")
    public JobResponse retry(@PathVariable UUID projectId, @PathVariable UUID jobId) {
        return JobResponse.from(service.retry(projectId, jobId));
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID projectId) {
        return streamService.subscribe(projectId);
    }

    public record ProbeRequest(@Min(0) @Max(20) int failUntilAttempt,
            @Min(0) @Max(60000) long durationMillis,
            @Min(1) @Max(20) int maxAttempts) {
    }

    public record JobResponse(UUID id, UUID projectId, String resourceType, UUID resourceId,
            String type, String status, int progress, int currentAttempt, int maxAttempts,
            Instant nextAttemptAt, Instant heartbeatAt, Instant startedAt, Instant completedAt,
            Instant cancellationRequestedAt, boolean cancellable, String resultJson,
            String errorCode, String errorMessage, Instant createdAt, Instant updatedAt) {
        static JobResponse from(ProcessingJobEntity job) {
            return new JobResponse(job.getId(), job.getProjectId(), job.getResourceType(), job.getResourceId(),
                    job.getType(), job.getStatus(), job.getProgress(), job.getCurrentAttempt(),
                    job.getMaxAttempts(), job.getNextAttemptAt(), job.getHeartbeatAt(), job.getStartedAt(),
                    job.getCompletedAt(), job.getCancellationRequestedAt(), job.getCancellable(),
                    job.getResultJson(), job.getErrorCode(), job.getErrorMessage(),
                    job.getCreatedAt(), job.getUpdatedAt());
        }
    }
}
