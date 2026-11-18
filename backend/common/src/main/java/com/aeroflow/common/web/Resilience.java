package com.aeroflow.common.web;

import com.aeroflow.common.error.ApiException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** Retry (exponential backoff) + circuit breaker around synchronous service-to-service calls. */
public final class Resilience {
    private static final Map<String, Retry> RETRIES = new ConcurrentHashMap<>();
    private static final Map<String, CircuitBreaker> BREAKERS = new ConcurrentHashMap<>();

    private static final RetryConfig RETRY = RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(200), 2.0))
            .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
            .build();

    private static final CircuitBreakerConfig BREAKER = CircuitBreakerConfig.custom()
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(15))
            .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
            .build();

    private Resilience() {}

    public static <T> T call(String name, Supplier<T> supplier) {
        Retry retry = RETRIES.computeIfAbsent(name, n -> Retry.of(n, RETRY));
        CircuitBreaker cb = BREAKERS.computeIfAbsent(name, n -> CircuitBreaker.of(n, BREAKER));
        try {
            return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(cb, supplier)).get();
        } catch (CallNotPermittedException | ResourceAccessException | HttpServerErrorException e) {
            throw ApiException.upstream("Dependency '" + name + "' is unavailable, please retry shortly");
        }
    }
}
