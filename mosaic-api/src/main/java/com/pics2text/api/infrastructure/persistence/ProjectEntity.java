package com.pics2text.api.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("mosaic_project")
public class ProjectEntity {

    @TableId
    private UUID id;
    private String name;
    private String status;
    private String description;
    private String configurationJson;
    private Integer retentionDays;
    private Long maxPhotoBytes;
    private UUID activeLayoutId;
    private UUID activeTextMaskId;
    private Instant archivedAt;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public ProjectEntity() {
    }

    public ProjectEntity(UUID id, String name, String status, UUID createdBy,
            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.description = "";
        this.configurationJson = "{}";
        this.retentionDays = 30;
        this.maxPhotoBytes = 104857600L;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getConfigurationJson() { return configurationJson; }
    public void setConfigurationJson(String configurationJson) { this.configurationJson = configurationJson; }
    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
    public Long getMaxPhotoBytes() { return maxPhotoBytes; }
    public void setMaxPhotoBytes(Long maxPhotoBytes) { this.maxPhotoBytes = maxPhotoBytes; }
    public UUID getActiveLayoutId() { return activeLayoutId; }
    public void setActiveLayoutId(UUID activeLayoutId) { this.activeLayoutId = activeLayoutId; }
    public UUID getActiveTextMaskId() { return activeTextMaskId; }
    public void setActiveTextMaskId(UUID activeTextMaskId) { this.activeTextMaskId = activeTextMaskId; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
