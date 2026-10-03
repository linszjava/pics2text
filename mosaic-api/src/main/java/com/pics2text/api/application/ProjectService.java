package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.ProjectEntity;
import com.pics2text.api.infrastructure.persistence.ProjectMapper;
import com.pics2text.layout.infrastructure.persistence.TextMaskMapper;
import com.pics2text.identity.domain.User;
import com.pics2text.project.domain.ProjectPermission;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final TextMaskMapper textMaskMapper;
    private final ProjectAccessService projectAccessService;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ProjectService(
            ProjectMapper projectMapper,
            TextMaskMapper textMaskMapper,
            ProjectAccessService projectAccessService,
            AuthenticationService authenticationService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.textMaskMapper = textMaskMapper;
        this.projectAccessService = projectAccessService;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProjectEntity create(String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank() || normalizedName.length() > 120) {
            throw new ApiException("PROJECT_NAME_INVALID", "项目名称不能为空且不能超过120个字符", HttpStatus.BAD_REQUEST);
        }
        User actor = authenticationService.currentUser();
        Instant now = Instant.now();
        ProjectEntity project = new ProjectEntity(
                UUID.randomUUID(), normalizedName, "ACTIVE", actor.id(), now, now);
        projectMapper.insert(project);
        auditService.record(actor.id(), "PROJECT_CREATE", "PROJECT", project.getId().toString(), true);
        return project;
    }

    public ProjectEntity get(UUID projectId) {
        projectAccessService.require(projectId, ProjectPermission.VIEW);
        return projectAccessService.requireProject(projectId);
    }

    public List<ProjectEntity> list() {
        return projectMapper.listByCreator(authenticationService.currentUser().id());
    }

    @Transactional
    public ProjectEntity update(UUID projectId, String name, String description,
            String configurationJson, Integer retentionDays, Long maxPhotoBytes) {
        User actor = projectAccessService.require(projectId, ProjectPermission.MANAGE_PROJECT);
        ProjectEntity project = projectAccessService.requireProject(projectId);
        if (name != null) {
            String normalized = name.trim();
            if (normalized.isBlank() || normalized.length() > 120) {
                throw new ApiException("PROJECT_NAME_INVALID", "项目名称不能为空且不能超过120个字符", HttpStatus.BAD_REQUEST);
            }
            project.setName(normalized);
        }
        if (description != null) project.setDescription(description.trim());
        if (configurationJson != null) {
            validateConfiguration(configurationJson);
            project.setConfigurationJson(configurationJson);
        }
        if (retentionDays != null) project.setRetentionDays(retentionDays);
        if (maxPhotoBytes != null) project.setMaxPhotoBytes(maxPhotoBytes);
        project.setUpdatedAt(Instant.now());
        projectMapper.updateById(project);
        auditService.record(actor.id(), "PROJECT_UPDATE", "PROJECT", projectId.toString(), true);
        return project;
    }

    @Transactional
    public ProjectEntity setArchived(UUID projectId, boolean archived) {
        User actor = projectAccessService.require(projectId, ProjectPermission.MANAGE_PROJECT);
        ProjectEntity project = projectAccessService.requireProject(projectId);
        Instant now = Instant.now();
        project.setStatus(archived ? "ARCHIVED" : "ACTIVE");
        project.setArchivedAt(archived ? now : null);
        project.setUpdatedAt(now);
        projectMapper.updateById(project);
        auditService.record(actor.id(), archived ? "PROJECT_ARCHIVE" : "PROJECT_ACTIVATE",
                "PROJECT", projectId.toString(), true);
        return project;
    }

    @Transactional
    public ProjectEntity selectTextMask(UUID projectId, UUID maskId) {
        projectAccessService.require(projectId, ProjectPermission.EDIT_LAYOUT);
        ProjectEntity project = projectAccessService.requireProject(projectId);
        if (!"ACTIVE".equals(project.getStatus())) {
            throw new ApiException("PROJECT_ARCHIVED", "已归档项目不能设置文字方案", HttpStatus.CONFLICT);
        }
        var mask = textMaskMapper.selectById(maskId);
        if (mask == null || !projectId.equals(mask.getProjectId())) {
            throw new ApiException("TEXT_MASK_NOT_FOUND", "文字方案不存在", HttpStatus.NOT_FOUND);
        }
        project.setActiveTextMaskId(maskId);
        project.setActiveLayoutId(null);
        project.setUpdatedAt(Instant.now());
        projectMapper.updateById(project);
        return project;
    }

    private void validateConfiguration(String json) {
        try {
            if (!objectMapper.readTree(json).isObject()) {
                throw new ApiException("PROJECT_CONFIGURATION_INVALID", "项目配置必须是 JSON 对象", HttpStatus.BAD_REQUEST);
            }
        } catch (JacksonException exception) {
            throw new ApiException("PROJECT_CONFIGURATION_INVALID", "项目配置不是有效 JSON", HttpStatus.BAD_REQUEST);
        }
    }

}
