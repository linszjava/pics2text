package com.pics2text.api.interfaces;

import com.pics2text.api.application.AuthenticationService;
import com.pics2text.identity.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse bootstrap(
            @RequestHeader(value = "X-Bootstrap-Token", required = false) String bootstrapToken,
            @Valid @RequestBody BootstrapRequest request) {
        User user = authenticationService.bootstrap(
                bootstrapToken, request.username(), request.password(), request.displayName());
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request) {
        return SessionResponse.from(authenticationService.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authenticationService.logout();
    }

    @PostMapping("/refresh")
    public SessionResponse refresh() {
        return SessionResponse.from(authenticationService.refresh());
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(authenticationService.currentUser());
    }

    public record BootstrapRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 100) String displayName) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    public record SessionResponse(
            String tokenName,
            String tokenValue,
            long tokenTimeout,
            long activeTimeout,
            UserResponse user) {

        static SessionResponse from(AuthenticationService.Session session) {
            return new SessionResponse(
                    session.tokenName(),
                    session.tokenValue(),
                    session.tokenTimeout(),
                    session.activeTimeout(),
                    UserResponse.from(session.user()));
        }
    }
}
