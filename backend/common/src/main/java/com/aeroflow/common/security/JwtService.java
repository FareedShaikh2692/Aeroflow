package com.aeroflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256 access tokens. The signing secret is shared by all services via the
 * JWT_SECRET environment variable (see SECURITY.md for the RS256/JWKS upgrade path).
 */
@Service
public class JwtService {
    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        if (props.secret() == null || props.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long");
        }
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(UUID subject, String email, String name, Collection<String> roles, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(props.issuer())
                .subject(subject.toString())
                .claim("email", email)
                .claim("name", name)
                .claim("roles", List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public String issueAccessToken(UUID subject, String email, String name, Collection<String> roles) {
        return issue(subject, email, name, roles, props.accessTokenTtl());
    }

    public Duration accessTokenTtl() { return props.accessTokenTtl(); }
    public Duration serviceTokenTtl() { return props.serviceTokenTtl(); }

    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(props.issuer()).build()
                    .parseSignedClaims(token).getPayload();
            @SuppressWarnings("unchecked")
            List<String> roles = c.get("roles", List.class);
            return Optional.of(new AuthenticatedUser(UUID.fromString(c.getSubject()), c.get("email", String.class),
                    c.get("name", String.class), roles == null ? java.util.Set.of() : new LinkedHashSet<>(roles)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
