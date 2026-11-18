package com.aeroflow.auth.service;

import com.aeroflow.auth.api.AuthDtos.*;
import com.aeroflow.auth.domain.User;
import com.aeroflow.auth.repo.RefreshTokenRepository;
import com.aeroflow.auth.repo.UserRepository;
import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.error.ApiException;
import com.aeroflow.common.security.AuthenticatedUser;
import com.aeroflow.common.security.CurrentUser;
import com.aeroflow.common.security.Roles;
import com.aeroflow.common.web.PageResponse;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {
    private static final Set<String> PRIVILEGED = Set.of(Roles.SUPER_ADMIN, Roles.ADMIN);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AuthService authService;

    public UserManagementService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                                 AuditService audit, AuthService authService) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.audit = audit;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String search, String role, Pageable pageable) {
        Specification<User> spec = (root, q, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), like)));
        }
        if (role != null && !role.isBlank()) {
            spec = spec.and((root, q, cb) -> {
                q.distinct(true);
                return cb.equal(root.join("roles", JoinType.INNER), role);
            });
        }
        return PageResponse.of(users.findAll(spec, pageable), UserMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserMapper.toResponse(find(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        validateRoles(req.roles());
        guardPrivilegedAssignment(req.roles());
        if (users.existsByEmailIgnoreCase(req.email().trim())) {
            throw ApiException.conflict("EMAIL_IN_USE", "An account with this e-mail already exists");
        }
        User u = users.save(new User(req.email(), encoder.encode(req.password()), req.firstName().trim(),
                req.lastName().trim(), req.phone(), req.roles()));
        if (u.getRoles().contains(Roles.PASSENGER)) authService.publishRegistered(u);
        audit.record("USER_CREATED", "User", u.getId().toString(), "email=" + u.getEmail() + " roles=" + u.getRoles());
        return UserMapper.toResponse(u);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest req) {
        validateRoles(req.roles());
        User u = find(id);
        AuthenticatedUser actor = CurrentUser.require();
        if (u.getRoles().contains(Roles.SUPER_ADMIN) && !actor.hasRole(Roles.SUPER_ADMIN)) {
            throw ApiException.forbidden("Only a SUPER_ADMIN can modify a SUPER_ADMIN account");
        }
        guardPrivilegedAssignment(req.roles());
        if (actor.id().equals(id) && (!req.enabled() || !req.roles().containsAll(
                u.getRoles().stream().filter(PRIVILEGED::contains).toList()))) {
            throw ApiException.badRequest("SELF_LOCKOUT", "You cannot disable yourself or remove your own admin role");
        }
        Set<String> before = Set.copyOf(u.getRoles());
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        u.setPhone(req.phone());
        u.setRoles(req.roles());
        boolean wasEnabled = u.isEnabled();
        u.setEnabled(req.enabled());
        if (wasEnabled && !req.enabled()) refreshTokens.revokeAllForUser(id, Instant.now());
        audit.record("USER_UPDATED", "User", id.toString(),
                "roles " + before + " -> " + req.roles() + ", enabled=" + req.enabled());
        return UserMapper.toResponse(u);
    }

    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest req) {
        User u = find(id);
        if (u.getRoles().contains(Roles.SUPER_ADMIN) && !CurrentUser.require().hasRole(Roles.SUPER_ADMIN)) {
            throw ApiException.forbidden("Only a SUPER_ADMIN can reset a SUPER_ADMIN password");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        refreshTokens.revokeAllForUser(id, Instant.now());
        audit.record("USER_PASSWORD_RESET", "User", id.toString(), null);
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
    }

    private static void validateRoles(Set<String> roles) {
        for (String r : roles) {
            if (!Roles.ALL_HUMAN.contains(r)) throw ApiException.badRequest("INVALID_ROLE", "Unknown role: " + r);
        }
    }

    private static void guardPrivilegedAssignment(Set<String> roles) {
        AuthenticatedUser actor = CurrentUser.require();
        if (roles.contains(Roles.SUPER_ADMIN) && !actor.hasRole(Roles.SUPER_ADMIN)) {
            throw ApiException.forbidden("Only a SUPER_ADMIN can grant the SUPER_ADMIN role");
        }
    }
}
