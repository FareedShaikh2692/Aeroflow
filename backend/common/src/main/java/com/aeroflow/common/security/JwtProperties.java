package com.aeroflow.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aeroflow.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, Duration serviceTokenTtl) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) issuer = "aeroflow-auth";
        if (accessTokenTtl == null) accessTokenTtl = Duration.ofMinutes(15);
        if (serviceTokenTtl == null) serviceTokenTtl = Duration.ofMinutes(10);
    }
}
