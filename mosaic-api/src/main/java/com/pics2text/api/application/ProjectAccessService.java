package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.ProjectEntity;
import com.pics2text.api.infrastructure.persistence.ProjectMapper;
import com.pics2text.api.infrastructure.persistence.ProjectMemberEntity;
import com.pics2text.api.infrastructure.persistence.ProjectMemberMapper;
import com.pics2text.identity.domain.User;
import com.pics2text.project.domain.ProjectPermission;
import com.pics2text.project.domain.ProjectRole;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {

    private final AuthenticationService authenticationService;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    public ProjectAccessService(
            AuthenticationService authenticationService,
            ProjectMapper projectMapper,
            ProjectMemberMapper projectMemberMapper) {
        this.authenticationService = authenticationService;
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
    }

    public User require(UUID projectId, ProjectPermission permission) {
        if (projectMapper.selectById(projectId) == null) {
            throw new ApiException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND);
        }
        User user = authenticationService.currentUser();
        if (user.administrator()) {
            return user;
        }
        ProjectMemberEntity membership = membership(projectId, user.id());
        if (membership == null || !ProjectRole.valueOf(membership.getRole()).allows(permission)) {
            throw new ApiException("PROJECT_ACCESS_DENIED", "无权访问该项目资源", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    public ProjectMemberEntity membership(UUID projectId, UUID userId) {
        return projectMemberMapper.find(projectId, userId);
    }

    public ProjectEntity requireProject(UUID projectId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ApiException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND);
        }
        return project;
    }
}
