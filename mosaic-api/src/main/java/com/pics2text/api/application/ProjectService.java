package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.ProjectEntity;
import com.pics2text.api.infrastructure.persistence.ProjectMapper;
import com.pics2text.api.infrastructure.persistence.ProjectMemberEntity;
import com.pics2text.api.infrastructure.persistence.ProjectMemberMapper;
import com.pics2text.identity.domain.User;
import com.pics2text.project.domain.ProjectPermission;
import com.pics2text.project.domain.ProjectRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectAccessService projectAccessService;
    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final AuditService auditService;

    public ProjectService(
            ProjectMapper projectMapper,
            ProjectMemberMapper projectMemberMapper,
            ProjectAccessService projectAccessService,
            AuthenticationService authenticationService,
            UserService userService,
            AuditService auditService) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectAccessService = projectAccessService;
        this.authenticationService = authenticationService;
        this.userService = userService;
        this.auditService = auditService;
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
        projectMemberMapper.insert(new ProjectMemberEntity(
                project.getId(), actor.id(), ProjectRole.PROJECT_ADMIN.name(), actor.id(), now));
        auditService.record(actor.id(), "PROJECT_CREATE", "PROJECT", project.getId().toString(), true);
        return project;
    }

    public ProjectEntity get(UUID projectId) {
        projectAccessService.require(projectId, ProjectPermission.VIEW);
        return projectAccessService.requireProject(projectId);
    }

    public List<MemberView> listMembers(UUID projectId) {
        projectAccessService.require(projectId, ProjectPermission.VIEW);
        return projectMemberMapper.listByProject(projectId)
                .stream()
                .map(member -> new MemberView(
                        userService.require(member.getUserId()),
                        ProjectRole.valueOf(member.getRole()),
                        member.getCreatedAt()))
                .toList();
    }

    @Transactional
    public MemberView putMember(UUID projectId, UUID userId, ProjectRole role) {
        User actor = projectAccessService.require(projectId, ProjectPermission.MANAGE_MEMBERS);
        User target = userService.require(userId);
        if (!target.active()) {
            throw new ApiException("USER_DISABLED", "不能添加已停用用户", HttpStatus.CONFLICT);
        }
        ProjectMemberEntity existing = projectAccessService.membership(projectId, userId);
        if (existing == null) {
            existing = new ProjectMemberEntity(projectId, userId, role.name(), actor.id(), Instant.now());
            projectMemberMapper.insert(existing);
        } else {
            if (ProjectRole.PROJECT_ADMIN.name().equals(existing.getRole())
                    && role != ProjectRole.PROJECT_ADMIN) {
                ensureAnotherAdministrator(projectId);
            }
            projectMemberMapper.updateRole(projectId, userId, role.name());
            existing.setRole(role.name());
        }
        auditService.record(actor.id(), "PROJECT_MEMBER_PUT", "PROJECT_MEMBER",
                projectId + ":" + userId, true);
        return new MemberView(target, role, existing.getCreatedAt());
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId) {
        User actor = projectAccessService.require(projectId, ProjectPermission.MANAGE_MEMBERS);
        ProjectMemberEntity membership = projectAccessService.membership(projectId, userId);
        if (membership == null) {
            throw new ApiException("PROJECT_MEMBER_NOT_FOUND", "项目成员不存在", HttpStatus.NOT_FOUND);
        }
        if (ProjectRole.PROJECT_ADMIN.name().equals(membership.getRole())) {
            ensureAnotherAdministrator(projectId);
        }
        projectMemberMapper.deleteMembership(projectId, userId);
        auditService.record(actor.id(), "PROJECT_MEMBER_DELETE", "PROJECT_MEMBER",
                projectId + ":" + userId, true);
    }

    private void ensureAnotherAdministrator(UUID projectId) {
        long administrators = projectMemberMapper.countAdministrators(projectId);
        if (administrators <= 1) {
            throw new ApiException(
                    "LAST_PROJECT_ADMIN",
                    "项目必须至少保留一名项目管理员",
                    HttpStatus.CONFLICT);
        }
    }

    public record MemberView(User user, ProjectRole role, Instant createdAt) {
    }
}
