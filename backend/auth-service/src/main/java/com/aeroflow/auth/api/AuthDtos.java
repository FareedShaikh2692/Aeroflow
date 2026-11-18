package com.aeroflow.auth.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public static final String PASSWORD_RULE = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$";
    public static final String PASSWORD_MSG = "Password must be 10-72 characters with upper, lower, digit and symbol";

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 72) String password) {}

    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String password,
                                  @NotBlank @Size(max = 80) String firstName,
                                  @NotBlank @Size(max = 80) String lastName,
                                  @Pattern(regexp = "^\\+?[0-9 ()-]{6,30}$", message = "Invalid phone number") String phone) {}

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String newPassword) {}

    public record UserResponse(UUID id, String email, String firstName, String lastName, String phone,
                               Set<String> roles, boolean enabled, Instant lastLoginAt, Instant createdAt,
                               Instant lockedUntil) {}

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                                UserResponse user) {}

    public record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
                                    @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String password,
                                    @NotBlank @Size(max = 80) String firstName,
                                    @NotBlank @Size(max = 80) String lastName,
                                    @Pattern(regexp = "^\\+?[0-9 ()-]{6,30}$", message = "Invalid phone number") String phone,
                                    @NotEmpty Set<@NotBlank String> roles) {}

    public record UpdateUserRequest(@NotBlank @Size(max = 80) String firstName,
                                    @NotBlank @Size(max = 80) String lastName,
                                    @Pattern(regexp = "^\\+?[0-9 ()-]{6,30}$", message = "Invalid phone number") String phone,
                                    @NotEmpty Set<@NotBlank String> roles,
                                    boolean enabled) {}

    public record ResetPasswordRequest(@NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String newPassword) {}
}
