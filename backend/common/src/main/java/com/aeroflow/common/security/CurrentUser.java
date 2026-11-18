package com.aeroflow.common.security;

import com.aeroflow.common.error.ApiException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<AuthenticatedUser> get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthenticatedUser u) return Optional.of(u);
        return Optional.empty();
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }
}
