package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.ProjectEntity;
import com.pics2text.api.infrastructure.persistence.ProjectMapper;
import com.pics2text.identity.domain.User;
import com.pics2text.project.domain.ProjectPermission;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {

    private final AuthenticationService authenticationService;
    private final ProjectMapper projectMapper;

    public ProjectAccessService(
            AuthenticationService authenticationService,
            ProjectMapper projectMapper) {
        this.authenticationService = authenticationService;
        this.projectMapper = projectMapper;
    }

    public User require(UUID projectId, ProjectPermission permission) {
        User user = authenticationService.currentUser();
        requireOwnedProject(projectId, user.id());
        return user;
    }

    public ProjectEntity requireProject(UUID projectId) {
        return requireOwnedProject(projectId, authenticationService.currentUser().id());
    }

    private ProjectEntity requireOwnedProject(UUID projectId, UUID userId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null || !userId.equals(project.getCreatedBy())) {
            throw new ApiException("PROJECT_NOT_FOUND", "项目不存在", HttpStatus.NOT_FOUND);
        }
        return project;
    }
}
