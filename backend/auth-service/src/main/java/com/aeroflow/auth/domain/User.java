package com.aeroflow.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id private UUID id;
    @Column(nullable = false) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    private String phone;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles = new LinkedHashSet<>();

    protected User() {}

    public User(String email, String passwordHash, String firstName, String lastName, String phone, Set<String> roles) {
        this.id = UUID.randomUUID();
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.roles = new LinkedHashSet<>(roles);
        this.createdAt = this.updatedAt = Instant.now();
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    public boolean isLocked(Instant now) { return lockedUntil != null && lockedUntil.isAfter(now); }

    public void recordFailedLogin(int max, java.time.Duration lockout) {
        failedAttempts++;
        if (failedAttempts >= max) {
            lockedUntil = Instant.now().plus(lockout);
            failedAttempts = 0;
        }
    }

    public void recordSuccessfulLogin() {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = Instant.now();
    }

    public String fullName() { return firstName + " " + lastName; }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String h) { this.passwordHash = h; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> r) { this.roles.clear(); this.roles.addAll(r); }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public int getFailedAttempts() { return failedAttempts; }
}
