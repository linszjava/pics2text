package com.pics2text.api.application;

import com.pics2text.job.infrastructure.persistence.ProcessingJobMapper;
import com.pics2text.project.domain.ProjectPermission;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class JobEventStreamService {
    private final ProjectAccessService projectAccessService;
    private final ProcessingJobMapper jobMapper;
    private final ConcurrentMap<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();

    public JobEventStreamService(ProjectAccessService projectAccessService, ProcessingJobMapper jobMapper) {
        this.projectAccessService = projectAccessService;
        this.jobMapper = jobMapper;
    }

    public SseEmitter subscribe(UUID projectId) {
        projectAccessService.require(projectId, ProjectPermission.VIEW);
        UUID id = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
        Subscription subscription = new Subscription(projectId, emitter, Instant.now().minusSeconds(1));
        subscriptions.put(id, subscription);
        emitter.onCompletion(() -> subscriptions.remove(id));
        emitter.onTimeout(() -> subscriptions.remove(id));
        emitter.onError(ignored -> subscriptions.remove(id));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException exception) {
            subscriptions.remove(id);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @Scheduled(fixedDelayString = "${jobs.sse.poll-interval:1000}")
    public void publishUpdates() {
        subscriptions.forEach((id, subscription) -> {
            Instant latest = subscription.after();
            try {
                var jobs = jobMapper.listUpdated(subscription.projectId(), subscription.after(), 100);
                for (var job : jobs) {
                    subscription.emitter().send(SseEmitter.event()
                            .id(job.getUpdatedAt().toString() + ":" + job.getId())
                            .name("job-updated")
                            .data(JobUpdate.from(job)));
                    if (job.getUpdatedAt().isAfter(latest)) {
                        latest = job.getUpdatedAt();
                    }
                }
                subscription.after(latest);
            } catch (Exception exception) {
                subscriptions.remove(id);
                subscription.emitter().complete();
            }
        });
    }

    private record JobUpdate(UUID id, String type, String status, int progress, int currentAttempt,
            int maxAttempts, String errorCode, String errorMessage, Instant updatedAt) {
        private static JobUpdate from(com.pics2text.job.infrastructure.persistence.ProcessingJobEntity job) {
            return new JobUpdate(job.getId(), job.getType(), job.getStatus(), job.getProgress(),
                    job.getCurrentAttempt(), job.getMaxAttempts(), job.getErrorCode(),
                    job.getErrorMessage(), job.getUpdatedAt());
        }
    }

    private static final class Subscription {
        private final UUID projectId;
        private final SseEmitter emitter;
        private volatile Instant after;

        private Subscription(UUID projectId, SseEmitter emitter, Instant after) {
            this.projectId = projectId;
            this.emitter = emitter;
            this.after = after;
        }

        UUID projectId() { return projectId; }
        SseEmitter emitter() { return emitter; }
        Instant after() { return after; }
        void after(Instant value) { after = value; }
    }
}
