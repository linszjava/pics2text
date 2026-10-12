package com.pics2text.api.application;

import com.pics2text.api.config.ObjectStorageProperties;
import com.pics2text.api.config.PhotoRelayProperties;
import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskEntity;
import com.pics2text.api.infrastructure.persistence.PhotoRelayUploadTaskMapper;
import com.pics2text.identity.domain.User;
import com.pics2text.photoasset.domain.AnalysisStatus;
import com.pics2text.photoasset.domain.AuthorizationStatus;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import com.pics2text.photoasset.domain.ReviewStatus;
import com.pics2text.photoasset.domain.UploadMode;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PhotoRelayPersistenceService {
    private final PhotoAssetMapper photos;
    private final PhotoRelayUploadTaskMapper tasks;
    private final ObjectKeyPolicy keys;
    private final ObjectStorageProperties storage;
    private final PhotoRelayProperties relay;
    private final AuditService audit;

    PhotoRelayPersistenceService(PhotoAssetMapper photos, PhotoRelayUploadTaskMapper tasks,
            ObjectKeyPolicy keys, ObjectStorageProperties storage, PhotoRelayProperties relay, AuditService audit) {
        this.photos = photos;
        this.tasks = tasks;
        this.keys = keys;
        this.storage = storage;
        this.relay = relay;
        this.audit = audit;
    }

    @Transactional
    Created create(UUID projectId, User actor, String filename, String contentType, long size,
            String sha256, boolean authorized, String relativePath) {
        PhotoAssetEntity duplicate = photos.findAvailableDuplicate(projectId, sha256);
        if (duplicate != null) return new Created(null, duplicate, true);
        UUID photoId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        var objectKeys = keys.keys(projectId, photoId, contentType);
        Instant now = Instant.now();
        PhotoAssetEntity photo = new PhotoAssetEntity();
        photo.setId(photoId);
        photo.setProjectId(projectId);
        photo.setUploaderId(actor.id());
        photo.setOriginalFilename(filename);
        photo.setDeclaredContentType(contentType);
        photo.setByteSize(size);
        photo.setSha256(sha256);
        photo.setAuthorizationStatus(authorized
                ? AuthorizationStatus.GRANTED.name() : AuthorizationStatus.PENDING.name());
        photo.setReviewStatus(ReviewStatus.PENDING.name());
        photo.setStatus(PhotoAssetStatus.UPLOADING.name());
        photo.setAnalysisStatus(AnalysisStatus.PENDING.name());
        photo.setUploadMode(UploadMode.SINGLE.name());
        photo.setExpectedPartCount(1);
        photo.setOriginalKey(objectKeys.original());
        photo.setThumbnailKey(objectKeys.thumbnail());
        photo.setPreviewKey(objectKeys.preview());
        photo.setUploadExpiresAt(now.plus(storage.uploadTtl()));
        photo.setCreatedAt(now);
        photo.setUpdatedAt(now);
        photos.insert(photo);
        PhotoRelayUploadTaskEntity task = new PhotoRelayUploadTaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setPhotoId(photoId);
        task.setUploaderId(actor.id());
        task.setStorageNode(relay.nodeId());
        task.setRelativePath(relativePath);
        task.setByteSize(size);
        task.setStatus("RECEIVED");
        task.setAttemptCount(0);
        task.setMaxAttempts(relay.maxAttempts());
        task.setNextAttemptAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        tasks.insert(task);
        audit.record(actor.id(), "PHOTO_RELAY_RECEIVED", "PHOTO_ASSET", photoId.toString(), true);
        return new Created(task, photo, false);
    }

    @Transactional
    List<PhotoRelayUploadTaskEntity> claimBatch(int limit) {
        Instant now = Instant.now();
        var found = tasks.findClaimCandidates(relay.nodeId(), now, limit);
        return found.stream().filter(task -> {
            UUID token = UUID.randomUUID();
            task.setClaimToken(token);
            return tasks.claim(task.getId(), token, now) == 1;
        }).toList();
    }

    record Created(PhotoRelayUploadTaskEntity task, PhotoAssetEntity photo, boolean duplicate) { }
}
