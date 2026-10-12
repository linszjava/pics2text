package com.pics2text.api.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("photo_asset")
public class PhotoAssetEntity {
    @TableId
    private UUID id;
    private UUID projectId;
    private UUID uploaderId;
    private String originalFilename;
    private String declaredContentType;
    private String detectedContentType;
    private Long byteSize;
    private Integer width;
    private Integer height;
    private String sha256;
    private String authorizationStatus;
    private String reviewStatus;
    private String status;
    private String analysisStatus;
    private String uploadMode;
    private String multipartUploadId;
    private Long partSize;
    private Integer expectedPartCount;
    private String originalKey;
    private String sourceKey;
    private String thumbnailKey;
    private String previewKey;
    private UUID duplicateOf;
    private String failureCode;
    private String failureMessage;
    private String analysisErrorCode;
    private String analysisErrorMessage;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String reviewNote;
    private Instant uploadExpiresAt;
    private UUID verificationToken;
    private Instant retentionUntil;
    private Instant availableAt;
    private Instant withdrawnAt;
    private Instant deletedAt;
    private Instant objectsDeletedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public UUID getUploaderId() { return uploaderId; }
    public void setUploaderId(UUID uploaderId) { this.uploaderId = uploaderId; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getDeclaredContentType() { return declaredContentType; }
    public void setDeclaredContentType(String declaredContentType) { this.declaredContentType = declaredContentType; }
    public String getDetectedContentType() { return detectedContentType; }
    public void setDetectedContentType(String detectedContentType) { this.detectedContentType = detectedContentType; }
    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getAuthorizationStatus() { return authorizationStatus; }
    public void setAuthorizationStatus(String authorizationStatus) { this.authorizationStatus = authorizationStatus; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAnalysisStatus() { return analysisStatus; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }
    public String getUploadMode() { return uploadMode; }
    public void setUploadMode(String uploadMode) { this.uploadMode = uploadMode; }
    public String getMultipartUploadId() { return multipartUploadId; }
    public void setMultipartUploadId(String multipartUploadId) { this.multipartUploadId = multipartUploadId; }
    public Long getPartSize() { return partSize; }
    public void setPartSize(Long partSize) { this.partSize = partSize; }
    public Integer getExpectedPartCount() { return expectedPartCount; }
    public void setExpectedPartCount(Integer expectedPartCount) { this.expectedPartCount = expectedPartCount; }
    public String getOriginalKey() { return originalKey; }
    public void setOriginalKey(String originalKey) { this.originalKey = originalKey; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getThumbnailKey() { return thumbnailKey; }
    public void setThumbnailKey(String thumbnailKey) { this.thumbnailKey = thumbnailKey; }
    public String getPreviewKey() { return previewKey; }
    public void setPreviewKey(String previewKey) { this.previewKey = previewKey; }
    public UUID getDuplicateOf() { return duplicateOf; }
    public void setDuplicateOf(UUID duplicateOf) { this.duplicateOf = duplicateOf; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public String getAnalysisErrorCode() { return analysisErrorCode; }
    public void setAnalysisErrorCode(String analysisErrorCode) { this.analysisErrorCode = analysisErrorCode; }
    public String getAnalysisErrorMessage() { return analysisErrorMessage; }
    public void setAnalysisErrorMessage(String analysisErrorMessage) { this.analysisErrorMessage = analysisErrorMessage; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public void setUploadExpiresAt(Instant uploadExpiresAt) { this.uploadExpiresAt = uploadExpiresAt; }
    public UUID getVerificationToken() { return verificationToken; }
    public void setVerificationToken(UUID verificationToken) { this.verificationToken = verificationToken; }
    public Instant getRetentionUntil() { return retentionUntil; }
    public void setRetentionUntil(Instant retentionUntil) { this.retentionUntil = retentionUntil; }
    public Instant getAvailableAt() { return availableAt; }
    public void setAvailableAt(Instant availableAt) { this.availableAt = availableAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(Instant withdrawnAt) { this.withdrawnAt = withdrawnAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Instant getObjectsDeletedAt() { return objectsDeletedAt; }
    public void setObjectsDeletedAt(Instant objectsDeletedAt) { this.objectsDeletedAt = objectsDeletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
