package com.pics2text.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.BootstrapMapper;
import com.pics2text.identity.domain.GlobalRole;
import com.pics2text.identity.domain.User;
import com.pics2text.identity.domain.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private AuditService auditService;
    @Mock
    private BootstrapMapper bootstrapMapper;
    @Mock
    private HttpServletRequest request;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                userService,
                loginAttemptService,
                auditService,
                bootstrapMapper,
                request,
                "bootstrap-secret",
                3600);
    }

    @Test
    void unknownUserGetsGenericAuthenticationFailure() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(userService.findByUsername("unknown")).thenReturn(Optional.empty());
        when(userService.passwordMatches(null, "incorrect-password")).thenReturn(false);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.login("Unknown", "incorrect-password"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        assertEquals("LOGIN_FAILED", exception.code());
        assertEquals("用户名或密码错误", exception.getMessage());
        verify(loginAttemptService).recordFailure("unknown", "127.0.0.1");
        verify(auditService).record(isNull(), anyString(), anyString(), isNull(), anyBoolean());
    }

    @Test
    void disabledUserCannotLogin() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        User disabled = user(UserStatus.DISABLED);
        when(userService.findByUsername("disabled")).thenReturn(Optional.of(disabled));
        when(userService.passwordMatches(disabled, "Strong-Pass-2026")).thenReturn(true);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.login("disabled", "Strong-Pass-2026"));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        assertEquals("USER_DISABLED", exception.code());
    }

    @Test
    void invalidBootstrapTokenDoesNotClaimBootstrap() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.bootstrap("wrong", "admin", "Strong-Pass-2026", "Administrator"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        verify(bootstrapMapper, never()).claim();
        verify(userService, never()).create(anyString(), anyString(), anyString(), any());
    }

    private User user(UserStatus status) {
        Instant now = Instant.now();
        return new User(
                UUID.randomUUID(),
                "disabled",
                "hash",
                "Disabled User",
                GlobalRole.USER,
                status,
                now,
                now);
    }
}
