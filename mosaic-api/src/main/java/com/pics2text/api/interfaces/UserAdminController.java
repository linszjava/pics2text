package com.pics2text.api.interfaces;

import cn.dev33.satoken.stp.StpUtil;
import com.pics2text.api.application.UserAdministrationService;
import com.pics2text.identity.domain.GlobalRole;
import com.pics2text.identity.domain.Permission;
import com.pics2text.identity.domain.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdministrationService userAdministrationService;

    public UserAdminController(UserAdministrationService userAdministrationService) {
        this.userAdministrationService = userAdministrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        StpUtil.checkPermission(Permission.USER_WRITE.code());
        return UserResponse.from(userAdministrationService.create(
                request.username(), request.password(), request.displayName(), request.globalRole()));
    }

    @GetMapping
    public List<UserResponse> list() {
        StpUtil.checkPermission(Permission.USER_READ.code());
        return userAdministrationService.list().stream().map(UserResponse::from).toList();
    }

    @PatchMapping("/{userId}/status")
    public UserResponse changeStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeStatusRequest request) {
        StpUtil.checkPermission(Permission.USER_WRITE.code());
        return UserResponse.from(userAdministrationService.changeStatus(userId, request.status()));
    }

    @PostMapping("/{userId}/kick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kick(@PathVariable UUID userId) {
        StpUtil.checkPermission(Permission.USER_KICK.code());
        userAdministrationService.kick(userId);
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 5, max = 128) String password,
            @NotBlank @Size(max = 100) String displayName,
            @NotNull GlobalRole globalRole) {
    }

    public record ChangeStatusRequest(@NotNull UserStatus status) {
    }
}
