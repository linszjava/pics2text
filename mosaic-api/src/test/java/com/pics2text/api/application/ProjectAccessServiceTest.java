package com.pics2text.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.ProjectEntity;
import com.pics2text.api.infrastructure.persistence.ProjectMapper;
import com.pics2text.api.infrastructure.persistence.ProjectMemberEntity;
import com.pics2text.api.infrastructure.persistence.ProjectMemberMapper;
import com.pics2text.identity.domain.GlobalRole;
import com.pics2text.identity.domain.User;
import com.pics2text.identity.domain.UserStatus;
import com.pics2text.project.domain.ProjectPermission;
import com.pics2text.project.domain.ProjectRole;
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

    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectMemberMapper projectMemberMapper;

    private ProjectAccessService service;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        service = new ProjectAccessService(authenticationService, projectMapper, projectMemberMapper);
        projectId = UUID.randomUUID();
        when(projectMapper.selectById(projectId)).thenReturn(new ProjectEntity());
    }

    @Test
    void globalAdministratorCanAccessEveryProject() {
        User administrator = user(GlobalRole.ADMIN);
        when(authenticationService.currentUser()).thenReturn(administrator);

        assertEquals(administrator, service.require(projectId, ProjectPermission.MANAGE_MEMBERS));
        verify(projectMemberMapper, never()).find(any(), any());
    }

    @Test
    void viewerCannotManageProjectMembers() {
        User viewer = user(GlobalRole.USER);
        when(authenticationService.currentUser()).thenReturn(viewer);
        when(projectMemberMapper.find(projectId, viewer.id())).thenReturn(new ProjectMemberEntity(
                projectId, viewer.id(), ProjectRole.VIEWER.name(), UUID.randomUUID(), Instant.now()));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.require(projectId, ProjectPermission.MANAGE_MEMBERS));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        assertEquals("PROJECT_ACCESS_DENIED", exception.code());
    }

    @Test
    void memberCannotAccessAProjectWithoutMembership() {
        User user = user(GlobalRole.USER);
        when(authenticationService.currentUser()).thenReturn(user);
        when(projectMemberMapper.find(projectId, user.id())).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.require(projectId, ProjectPermission.VIEW));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
    }

    private User user(GlobalRole role) {
        Instant now = Instant.now();
        return new User(
                UUID.randomUUID(), "tester", "hash", "Tester", role, UserStatus.ACTIVE, now, now);
    }
}
