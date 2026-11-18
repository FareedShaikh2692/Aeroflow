package com.aeroflow.auth.service;

import com.aeroflow.auth.api.AuthDtos.UserResponse;
import com.aeroflow.auth.domain.User;
import java.util.TreeSet;

public final class UserMapper {
    private UserMapper() {}
    public static UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getPhone(),
                new TreeSet<>(u.getRoles()), u.isEnabled(), u.getLastLoginAt(), u.getCreatedAt(), u.getLockedUntil());
    }
}
