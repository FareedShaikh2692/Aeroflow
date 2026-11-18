package com.aeroflow.common.config;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Redis-backed caching for services that include spring-data-redis. Cache failures are logged and
 * treated as a miss, so a Redis outage degrades latency rather than availability.
 */
@Configuration
@EnableCaching
@ConditionalOnClass(name = "org.springframework.data.redis.cache.RedisCacheManager")
public class CacheConfig implements CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    public static final Map<String, Duration> TTLS = Map.of(
            "airports", Duration.ofHours(6),
            "aircraft", Duration.ofHours(1),
            "flight", Duration.ofMinutes(5),
            "flightSearch", Duration.ofSeconds(60),
            "flightLayout", Duration.ofMinutes(10));

    @Bean
    RedisCacheManagerBuilderCustomizer aeroflowCacheTtls() {
        return builder -> {
            builder.cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(5)).prefixCacheNameWith("aeroflow:").disableCachingNullValues());
            TTLS.forEach((name, ttl) -> builder.withCacheConfiguration(name,
                    RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl).prefixCacheNameWith("aeroflow:")
                            .disableCachingNullValues()));
        };
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            public void handleCacheGetError(RuntimeException e, Cache c, Object k) { warn("get", c, e); }
            public void handleCachePutError(RuntimeException e, Cache c, Object k, Object v) { warn("put", c, e); }
            public void handleCacheEvictError(RuntimeException e, Cache c, Object k) { warn("evict", c, e); }
            public void handleCacheClearError(RuntimeException e, Cache c) { warn("clear", c, e); }
        };
    }

    private static void warn(String op, Cache c, RuntimeException e) {
        log.warn("Cache {} failed on '{}' - continuing without cache: {}", op, c.getName(), e.getMessage());
    }
}
