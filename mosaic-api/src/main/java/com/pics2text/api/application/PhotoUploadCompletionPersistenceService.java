package com.pics2text.api.application;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.job.application.JobCoordinator;
import com.pics2text.job.domain.JobType;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class PhotoUploadCompletionPersistenceService {
    private final PhotoAssetMapper mapper;
    private final JobCoordinator jobs;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    PhotoUploadCompletionPersistenceService(PhotoAssetMapper mapper, JobCoordinator jobs,
            ObjectMapper objectMapper, AuditService audit) {
        this.mapper = mapper;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional
    PhotoAssetEntity finalizeVerified(UUID photoId, UUID verificationToken,
            PhotoObjectVerifier.VerificationResult verification, UUID actorId) {
        PhotoAssetEntity asset = mapper.selectById(photoId);
        if (asset == null || !PhotoAssetStatus.VERIFYING.name().equals(asset.getStatus())
                || !verificationToken.equals(asset.getVerificationToken())) {
            throw new IllegalStateException("照片不再处于验真状态");
        }
        mapper.lockProjectUploadCompletion(asset.getProjectId());
        PhotoAssetEntity duplicate = mapper.findAvailableDuplicate(asset.getProjectId(), asset.getSha256());
        Instant now = Instant.now();
        int changed;
        if (duplicate != null && !duplicate.getId().equals(asset.getId())) {
            changed = mapper.markDuplicateFromVerification(
                    asset.getId(), verificationToken, duplicate.getId(), now);
            asset.setStatus(PhotoAssetStatus.DUPLICATE.name());
            asset.setDuplicateOf(duplicate.getId());
            asset.setFailureCode("PHOTO_DUPLICATE");
            asset.setFailureMessage("项目中已存在相同照片");
        } else {
            changed = mapper.markAvailableFromVerification(asset.getId(), verificationToken,
                    verification.contentType(), verification.width(), verification.height(), now);
            asset.setDetectedContentType(verification.contentType());
            asset.setWidth(verification.width());
            asset.setHeight(verification.height());
            asset.setStatus(PhotoAssetStatus.AVAILABLE.name());
            asset.setAvailableAt(now);
        }
        if (changed != 1) {
            throw new IllegalStateException("照片验真认领已失效");
        }
        asset.setVerificationToken(null);
        asset.setUpdatedAt(now);
        if (PhotoAssetStatus.AVAILABLE.name().equals(asset.getStatus())) {
            enqueueAnalysis(asset, actorId);
        }
        audit.record(actorId, "PHOTO_UPLOAD_COMPLETE", "PHOTO_ASSET", photoId.toString(), true);
        return asset;
    }

    @Transactional
    boolean failVerification(UUID photoId, UUID verificationToken, String code, String message,
            UUID actorId) {
        String safe = message == null ? code : message.substring(0, Math.min(message.length(), 500));
        int changed = mapper.failVerification(photoId, verificationToken, code, safe, Instant.now());
        if (changed > 0) {
            audit.record(actorId, "PHOTO_UPLOAD_COMPLETE", "PHOTO_ASSET", photoId.toString(), false);
        }
        return changed > 0;
    }

    private void enqueueAnalysis(PhotoAssetEntity asset, UUID actorId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("photoId", asset.getId()));
            jobs.enqueue(asset.getProjectId(), actorId, JobType.PHOTO_ANALYSIS,
                    "PHOTO_ASSET", asset.getId(), payload, 5, true);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法创建照片分析任务", exception);
        }
    }
}
