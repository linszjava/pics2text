package com.pics2text.layout.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public class EditableLayoutEntity {
    private UUID id; private UUID projectId; private UUID sourceAutoLayoutId; private UUID maskId;
    private String name; private String state; private Integer canvasWidth; private Integer canvasHeight;
    private Boolean allowPhotoReuse; private Integer maxPhotoReuse; private Long currentRevision;
    private Long lockVersion; private Integer currentVersionNumber; private UUID currentVersionId;
    private UUID publishedVersionId; private UUID lockedBy; private Instant lockedAt;
    private UUID publishedBy; private Instant publishedAt; private UUID createdBy;
    private Instant createdAt; private Instant updatedAt;
    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getProjectId() { return projectId; } public void setProjectId(UUID value) { projectId = value; }
    public UUID getSourceAutoLayoutId() { return sourceAutoLayoutId; } public void setSourceAutoLayoutId(UUID value) { sourceAutoLayoutId = value; }
    public UUID getMaskId() { return maskId; } public void setMaskId(UUID value) { maskId = value; }
    public String getName() { return name; } public void setName(String value) { name = value; }
    public String getState() { return state; } public void setState(String value) { state = value; }
    public Integer getCanvasWidth() { return canvasWidth; } public void setCanvasWidth(Integer value) { canvasWidth = value; }
    public Integer getCanvasHeight() { return canvasHeight; } public void setCanvasHeight(Integer value) { canvasHeight = value; }
    public Boolean getAllowPhotoReuse() { return allowPhotoReuse; } public void setAllowPhotoReuse(Boolean value) { allowPhotoReuse = value; }
    public Integer getMaxPhotoReuse() { return maxPhotoReuse; } public void setMaxPhotoReuse(Integer value) { maxPhotoReuse = value; }
    public Long getCurrentRevision() { return currentRevision; } public void setCurrentRevision(Long value) { currentRevision = value; }
    public Long getLockVersion() { return lockVersion; } public void setLockVersion(Long value) { lockVersion = value; }
    public Integer getCurrentVersionNumber() { return currentVersionNumber; } public void setCurrentVersionNumber(Integer value) { currentVersionNumber = value; }
    public UUID getCurrentVersionId() { return currentVersionId; } public void setCurrentVersionId(UUID value) { currentVersionId = value; }
    public UUID getPublishedVersionId() { return publishedVersionId; } public void setPublishedVersionId(UUID value) { publishedVersionId = value; }
    public UUID getLockedBy() { return lockedBy; } public void setLockedBy(UUID value) { lockedBy = value; }
    public Instant getLockedAt() { return lockedAt; } public void setLockedAt(Instant value) { lockedAt = value; }
    public UUID getPublishedBy() { return publishedBy; } public void setPublishedBy(UUID value) { publishedBy = value; }
    public Instant getPublishedAt() { return publishedAt; } public void setPublishedAt(Instant value) { publishedAt = value; }
    public UUID getCreatedBy() { return createdBy; } public void setCreatedBy(UUID value) { createdBy = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}
