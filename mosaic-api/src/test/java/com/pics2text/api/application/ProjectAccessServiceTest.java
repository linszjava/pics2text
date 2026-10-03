package com.pics2text.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.ProjectEntity;
import com.pics2text.api.infrastructure.persistence.ProjectMapper;
import com.pics2text.identity.domain.GlobalRole;
import com.pics2text.identity.domain.User;
import com.pics2text.identity.domain.UserStatus;
import com.pics2text.project.domain.ProjectPermission;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {
    @Mock private AuthenticationService authenticationService;
    @Mock private ProjectMapper projectMapper;

    private ProjectAccessService service;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        service = new ProjectAccessService(authenticationService, projectMapper);
        projectId = UUID.randomUUID();
    }

    @Test
    void creatorCanUseOwnProject() {
        User user = user();
        ProjectEntity project = new ProjectEntity();
        project.setCreatedBy(user.id());
        when(projectMapper.selectById(projectId)).thenReturn(project);
        when(authenticationService.currentUser()).thenReturn(user);

        assertEquals(user, service.require(projectId, ProjectPermission.VIEW));
        assertEquals(user, service.require(projectId, ProjectPermission.MANAGE_PROJECT));
        assertEquals(project, service.requireProject(projectId));
    }

    @Test
    void otherUserCannotAccessProject() {
        User user = user();
        ProjectEntity project = new ProjectEntity();
        project.setCreatedBy(UUID.randomUUID());
        when(projectMapper.selectById(projectId)).thenReturn(project);
        when(authenticationService.currentUser()).thenReturn(user);

        ApiException viewException = assertThrows(ApiException.class,
                () -> service.require(projectId, ProjectPermission.VIEW));
        ApiException manageException = assertThrows(ApiException.class,
                () -> service.require(projectId, ProjectPermission.MANAGE_PROJECT));
        ApiException projectException = assertThrows(ApiException.class,
                () -> service.requireProject(projectId));

        assertEquals(HttpStatus.NOT_FOUND, viewException.status());
        assertEquals(HttpStatus.NOT_FOUND, manageException.status());
        assertEquals(HttpStatus.NOT_FOUND, projectException.status());
    }

    @Test
    void missingProjectStillReturnsNotFound() {
        when(authenticationService.currentUser()).thenReturn(user());
        when(projectMapper.selectById(projectId)).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.require(projectId, ProjectPermission.VIEW));

        assertEquals(HttpStatus.NOT_FOUND, exception.status());
    }

    private User user() {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), "tester", "hash", "Tester",
                GlobalRole.USER, UserStatus.ACTIVE, now, now);
    }
}
