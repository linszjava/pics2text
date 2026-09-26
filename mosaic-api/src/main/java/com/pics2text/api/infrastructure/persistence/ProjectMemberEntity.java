package com.pics2text.api.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("project_member")
public class ProjectMemberEntity {

    private UUID projectId;
    private UUID userId;
    private String role;
    private UUID createdBy;
    private Instant createdAt;

    public ProjectMemberEntity() {
    }

    public ProjectMemberEntity(UUID projectId, UUID userId, String role, UUID createdBy, Instant createdAt) {
        this.projectId = projectId;
        this.userId = userId;
        this.role = role;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
