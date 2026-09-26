package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.config.ObjectStorageProperties;
import com.pics2text.api.infrastructure.persistence.PhotoAssetEntity;
import com.pics2text.api.infrastructure.persistence.PhotoAssetMapper;
import com.pics2text.api.infrastructure.persistence.ProjectEntity;
import com.pics2text.api.infrastructure.storage.ObjectStorageGateway;
import com.pics2text.identity.domain.User;
import com.pics2text.job.application.JobCoordinator;
import com.pics2text.job.domain.JobType;
import com.pics2text.photoasset.domain.AuthorizationStatus;
import com.pics2text.photoasset.domain.AnalysisStatus;
import com.pics2text.photoasset.domain.PhotoAssetStatus;
import com.pics2text.photoasset.domain.ReviewStatus;
import com.pics2text.photoasset.domain.UploadMode;
import com.pics2text.photoasset.infrastructure.persistence.PhotoAnalysisEntity;
import com.pics2text.photoasset.infrastructure.persistence.PhotoAnalysisMapper;
import com.pics2text.photoasset.infrastructure.persistence.PhotoVariantEntity;
import com.pics2text.project.domain.ProjectPermission;
import com.pics2text.project.domain.ProjectRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PhotoAssetService {
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final PhotoAssetMapper mapper;
    private final ProjectAccessService access;
    private final ObjectStorageGateway storage;
    private final ObjectStorageProperties properties;
    private final ObjectKeyPolicy keyPolicy;
    private final PhotoObjectVerifier verifier;
    private final AuditService audit;
    private final JobCoordinator jobs;
    private final ObjectMapper objectMapper;
    private final PhotoAnalysisMapper analysisMapper;

    public PhotoAssetService(PhotoAssetMapper mapper, ProjectAccessService access,
            ObjectStorageGateway storage, ObjectStorageProperties properties,
            ObjectKeyPolicy keyPolicy, PhotoObjectVerifier verifier, AuditService audit,
            JobCoordinator jobs, ObjectMapper objectMapper, PhotoAnalysisMapper analysisMapper) {
        this.mapper = mapper;
        this.access = access;
        this.storage = storage;
        this.properties = properties;
        this.keyPolicy = keyPolicy;
        this.verifier = verifier;
        this.audit = audit;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.analysisMapper = analysisMapper;
    }

    @Transactional
    public InitiatedUpload initiate(UUID projectId, String filename, String contentType,
            long byteSize, String sha256, boolean authorizationGranted) {
        User actor = access.require(projectId, ProjectPermission.UPLOAD_PHOTOS);
        ProjectEntity project = access.requireProject(projectId);
        if (!"ACTIVE".equals(project.getStatus())) {
            throw conflict("PROJECT_ARCHIVED", "已归档项目不能上传照片");
        }
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String normalizedHash = sha256 == null ? "" : sha256.toLowerCase(Locale.ROOT);
        if (filename == null || filename.isBlank() || filename.length() > 255) {
            throw bad("PHOTO_FILENAME_INVALID", "文件名不能为空且不能超过255个字符");
        }
        if (!CONTENT_TYPES.contains(normalizedType)) {
            throw bad("PHOTO_CONTENT_TYPE_UNSUPPORTED", "仅支持 JPEG、PNG 和 WebP 图片");
        }
        if (byteSize <= 0 || byteSize > project.getMaxPhotoBytes()) {
            throw bad("PHOTO_SIZE_INVALID", "照片大小超出项目限制");
        }
        if (!SHA256.matcher(normalizedHash).matches()) {
            throw bad("PHOTO_SHA256_INVALID", "SHA-256 必须是64位十六进制字符串");
        }
        PhotoAssetEntity duplicate = mapper.findAvailableDuplicate(projectId, normalizedHash);
        if (duplicate != null) return new InitiatedUpload(duplicate, true, null);

        UUID id = UUID.randomUUID();
        var keys = keyPolicy.keys(projectId, id, normalizedType);
        UploadMode mode = byteSize >= properties.multipartThreshold() ? UploadMode.MULTIPART : UploadMode.SINGLE;
        int parts = mode == UploadMode.MULTIPART
                ? Math.toIntExact((byteSize + properties.multipartPartSize() - 1) / properties.multipartPartSize()) : 1;
        String uploadId = mode == UploadMode.MULTIPART
                ? storage.createMultipart(keys.original(), normalizedType) : null;
        registerMultipartRollbackCleanup(keys.original(), uploadId);
        Instant now = Instant.now();
        PhotoAssetEntity asset = new PhotoAssetEntity();
        asset.setId(id);
        asset.setProjectId(projectId);
        asset.setUploaderId(actor.id());
        asset.setOriginalFilename(filename.trim());
        asset.setDeclaredContentType(normalizedType);
        asset.setByteSize(byteSize);
        asset.setSha256(normalizedHash);
        asset.setAuthorizationStatus(authorizationGranted ? AuthorizationStatus.GRANTED.name() : AuthorizationStatus.PENDING.name());
        asset.setReviewStatus(ReviewStatus.PENDING.name());
        asset.setStatus(PhotoAssetStatus.UPLOADING.name());
        asset.setAnalysisStatus(AnalysisStatus.PENDING.name());
        asset.setUploadMode(mode.name());
        asset.setMultipartUploadId(uploadId);
        asset.setPartSize(mode == UploadMode.MULTIPART ? properties.multipartPartSize() : null);
        asset.setExpectedPartCount(parts);
        asset.setOriginalKey(keys.original());
        asset.setThumbnailKey(keys.thumbnail());
        asset.setPreviewKey(keys.preview());
        asset.setUploadExpiresAt(now.plus(properties.uploadTtl()));
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        mapper.insert(asset);
        ObjectStorageGateway.PresignedRequest direct = mode == UploadMode.SINGLE
                ? storage.presignPut(keys.original(), normalizedType, byteSize) : null;
        audit.record(actor.id(), "PHOTO_UPLOAD_INITIATE", "PHOTO_ASSET", id.toString(), true);
        return new InitiatedUpload(asset, false, direct);
    }

    public ObjectStorageGateway.PresignedRequest presignPart(UUID projectId, UUID photoId, int partNumber) {
        User actor = access.require(projectId, ProjectPermission.UPLOAD_PHOTOS);
        PhotoAssetEntity asset = require(projectId, photoId);
        ensureUploadOwnerOrAdministrator(asset, actor);
        ensureUploading(asset);
        if (!UploadMode.MULTIPART.name().equals(asset.getUploadMode())) {
            throw bad("UPLOAD_NOT_MULTIPART", "该上传不是分片上传");
        }
        if (partNumber < 1 || partNumber > asset.getExpectedPartCount()) {
            throw bad("UPLOAD_PART_INVALID", "分片编号超出范围");
        }
        return storage.presignPart(asset.getOriginalKey(), asset.getMultipartUploadId(), partNumber);
    }

    public UploadProgress progress(UUID projectId, UUID photoId) {
        access.require(projectId, ProjectPermission.VIEW);
        PhotoAssetEntity asset = require(projectId, photoId);
        if (!PhotoAssetStatus.UPLOADING.name().equals(asset.getStatus())) {
            return new UploadProgress(asset.getStatus(), asset.getByteSize(), asset.getByteSize(),
                    asset.getExpectedPartCount(), asset.getExpectedPartCount(), asset.getFailureCode(), asset.getFailureMessage());
        }
        if (UploadMode.MULTIPART.name().equals(asset.getUploadMode())) {
            var parts = storage.listParts(asset.getOriginalKey(), asset.getMultipartUploadId());
            long bytes = parts.stream().mapToLong(ObjectStorageGateway.UploadedPart::size).sum();
            return new UploadProgress(asset.getStatus(), bytes, asset.getByteSize(), parts.size(),
                    asset.getExpectedPartCount(), null, null);
        }
        return new UploadProgress(asset.getStatus(), 0, asset.getByteSize(), 0, 1, null, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PhotoAssetEntity complete(UUID projectId, UUID photoId,
            List<ObjectStorageGateway.CompletedUploadPart> parts) {
        User actor = access.require(projectId, ProjectPermission.UPLOAD_PHOTOS);
        PhotoAssetEntity asset = require(projectId, photoId);
        ensureUploadOwnerOrAdministrator(asset, actor);
        ensureUploading(asset);
        try {
            if (UploadMode.MULTIPART.name().equals(asset.getUploadMode())) {
                validateParts(parts, asset.getExpectedPartCount());
                storage.completeMultipart(asset.getOriginalKey(), asset.getMultipartUploadId(), parts);
            } else if (parts != null && !parts.isEmpty()) {
                throw bad("UPLOAD_PARTS_UNEXPECTED", "单段上传不接受分片清单");
            }
            asset.setStatus(PhotoAssetStatus.VERIFYING.name());
            asset.setUpdatedAt(Instant.now());
            mapper.updateById(asset);
            var result = verifier.verify(asset.getOriginalKey(), asset.getByteSize(), asset.getSha256());
            if (!asset.getDeclaredContentType().equals(result.contentType())) {
                throw new ApiException("PHOTO_CONTENT_TYPE_MISMATCH", "文件格式与声明的 Content-Type 不一致",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            PhotoAssetEntity duplicate = mapper.findAvailableDuplicate(projectId, asset.getSha256());
            if (duplicate != null && !duplicate.getId().equals(asset.getId())) {
                storage.delete(asset.getOriginalKey());
                asset.setStatus(PhotoAssetStatus.DUPLICATE.name());
                asset.setDuplicateOf(duplicate.getId());
                asset.setFailureCode("PHOTO_DUPLICATE");
                asset.setFailureMessage("项目中已存在相同照片");
            } else {
                asset.setDetectedContentType(result.contentType());
                asset.setWidth(result.width());
                asset.setHeight(result.height());
                asset.setStatus(PhotoAssetStatus.AVAILABLE.name());
                asset.setAvailableAt(Instant.now());
            }
            asset.setUpdatedAt(Instant.now());
            mapper.updateById(asset);
            if (PhotoAssetStatus.AVAILABLE.name().equals(asset.getStatus())) {
                enqueueAnalysis(asset, actor.id());
            }
            audit.record(actor.id(), "PHOTO_UPLOAD_COMPLETE", "PHOTO_ASSET", photoId.toString(), true);
            return asset;
        } catch (ApiException exception) {
            fail(asset, exception.code(), exception.getMessage(), true);
            audit.record(actor.id(), "PHOTO_UPLOAD_COMPLETE", "PHOTO_ASSET", photoId.toString(), false);
            throw exception;
        } catch (RuntimeException exception) {
            fail(asset, "PHOTO_STORAGE_ERROR", "对象存储操作失败", true);
            audit.record(actor.id(), "PHOTO_UPLOAD_COMPLETE", "PHOTO_ASSET", photoId.toString(), false);
            throw new ApiException("PHOTO_STORAGE_ERROR", "对象存储操作失败", HttpStatus.BAD_GATEWAY, exception);
        }
    }

    public List<PhotoAssetEntity> list(UUID projectId, int page, int size) {
        access.require(projectId, ProjectPermission.VIEW);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        return mapper.listByProject(projectId, safeSize, (long) safePage * safeSize);
    }

    public PhotoAssetEntity get(UUID projectId, UUID photoId) {
        access.require(projectId, ProjectPermission.VIEW);
        return require(projectId, photoId);
    }

    public AnalysisResult analysis(UUID projectId, UUID photoId) {
        access.require(projectId, ProjectPermission.VIEW);
        require(projectId, photoId);
        PhotoAnalysisEntity result = analysisMapper.findAnalysis(photoId);
        if (result == null) {
            throw new ApiException("PHOTO_ANALYSIS_NOT_READY", "照片分析结果尚未生成", HttpStatus.NOT_FOUND);
        }
        return new AnalysisResult(result, analysisMapper.listVariants(photoId));
    }

    @Transactional
    public PhotoAssetEntity review(UUID projectId, UUID photoId, ReviewStatus decision, String note) {
        User actor = access.require(projectId, ProjectPermission.REVIEW_PHOTOS);
        if (decision == null || decision == ReviewStatus.PENDING) {
            throw bad("PHOTO_REVIEW_DECISION_INVALID", "人工审核只能选择通过或拒绝");
        }
        String normalizedNote = note == null ? "" : note.trim();
        if (normalizedNote.isBlank() || normalizedNote.length() > 500) {
            throw bad("PHOTO_REVIEW_NOTE_INVALID", "审核说明不能为空且不能超过500个字符");
        }
        require(projectId, photoId);
        if (analysisMapper.manualReview(projectId, photoId, decision.name(), actor.id(),
                normalizedNote, Instant.now()) == 0) {
            throw conflict("PHOTO_REVIEW_STATE_INVALID", "仅可审核已完成分析的可用照片");
        }
        audit.record(actor.id(), "PHOTO_MANUAL_REVIEW_" + decision.name(),
                "PHOTO_ASSET", photoId.toString(), true);
        return require(projectId, photoId);
    }

    public ObjectStorageGateway.PresignedRequest download(UUID projectId, UUID photoId) {
        access.require(projectId, ProjectPermission.VIEW);
        PhotoAssetEntity asset = require(projectId, photoId);
        if (!PhotoAssetStatus.AVAILABLE.name().equals(asset.getStatus())) {
            throw conflict("PHOTO_NOT_AVAILABLE", "照片当前不可下载");
        }
        return storage.presignGet(asset.getOriginalKey(), asset.getOriginalFilename());
    }

    @Transactional
    public PhotoAssetEntity withdraw(UUID projectId, UUID photoId) {
        User actor = access.require(projectId, ProjectPermission.MANAGE_PHOTOS);
        PhotoAssetEntity asset = require(projectId, photoId);
        ProjectEntity project = access.requireProject(projectId);
        if (!PhotoAssetStatus.AVAILABLE.name().equals(asset.getStatus())) {
            throw conflict("PHOTO_NOT_AVAILABLE", "仅可撤回可用照片");
        }
        Instant now = Instant.now();
        asset.setStatus(PhotoAssetStatus.WITHDRAWN.name());
        asset.setAuthorizationStatus(AuthorizationStatus.REVOKED.name());
        asset.setWithdrawnAt(now);
        asset.setRetentionUntil(now.plus(project.getRetentionDays(), ChronoUnit.DAYS));
        asset.setUpdatedAt(now);
        mapper.updateById(asset);
        audit.record(actor.id(), "PHOTO_WITHDRAW", "PHOTO_ASSET", photoId.toString(), true);
        return asset;
    }

    @Transactional
    public void delete(UUID projectId, UUID photoId) {
        User actor = access.require(projectId, ProjectPermission.MANAGE_PROJECT);
        PhotoAssetEntity asset = require(projectId, photoId);
        cleanupStorage(asset);
        asset.setObjectsDeletedAt(Instant.now());
        asset.setStatus(PhotoAssetStatus.DELETED.name());
        asset.setDeletedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        mapper.updateById(asset);
        audit.record(actor.id(), "PHOTO_DELETE", "PHOTO_ASSET", photoId.toString(), true);
    }

    @Transactional
    public void cancelUpload(UUID projectId, UUID photoId) {
        User actor = access.require(projectId, ProjectPermission.UPLOAD_PHOTOS);
        PhotoAssetEntity asset = require(projectId, photoId);
        ensureUploadOwnerOrAdministrator(asset, actor);
        ensureUploading(asset);
        fail(asset, "UPLOAD_CANCELLED", "上传已取消", true);
        audit.record(actor.id(), "PHOTO_UPLOAD_CANCEL", "PHOTO_ASSET", photoId.toString(), true);
    }

    private void validateParts(List<ObjectStorageGateway.CompletedUploadPart> parts, int expected) {
        if (parts == null || parts.size() != expected) throw bad("UPLOAD_PARTS_INCOMPLETE", "分片清单不完整");
        Set<Integer> numbers = new HashSet<>();
        for (var part : parts) {
            if (part.partNumber() < 1 || part.partNumber() > expected || part.eTag() == null
                    || part.eTag().isBlank() || !numbers.add(part.partNumber())) {
                throw bad("UPLOAD_PARTS_INVALID", "分片清单包含无效编号、重复编号或空 ETag");
            }
        }
        if (numbers.size() != expected) throw bad("UPLOAD_PARTS_INCOMPLETE", "分片清单不完整");
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

    private void registerMultipartRollbackCleanup(String objectKey, String uploadId) {
        if (uploadId == null || !TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storage.abortMultipart(objectKey, uploadId);
                    } catch (RuntimeException ignored) {
                        // 对象存储自身还应配置未完成分片生命周期规则作为最终兜底。
                    }
                }
            }
        });
    }

    private PhotoAssetEntity require(UUID projectId, UUID photoId) {
        PhotoAssetEntity asset = mapper.selectById(photoId);
        if (asset == null || !projectId.equals(asset.getProjectId()) || PhotoAssetStatus.DELETED.name().equals(asset.getStatus())) {
            throw new ApiException("PHOTO_NOT_FOUND", "照片不存在", HttpStatus.NOT_FOUND);
        }
        return asset;
    }

    private void ensureUploading(PhotoAssetEntity asset) {
        if (!PhotoAssetStatus.UPLOADING.name().equals(asset.getStatus())) throw conflict("UPLOAD_STATE_INVALID", "上传状态不允许该操作");
        if (asset.getUploadExpiresAt().isBefore(Instant.now())) throw conflict("UPLOAD_EXPIRED", "上传会话已过期");
    }

    private void ensureUploadOwnerOrAdministrator(PhotoAssetEntity asset, User actor) {
        if (asset.getUploaderId().equals(actor.id()) || actor.administrator()) return;
        var membership = access.membership(asset.getProjectId(), actor.id());
        if (membership == null || !ProjectRole.valueOf(membership.getRole()).allows(ProjectPermission.MANAGE_PROJECT)) {
            throw new ApiException("UPLOAD_ACCESS_DENIED", "只能操作本人创建的上传会话", HttpStatus.FORBIDDEN);
        }
    }

    private void fail(PhotoAssetEntity asset, String code, String message, boolean deleteObject) {
        if (deleteObject) {
            try {
                cleanupStorage(asset);
                mapper.markObjectsDeleted(asset.getId(), Instant.now());
            } catch (RuntimeException ignored) { }
        }
        mapper.updateFailure(asset.getId(), PhotoAssetStatus.FAILED.name(), code,
                message.length() > 500 ? message.substring(0, 500) : message, Instant.now());
    }

    private void cleanupStorage(PhotoAssetEntity asset) {
        if (asset.getMultipartUploadId() != null && PhotoAssetStatus.UPLOADING.name().equals(asset.getStatus())) {
            storage.abortMultipart(asset.getOriginalKey(), asset.getMultipartUploadId());
        } else {
            storage.delete(asset.getOriginalKey());
        }
        if (asset.getSourceKey() != null && !asset.getSourceKey().equals(asset.getOriginalKey())) {
            storage.delete(asset.getSourceKey());
        }
        storage.delete(asset.getThumbnailKey());
        storage.delete(asset.getPreviewKey());
        analysisMapper.listVariants(asset.getId()).stream()
                .map(PhotoVariantEntity::objectKey).distinct().forEach(storage::delete);
    }

    private ApiException bad(String code, String message) { return new ApiException(code, message, HttpStatus.BAD_REQUEST); }
    private ApiException conflict(String code, String message) { return new ApiException(code, message, HttpStatus.CONFLICT); }

    public record InitiatedUpload(PhotoAssetEntity asset, boolean duplicate,
            ObjectStorageGateway.PresignedRequest directUpload) {}
    public record UploadProgress(String status, long uploadedBytes, long totalBytes,
            int uploadedParts, int totalParts, String failureCode, String failureMessage) {}
    public record AnalysisResult(PhotoAnalysisEntity analysis, List<PhotoVariantEntity> variants) {}
}
