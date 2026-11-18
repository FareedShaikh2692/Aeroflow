package com.aeroflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    static final String SECRET = "test-secret-test-secret-test-secret-0123456789";
    final JwtService jwt = new JwtService(new JwtProperties(SECRET, "aeroflow-auth", Duration.ofMinutes(5), null));

    @Test
    void roundTripsClaims() {
        UUID id = UUID.randomUUID();
        var user = jwt.verify(jwt.issueAccessToken(id, "a@b.co", "A B", List.of("ADMIN", "FLIGHT_AGENT"))).orElseThrow();
        assertThat(user.id()).isEqualTo(id);
        assertThat(user.roles()).containsExactly("ADMIN", "FLIGHT_AGENT");
        assertThat(user.isStaff()).isTrue();
        assertThat(user.hasRole("PASSENGER")).isFalse();
    }

    @Test
    void rejectsExpiredForeignIssuerAndForeignKey() {
        assertThat(jwt.verify(jwt.issue(UUID.randomUUID(), "a", "a", List.of(), Duration.ofSeconds(-5)))).isEmpty();
        JwtService otherIssuer = new JwtService(new JwtProperties(SECRET, "evil", null, null));
        assertThat(jwt.verify(otherIssuer.issueAccessToken(UUID.randomUUID(), "a", "a", List.of("ADMIN")))).isEmpty();
        JwtService otherKey = new JwtService(new JwtProperties("another-secret-another-secret-another-0123", null, null, null));
        assertThat(jwt.verify(otherKey.issueAccessToken(UUID.randomUUID(), "a", "a", List.of("ADMIN")))).isEmpty();
        assertThat(jwt.verify("not.a.jwt")).isEmpty();
    }

    @Test
    void refusesWeakOrMissingSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("short", null, null, null))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(new JwtProperties(null, null, null, null))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void serviceTokensCarryServiceRoleAndAreCached() {
        ServiceTokenProvider p = new ServiceTokenProvider(jwt, "booking-service");
        String t = p.token();
        assertThat(p.token()).isSameAs(t);
        assertThat(jwt.verify(t).orElseThrow().roles()).containsExactly(Roles.SERVICE);
    }
}
