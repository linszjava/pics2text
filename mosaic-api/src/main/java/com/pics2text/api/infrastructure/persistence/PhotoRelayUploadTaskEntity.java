package com.pics2text.api.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("photo_relay_upload_task")
public class PhotoRelayUploadTaskEntity {
    @TableId
    private UUID id;
    private UUID projectId;
    private UUID photoId;
    private UUID uploaderId;
    private String storageNode;
    private String relativePath;
    private Long byteSize;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Instant nextAttemptAt;
    private UUID claimToken;
    private Instant claimedAt;
    private String failureCode;
    private String failureMessage;
    private Instant tempDeletedAt;
    private Instant createdAt;
    private Instant updatedAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID value) { projectId = value; }
    public UUID getPhotoId() { return photoId; }
    public void setPhotoId(UUID value) { photoId = value; }
    public UUID getUploaderId() { return uploaderId; }
    public void setUploaderId(UUID value) { uploaderId = value; }
    public String getStorageNode() { return storageNode; }
    public void setStorageNode(String value) { storageNode = value; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String value) { relativePath = value; }
    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long value) { byteSize = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer value) { attemptCount = value; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer value) { maxAttempts = value; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant value) { nextAttemptAt = value; }
    public UUID getClaimToken() { return claimToken; }
    public void setClaimToken(UUID value) { claimToken = value; }
    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant value) { claimedAt = value; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String value) { failureCode = value; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String value) { failureMessage = value; }
    public Instant getTempDeletedAt() { return tempDeletedAt; }
    public void setTempDeletedAt(Instant value) { tempDeletedAt = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
