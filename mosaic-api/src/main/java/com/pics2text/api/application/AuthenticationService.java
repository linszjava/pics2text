package com.pics2text.api.application;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.pics2text.api.common.ApiException;
import com.pics2text.api.infrastructure.persistence.BootstrapMapper;
import com.pics2text.identity.domain.GlobalRole;
import com.pics2text.identity.domain.User;
import com.pics2text.identity.domain.UsernamePolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserService userService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final BootstrapMapper bootstrapMapper;
    private final HttpServletRequest request;
    private final String bootstrapToken;
    private final boolean registrationEnabled;
    private final long tokenTimeout;

    public AuthenticationService(
            UserService userService,
            LoginAttemptService loginAttemptService,
            AuditService auditService,
            BootstrapMapper bootstrapMapper,
            HttpServletRequest request,
            @Value("${identity.bootstrap-token}") String bootstrapToken,
            @Value("${identity.registration-enabled:true}") boolean registrationEnabled,
            @Value("${sa-token.timeout}") long tokenTimeout) {
        this.userService = userService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.bootstrapMapper = bootstrapMapper;
        this.request = request;
        this.bootstrapToken = bootstrapToken;
        this.registrationEnabled = registrationEnabled;
        this.tokenTimeout = tokenTimeout;
    }

    @Transactional
    public User bootstrap(String suppliedToken, String username, String password, String displayName) {
        if (!secureEquals(bootstrapToken, suppliedToken)) {
            auditService.record(null, "IDENTITY_BOOTSTRAP", "USER", null, false);
            throw new ApiException("BOOTSTRAP_TOKEN_INVALID", "初始化凭据无效", HttpStatus.UNAUTHORIZED);
        }
        if (bootstrapMapper.claim() != 1) {
            throw new ApiException("BOOTSTRAP_COMPLETED", "系统管理员初始化已经完成", HttpStatus.CONFLICT);
        }
        User user = userService.create(username, password, displayName, GlobalRole.ADMIN);
        auditService.record(user.id(), "IDENTITY_BOOTSTRAP", "USER", user.id().toString(), true);
        return user;
    }

    @Transactional
    public User register(String username, String password, String displayName) {
        if (!registrationEnabled) {
            throw new ApiException("REGISTRATION_DISABLED", "当前环境未开放用户注册", HttpStatus.FORBIDDEN);
        }
        User user = userService.create(username, password, displayName, GlobalRole.USER);
        auditService.record(user.id(), "USER_REGISTER", "USER", user.id().toString(), true);
        return user;
    }

    public Session login(String username, String password) {
        String normalizedUsername = UsernamePolicy.normalize(username);
        String clientIp = clientIp();
        loginAttemptService.verifyAllowed(normalizedUsername, clientIp);
        User user = userService.findByUsername(normalizedUsername).orElse(null);
        boolean passwordMatches = userService.passwordMatches(user, password);
        if (user == null || !passwordMatches) {
            loginAttemptService.recordFailure(normalizedUsername, clientIp);
            auditService.record(user == null ? null : user.id(), "LOGIN", "USER",
                    user == null ? null : user.id().toString(), false);
            throw new ApiException("LOGIN_FAILED", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        if (!user.active()) {
            auditService.record(user.id(), "LOGIN", "USER", user.id().toString(), false);
            throw new ApiException("USER_DISABLED", "用户已停用", HttpStatus.FORBIDDEN);
        }

        loginAttemptService.clear(normalizedUsername, clientIp);
        StpUtil.login(user.id().toString());
        StpUtil.updateLastActiveToNow();
        auditService.record(user.id(), "LOGIN", "USER", user.id().toString(), true);
        return session(user, StpUtil.getTokenInfo());
    }

    public void logout() {
        UUID userId = currentUserId();
        auditService.record(userId, "LOGOUT", "USER", userId.toString(), true);
        StpUtil.logout();
    }

    public Session refresh() {
        User user = currentUser();
        StpUtil.renewTimeout(tokenTimeout);
        StpUtil.updateLastActiveToNow();
        auditService.record(user.id(), "TOKEN_RENEW", "USER", user.id().toString(), true);
        return session(user, StpUtil.getTokenInfo());
    }

    public User currentUser() {
        User user = userService.require(currentUserId());
        if (!user.active()) {
            StpUtil.logout();
            throw new ApiException("USER_DISABLED", "用户已停用", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    public UUID currentUserId() {
        return UUID.fromString(StpUtil.getLoginIdAsString());
    }

    public void kick(UUID actorUserId, UUID targetUserId) {
        userService.require(targetUserId);
        StpUtil.kickout(targetUserId.toString());
        auditService.record(actorUserId, "USER_KICK", "USER", targetUserId.toString(), true);
    }

    private Session session(User user, SaTokenInfo tokenInfo) {
        return new Session(
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                tokenInfo.getTokenActiveTimeout(),
                user);
    }

    private String clientIp() {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean secureEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    public record Session(
            String tokenName,
            String tokenValue,
            long tokenTimeout,
            long activeTimeout,
            User user) {
    }
}
