package com.pics2text.api.application;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PhotoRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(PhotoRetentionJob.class);
    private final PhotoAssetMapper mapper;
    private final PhotoObjectDeletionService deletionService;
    private final Duration verificationLease;

    public PhotoRetentionJob(PhotoAssetMapper mapper, PhotoObjectDeletionService deletionService,
            @Value("${object-storage.verification-lease:30m}") Duration verificationLease) {
        this.mapper = mapper;
        this.deletionService = deletionService;
        this.verificationLease = verificationLease;
    }

    @Scheduled(fixedDelayString = "${object-storage.cleanup-interval:PT1H}")
    public void cleanup() {
        Instant now = Instant.now();
        mapper.findExpiredUploads(now, now.minus(verificationLease), 100)
                .forEach(asset -> cleanup(asset, true));
        mapper.findExpiredRetention(now, 100).forEach(asset -> cleanup(asset, false));
    }

    private void cleanup(PhotoAssetEntity asset, boolean expiredUpload) {
        try {
            Instant completedAt = now();
            if (expiredUpload && PhotoAssetStatus.UPLOADING.name().equals(asset.getStatus())) {
                if (mapper.expireUploading(asset.getId(), completedAt) == 0) return;
            } else if (expiredUpload && PhotoAssetStatus.VERIFYING.name().equals(asset.getStatus())) {
                if (asset.getVerificationToken() == null || mapper.failVerification(asset.getId(),
                        asset.getVerificationToken(), "UPLOAD_EXPIRED", "上传验真租约已过期", completedAt) == 0) {
                    return;
                }
            }
            deletionService.deleteAll(asset);
            if (expiredUpload || PhotoAssetStatus.FAILED.name().equals(asset.getStatus())
                    || PhotoAssetStatus.DUPLICATE.name().equals(asset.getStatus())) {
                mapper.markObjectsDeleted(asset.getId(), completedAt);
            } else {
                mapper.markDeleted(asset.getId(), completedAt, "RETENTION_EXPIRED", "撤回照片保留期已结束");
            }
        } catch (RuntimeException exception) {
            log.warn("清理照片对象失败 photoId={}", asset.getId(), exception);
        }
    }

    private Instant now() { return Instant.now(); }
}
