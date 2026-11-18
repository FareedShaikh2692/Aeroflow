package com.aeroflow.gateway;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {
    static final String CORRELATION = "X-Correlation-Id";
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    /** CORS is terminated here (services never see browser preflights). */
    @Bean
    CorsWebFilter corsWebFilter(@Value("${aeroflow.cors.allowed-origins}") String origins,
                                @Value("${aeroflow.cors.allowed-origin-patterns:}") String patterns) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(split(origins));
        if (!patterns.isBlank()) cfg.setAllowedOriginPatterns(split(patterns));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "Idempotency-Key", CORRELATION));
        cfg.setExposedHeaders(List.of(CORRELATION, HttpHeaders.CONTENT_DISPOSITION, "X-RateLimit-Remaining"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return new CorsWebFilter(src);
    }

    @Bean
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just(clientIp(exchange.getRequest()));
    }

    /** Ensures every request carries a correlation id that downstream services log. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    GlobalFilter correlationIdFilter() {
        return (exchange, chain) -> {
            String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION);
            String id = incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
            ServerHttpRequest req = exchange.getRequest().mutate().headers(h -> h.set(CORRELATION, id)).build();
            exchange.getResponse().getHeaders().set(CORRELATION, id);
            return chain.filter(exchange.mutate().request(req).build());
        };
    }

    static String clientIp(ServerHttpRequest req) {
        String fwd = req.getHeaders().getFirst("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return Optional.ofNullable(req.getRemoteAddress()).map(a -> a.getAddress().getHostAddress()).orElse("unknown");
    }

    private static List<String> split(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
