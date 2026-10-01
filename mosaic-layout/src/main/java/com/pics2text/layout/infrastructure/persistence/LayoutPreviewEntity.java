package com.pics2text.layout.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public class LayoutPreviewEntity {
    private UUID id; private UUID layoutId; private UUID versionId; private String status;
    private String objectKey; private Integer width; private Integer height; private Integer assignmentCount;
    private String errorCode; private String errorMessage; private UUID createdBy;
    private Instant createdAt; private Instant updatedAt; private Instant completedAt;
    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getLayoutId() { return layoutId; } public void setLayoutId(UUID value) { layoutId = value; }
    public UUID getVersionId() { return versionId; } public void setVersionId(UUID value) { versionId = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public String getObjectKey() { return objectKey; } public void setObjectKey(String value) { objectKey = value; }
    public Integer getWidth() { return width; } public void setWidth(Integer value) { width = value; }
    public Integer getHeight() { return height; } public void setHeight(Integer value) { height = value; }
    public Integer getAssignmentCount() { return assignmentCount; } public void setAssignmentCount(Integer value) { assignmentCount = value; }
    public String getErrorCode() { return errorCode; } public void setErrorCode(String value) { errorCode = value; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String value) { errorMessage = value; }
    public UUID getCreatedBy() { return createdBy; } public void setCreatedBy(UUID value) { createdBy = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant value) { completedAt = value; }
}
