package com.aeroflow.common.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Mints and caches a short-lived JWT carrying ROLE_SERVICE for internal service-to-service calls. */
@Component
public class ServiceTokenProvider {
    private static final UUID SERVICE_ID = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private final JwtService jwt;
    private final String serviceName;
    private volatile String cached;
    private volatile Instant refreshAt = Instant.EPOCH;

    public ServiceTokenProvider(JwtService jwt, @Value("${spring.application.name:aeroflow}") String serviceName) {
        this.jwt = jwt;
        this.serviceName = serviceName;
    }

    public synchronized String token() {
        if (cached == null || Instant.now().isAfter(refreshAt)) {
            cached = jwt.issue(SERVICE_ID, serviceName + "@services.aeroflow.internal", serviceName,
                    List.of(Roles.SERVICE), jwt.serviceTokenTtl());
            refreshAt = Instant.now().plus(jwt.serviceTokenTtl().dividedBy(2));
        }
        return cached;
    }
}
