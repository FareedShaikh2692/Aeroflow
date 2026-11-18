package com.aeroflow.auth.service;

import com.aeroflow.auth.api.AuthDtos.*;
import com.aeroflow.auth.domain.RefreshToken;
import com.aeroflow.auth.domain.User;
import com.aeroflow.auth.repo.RefreshTokenRepository;
import com.aeroflow.auth.repo.UserRepository;
import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.common.events.Topics;
import com.aeroflow.common.security.JwtService;
import com.aeroflow.common.security.Roles;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    /** Pre-computed hash used to equalise timing when the e-mail does not exist. */
    private final String dummyHash;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final DomainEventPublisher events;
    private final AuditService audit;
    private final Duration refreshTtl;
    private final int maxFailed;
    private final Duration lockout;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, DomainEventPublisher events, AuditService audit,
                       @Value("${aeroflow.security.refresh-token-ttl:7d}") Duration refreshTtl,
                       @Value("${aeroflow.security.max-failed-logins:5}") int maxFailed,
                       @Value("${aeroflow.security.lockout:15m}") Duration lockout) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.events = events;
        this.audit = audit;
        this.refreshTtl = refreshTtl;
        this.maxFailed = maxFailed;
        this.lockout = lockout;
        this.dummyHash = encoder.encode("timing-equaliser-" + UUID.randomUUID());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest req, String userAgent, String ip) {
        User user = users.findByEmail(req.email().trim()).orElse(null);
        if (user == null) {
            encoder.matches(req.password(), dummyHash);
            throw invalidCredentials();
        }
        if (user.isLocked(Instant.now())) {
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Too many failed attempts. Try again after " + user.getLockedUntil());
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            user.recordFailedLogin(maxFailed, lockout);
            log.info("Failed login for user {}", user.getId());
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been disabled");
        }
        user.recordSuccessfulLogin();
        audit.record(user.getId().toString(), user.getEmail(), String.join(",", user.getRoles()),
                "USER_LOGIN", "User", user.getId().toString(), null);
        return issueTokens(user, UUID.randomUUID(), userAgent, ip);
    }

    @Transactional
    public TokenResponse register(RegisterRequest req, String userAgent, String ip) {
        if (users.existsByEmailIgnoreCase(req.email().trim())) {
            throw ApiException.conflict("EMAIL_IN_USE", "An account with this e-mail already exists");
        }
        User user = users.save(new User(req.email(), encoder.encode(req.password()), req.firstName().trim(),
                req.lastName().trim(), req.phone(), Set.of(Roles.PASSENGER)));
        publishRegistered(user);
        return issueTokens(user, UUID.randomUUID(), userAgent, ip);
    }

    public void publishRegistered(User user) {
        events.publish(Topics.USER, EventTypes.USER_REGISTERED, user.getId().toString(), Map.of(
                "userId", user.getId(), "email", user.getEmail(), "firstName", user.getFirstName(),
                "lastName", user.getLastName(), "phone", user.getPhone() == null ? "" : user.getPhone(),
                "roles", user.getRoles()));
    }

    /** Rotates the refresh token. Presenting an already-used token revokes the whole family (theft detection). */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String presented, String userAgent, String ip) {
        RefreshToken current = refreshTokens.findByTokenHash(TokenHasher.hash(presented))
                .orElseThrow(AuthService::invalidRefresh);
        Instant now = Instant.now();
        if (current.getRevokedAt() != null) {
            int n = refreshTokens.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {} - revoked {} tokens", current.getUserId(), n);
            throw invalidRefresh();
        }
        if (!current.isActive(now)) throw invalidRefresh();
        User user = users.findById(current.getUserId()).orElseThrow(AuthService::invalidRefresh);
        if (!user.isEnabled()) throw invalidRefresh();
        current.revoke();
        return issueTokens(user, current.getFamilyId(), userAgent, ip);
    }

    @Transactional
    public void logout(String presented) {
        refreshTokens.findByTokenHash(TokenHasher.hash(presented))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User", userId));
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        refreshTokens.revokeAllForUser(userId, Instant.now());
        audit.record("PASSWORD_CHANGED", "User", userId.toString(), null);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId).map(UserMapper::toResponse).orElseThrow(() -> ApiException.notFound("User", userId));
    }

    private TokenResponse issueTokens(User user, UUID familyId, String userAgent, String ip) {
        String refresh = TokenHasher.newToken();
        refreshTokens.save(new RefreshToken(user.getId(), TokenHasher.hash(refresh), familyId,
                Instant.now().plus(refreshTtl), userAgent, ip));
        String access = jwt.issueAccessToken(user.getId(), user.getEmail(), user.fullName(), user.getRoles());
        return new TokenResponse(access, refresh, "Bearer", jwt.accessTokenTtl().toSeconds(), UserMapper.toResponse(user));
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int n = refreshTokens.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
        if (n > 0) log.info("Purged {} expired refresh tokens", n);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid e-mail or password");
    }

    private static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Session expired, please sign in again");
    }
}
