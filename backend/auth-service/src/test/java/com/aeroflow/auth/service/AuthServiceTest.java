package com.aeroflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aeroflow.auth.api.AuthDtos.*;
import com.aeroflow.auth.domain.RefreshToken;
import com.aeroflow.auth.domain.User;
import com.aeroflow.auth.repo.RefreshTokenRepository;
import com.aeroflow.auth.repo.UserRepository;
import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.common.security.JwtProperties;
import com.aeroflow.common.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock DomainEventPublisher events;
    @Mock AuditService audit;
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final JwtService jwt = new JwtService(new JwtProperties("test-secret-test-secret-test-secret-0123456789", null, null, null));
    AuthService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, refreshTokens, encoder, jwt, events, audit, Duration.ofDays(7), 3, Duration.ofMinutes(15));
        user = new User("Ops@AeroFlow.dev", encoder.encode("Correct#Pass1"), "Priya", "Nair", null, Set.of("OPERATIONS_MANAGER"));
        when(users.findByEmail("ops@aeroflow.dev")).thenReturn(Optional.of(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokens.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void loginIssuesVerifiableAccessTokenAndHashedRefreshToken() {
        TokenResponse r = service.login(new LoginRequest("ops@aeroflow.dev", "Correct#Pass1"), "JUnit", "127.0.0.1");
        assertThat(jwt.verify(r.accessToken())).hasValueSatisfying(u -> {
            assertThat(u.id()).isEqualTo(user.getId());
            assertThat(u.roles()).containsExactly("OPERATIONS_MANAGER");
        });
        ArgumentCaptor<RefreshToken> stored = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(stored.capture());
        assertThat(r.refreshToken()).hasSizeGreaterThan(40);
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("ops@aeroflow.dev");
    }

    @Test
    void wrongPasswordLocksAccountAfterMaxAttempts() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("ops@aeroflow.dev", "wrong"), null, null))
                    .extracting("code").isEqualTo("INVALID_CREDENTIALS");
        }
        assertThat(user.isLocked(Instant.now())).isTrue();
        assertThatThrownBy(() -> service.login(new LoginRequest("ops@aeroflow.dev", "Correct#Pass1"), null, null))
                .extracting("code").isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void unknownEmailAndDisabledAccountsAreRejected() {
        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@aeroflow.dev", "x"), null, null))
                .extracting("code").isEqualTo("INVALID_CREDENTIALS");
        user.setEnabled(false);
        assertThatThrownBy(() -> service.login(new LoginRequest("ops@aeroflow.dev", "Correct#Pass1"), null, null))
                .extracting("code").isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    void refreshRotatesTokenWithinSameFamily() {
        UUID family = UUID.randomUUID();
        RefreshToken current = new RefreshToken(user.getId(), TokenHasher.hash("old-token"), family, Instant.now().plusSeconds(600), null, null);
        when(refreshTokens.findByTokenHash(TokenHasher.hash("old-token"))).thenReturn(Optional.of(current));
        TokenResponse r = service.refresh("old-token", null, null);
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(r.refreshToken()).isNotEqualTo("old-token");
        verify(refreshTokens).save(argThat(t -> t.getFamilyId().equals(family)));
    }

    @Test
    void reusingRevokedRefreshTokenRevokesTheWholeFamily() {
        UUID family = UUID.randomUUID();
        RefreshToken used = new RefreshToken(user.getId(), TokenHasher.hash("stolen"), family, Instant.now().plusSeconds(600), null, null);
        used.revoke();
        when(refreshTokens.findByTokenHash(TokenHasher.hash("stolen"))).thenReturn(Optional.of(used));
        assertThatThrownBy(() -> service.refresh("stolen", null, null)).extracting("code").isEqualTo("INVALID_REFRESH_TOKEN");
        verify(refreshTokens).revokeFamily(eq(family), any());
    }

    @Test
    void expiredOrUnknownRefreshTokensAreRejected() {
        RefreshToken expired = new RefreshToken(user.getId(), TokenHasher.hash("exp"), UUID.randomUUID(), Instant.now().minusSeconds(1), null, null);
        when(refreshTokens.findByTokenHash(TokenHasher.hash("exp"))).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.refresh("exp", null, null)).extracting("code").isEqualTo("INVALID_REFRESH_TOKEN");
        assertThatThrownBy(() -> service.refresh("nope", null, null)).extracting("code").isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void registerCreatesPassengerAndPublishesEvent() {
        when(users.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        TokenResponse r = service.register(new RegisterRequest("new@example.com", "Str0ng!Passw", "New", "User", null), null, null);
        assertThat(r.user().roles()).containsExactly("PASSENGER");
        verify(events).publish(any(), eq(EventTypes.USER_REGISTERED), any(), any());
        when(users.existsByEmailIgnoreCase("new@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("new@example.com", "Str0ng!Passw", "N", "U", null), null, null))
                .extracting("code").isEqualTo("EMAIL_IN_USE");
    }

    @Test
    void changePasswordVerifiesCurrentAndRevokesSessions() {
        assertThatThrownBy(() -> service.changePassword(user.getId(), new ChangePasswordRequest("bad", "New#Passw0rd!")))
                .extracting("code").isEqualTo("INVALID_PASSWORD");
        service.changePassword(user.getId(), new ChangePasswordRequest("Correct#Pass1", "New#Passw0rd!"));
        assertThat(encoder.matches("New#Passw0rd!", user.getPasswordHash())).isTrue();
        verify(refreshTokens).revokeAllForUser(eq(user.getId()), any());
    }

    @Test
    void tokenHasherIsDeterministicAndTokensAreRandom() {
        assertThat(TokenHasher.hash("abc")).isEqualTo(TokenHasher.hash("abc")).hasSize(64);
        assertThat(TokenHasher.newToken()).isNotEqualTo(TokenHasher.newToken());
    }
}
