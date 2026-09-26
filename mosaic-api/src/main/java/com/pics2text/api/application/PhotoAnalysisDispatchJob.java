package com.pics2text.api.application;

import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.job.application.JobCoordinator;
import com.pics2text.job.domain.JobType;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "jobs.analysis-dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class PhotoAnalysisDispatchJob {
    private final PhotoAssetMapper photos;
    private final JobCoordinator jobs;
    private final ObjectMapper objectMapper;

    public PhotoAnalysisDispatchJob(PhotoAssetMapper photos, JobCoordinator jobs, ObjectMapper objectMapper) {
        this.photos = photos;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${jobs.analysis-dispatch.interval:30s}")
    @Transactional
    public void dispatchPendingPhotos() {
        for (var photo : photos.findAnalysisDispatchCandidates(50)) {
            try {
                jobs.enqueue(photo.getProjectId(), photo.getUploaderId(), JobType.PHOTO_ANALYSIS,
                        "PHOTO_ASSET", photo.getId(),
                        objectMapper.writeValueAsString(Map.of("photoId", photo.getId())), 5, true);
            } catch (JacksonException exception) {
                throw new IllegalStateException("无法序列化照片分析任务", exception);
            }
        }
    }
}
