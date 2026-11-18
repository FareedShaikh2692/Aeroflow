package com.aeroflow.common.security;

import com.aeroflow.common.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Shared stateless security chain. Each service lists its anonymous endpoints in
 * {@code aeroflow.security.public-paths}. CORS is enforced at the API gateway.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, ObjectMapper mapper,
                                            @Value("${aeroflow.security.public-paths:}") List<String> publicPaths,
                                            @Value("${aeroflow.cors.allowed-origins:}") List<String> corsOrigins,
                                            @Value("${aeroflow.cors.allowed-origin-patterns:}") List<String> corsPatterns)
            throws Exception {
        String[] open = publicPaths.stream().filter(p -> !p.isBlank()).toArray(String[]::new);
        http.csrf(c -> c.disable())
            // CORS is normally terminated at the gateway; the single-deployable mode has no gateway.
            .cors(c -> {
                List<String> origins = corsOrigins.stream().filter(o -> !o.isBlank()).toList();
                List<String> patterns = corsPatterns.stream().filter(o -> !o.isBlank()).toList();
                if (origins.isEmpty() && patterns.isEmpty()) { c.disable(); return; }
                org.springframework.web.cors.CorsConfiguration cfg = new org.springframework.web.cors.CorsConfiguration();
                cfg.setAllowedOrigins(origins);
                if (!patterns.isEmpty()) cfg.setAllowedOriginPatterns(patterns);
                cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id", "If-Match"));
                cfg.setExposedHeaders(List.of("X-Correlation-Id", "Content-Disposition"));
                cfg.setMaxAge(3600L);
                org.springframework.web.cors.UrlBasedCorsConfigurationSource src = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
                src.registerCorsConfiguration("/**", cfg);
                c.configurationSource(src);
            })
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .authorizeHttpRequests(a -> {
                a.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll();
                a.requestMatchers("/internal/**").hasRole(Roles.SERVICE);
                if (open.length > 0) a.requestMatchers(open).permitAll();
                a.anyRequest().authenticated();
            })
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> write(mapper, req, res, HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED", "Authentication required"))
                .accessDeniedHandler((req, res, ex) -> write(mapper, req, res, HttpStatus.FORBIDDEN,
                        "FORBIDDEN", "You do not have permission to perform this action")))
            .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'"))
                           .frameOptions(f -> f.deny()))
            .addFilterBefore(new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void write(ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res, HttpStatus status,
                              String code, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, req.getRequestURI(), MDC.get("traceId"), List.of()));
    }
}
