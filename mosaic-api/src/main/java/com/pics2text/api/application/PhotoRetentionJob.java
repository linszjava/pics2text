package com.pics2text.api.application;

import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PhotoRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(PhotoRetentionJob.class);
    private final PhotoAssetMapper mapper;
    private final PhotoObjectDeletionService deletionService;

    public PhotoRetentionJob(PhotoAssetMapper mapper, PhotoObjectDeletionService deletionService) {
        this.mapper = mapper;
        this.deletionService = deletionService;
    }

    @Scheduled(fixedDelayString = "${object-storage.cleanup-interval:PT1H}")
    public void cleanup() {
        Instant now = Instant.now();
        mapper.findExpiredUploads(now, 100).forEach(asset -> cleanup(asset, true));
        mapper.findExpiredRetention(now, 100).forEach(asset -> cleanup(asset, false));
    }

    private void cleanup(PhotoAssetEntity asset, boolean expiredUpload) {
        try {
            deletionService.deleteAll(asset);
            Instant completedAt = now();
            if (PhotoAssetStatus.FAILED.name().equals(asset.getStatus())) {
                mapper.markObjectsDeleted(asset.getId(), completedAt);
            } else if (expiredUpload) {
                mapper.updateFailure(asset.getId(), PhotoAssetStatus.FAILED.name(), "UPLOAD_EXPIRED",
                        "上传会话已过期", completedAt);
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
