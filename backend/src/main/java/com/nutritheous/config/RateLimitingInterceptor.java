package com.nutritheous.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP interceptor that applies rate limiting to API endpoints.
 * Uses Resilience4j rate limiters configured in RateLimitingConfig.
 *
 * Rate limits are applied per-user based on authenticated user ID.
 * For unauthenticated requests, limits are applied by IP address.
 */
@Component
@Slf4j
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final RateLimiterRegistry rateLimiterRegistry;

    public RateLimitingInterceptor(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // Determine which rate limiter to use based on endpoint
            String rateLimiterName = determineRateLimiterName(request);
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterName);

            // Get user identifier (user ID or IP address)
            String userId = getUserIdentifier(request);

            // Create a user-specific rate limiter instance
            String rateLimiterKey = rateLimiterName + ":" + userId;
            RateLimiter userRateLimiter = rateLimiterRegistry.rateLimiter(rateLimiterKey);

            // Check if request is permitted
            if (!userRateLimiter.acquirePermission()) {
                log.warn("Rate limit exceeded for user {} on endpoint {}", userId, request.getRequestURI());

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\": \"Too many requests\", " +
                        "\"message\": \"Rate limit exceeded. Please try again later.\", " +
                        "\"status\": 429}"
                );

                return false;
            }

            return true;

        } catch (RequestNotPermitted e) {
            log.warn("Rate limit exceeded: {}", e.getMessage());

            try {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\": \"Too many requests\", " +
                        "\"message\": \"Rate limit exceeded. Please try again later.\", " +
                        "\"status\": 429}"
                );
            } catch (Exception writeException) {
                log.error("Failed to write rate limit response", writeException);
            }

            return false;

        } catch (Exception e) {
            log.error("Error in rate limiting interceptor", e);
            // Allow request to proceed on error to avoid blocking legitimate traffic
            return true;
        }
    }

    /**
     * Determines which rate limiter to use based on the request path.
     */
    private String determineRateLimiterName(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (path.contains("/api/meals") && request.getMethod().equals("POST")) {
            return "mealUpload";
        } else if (path.contains("/api/analytics")) {
            return "analytics";
        } else {
            return "userApi";
        }
    }

    /**
     * Gets user identifier for rate limiting.
     * Uses authenticated user ID if available, otherwise falls back to IP address.
     */
    private String getUserIdentifier(HttpServletRequest request) {
        // Try to get user ID from JWT token or session (simplified)
        String userId = request.getHeader("X-User-Id");

        if (userId == null || userId.isEmpty()) {
            // Fall back to IP address for unauthenticated requests
            userId = getClientIpAddress(request);
        }

        return userId;
    }

    /**
     * Extracts client IP address, handling proxies and load balancers.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // If multiple IPs in X-Forwarded-For, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }
}
