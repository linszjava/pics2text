package com.pics2text.api.application;

import com.pics2text.api.common.ApiException;
import com.pics2text.identity.domain.GlobalRole;
import com.pics2text.identity.domain.User;
import com.pics2text.identity.domain.UserStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {

    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    public UserAdministrationService(
            UserService userService,
            AuthenticationService authenticationService,
            AuditService auditService) {
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
    }

    @Transactional
    public User create(String username, String password, String displayName, GlobalRole globalRole) {
        UUID actor = authenticationService.currentUserId();
        User user = userService.create(username, password, displayName, globalRole);
        auditService.record(actor, "USER_CREATE", "USER", user.id().toString(), true);
        return user;
    }

    public List<User> list() {
        return userService.list();
    }

    @Transactional
    public User changeStatus(UUID userId, UserStatus status) {
        UUID actor = authenticationService.currentUserId();
        User current = userService.require(userId);
        if (current.administrator() && current.active() && status == UserStatus.DISABLED
                && userService.countActiveAdministrators() <= 1) {
            throw new ApiException("LAST_GLOBAL_ADMIN", "系统必须至少保留一名启用的管理员", HttpStatus.CONFLICT);
        }
        User updated = userService.changeStatus(userId, status);
        if (status == UserStatus.DISABLED) {
            authenticationService.kick(actor, userId);
        }
        auditService.record(actor, "USER_STATUS_CHANGE", "USER", userId.toString(), true);
        return updated;
    }

    public void kick(UUID userId) {
        authenticationService.kick(authenticationService.currentUserId(), userId);
    }
}
