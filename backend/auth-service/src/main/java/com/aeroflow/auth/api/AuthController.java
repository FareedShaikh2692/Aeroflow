package com.aeroflow.auth.api;

import com.aeroflow.auth.api.AuthDtos.*;
import com.aeroflow.auth.service.AuthService;
import com.aeroflow.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with e-mail and password")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req, http.getHeader(HttpHeaders.USER_AGENT), ip(http));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Self-register a passenger account")
    public TokenResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return auth.register(req, http.getHeader(HttpHeaders.USER_AGENT), ip(http));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and obtain a new access token")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return auth.refresh(req.refreshToken(), http.getHeader(HttpHeaders.USER_AGENT), ip(http));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the refresh token family")
    public void logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me() {
        return auth.me(CurrentUser.require().id());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(CurrentUser.require().id(), req);
    }

    private static String ip(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : http.getRemoteAddr();
    }
}
