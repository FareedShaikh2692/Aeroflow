package com.aeroflow.common.security;

import java.util.Set;
import java.util.UUID;

/** Principal reconstructed from a verified JWT. */
public record AuthenticatedUser(UUID id, String email, String name, Set<String> roles) {
    public boolean hasRole(String role) { return roles.contains(role); }
    public boolean isStaff() { return Roles.isStaff(roles); }
}
