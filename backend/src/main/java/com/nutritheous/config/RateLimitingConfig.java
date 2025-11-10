package com.nutritheous.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for API rate limiting.
 * Provides rate limiters for different API endpoints and services.
 *
 * Rate limiters protect against:
 * - Excessive API usage that could exhaust external service quotas
 * - Potential DoS attacks
 * - Unintentional resource exhaustion from bugs or misconfiguration
 */
@Configuration
@Slf4j
public class RateLimitingConfig {

    /**
     * Creates a rate limiter registry with custom configurations.
     * This supplements the Resilience4j configuration in application-resilience.yml.
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        log.info("Initializing rate limiter registry");

        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();

        // User-facing API endpoints - generous limits per user
        RateLimiterConfig userApiConfig = RateLimiterConfig.custom()
                .limitForPeriod(100)  // 100 requests
                .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
                .timeoutDuration(Duration.ofSeconds(0))  // No wait, fail immediately
                .build();

        registry.rateLimiter("userApi", userApiConfig);
        log.info("Created rate limiter 'userApi': 100 requests/minute per user");

        // Meal upload endpoint - more restrictive to prevent abuse
        RateLimiterConfig uploadConfig = RateLimiterConfig.custom()
                .limitForPeriod(20)  // 20 uploads
                .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
                .timeoutDuration(Duration.ofSeconds(0))
                .build();

        registry.rateLimiter("mealUpload", uploadConfig);
        log.info("Created rate limiter 'mealUpload': 20 requests/minute");

        // Analytics endpoints - moderate limits
        RateLimiterConfig analyticsConfig = RateLimiterConfig.custom()
                .limitForPeriod(30)  // 30 requests
                .limitRefreshPeriod(Duration.ofMinutes(1))  // per minute
                .timeoutDuration(Duration.ofSeconds(0))
                .build();

        registry.rateLimiter("analytics", analyticsConfig);
        log.info("Created rate limiter 'analytics': 30 requests/minute");

        return registry;
    }

    /**
     * Creates a default rate limiter for general API endpoints.
     */
    @Bean
    public RateLimiter defaultRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("userApi");
    }
}
