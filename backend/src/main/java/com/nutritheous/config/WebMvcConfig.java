package com.nutritheous.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for interceptors and other web-related settings.
 */
@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitingInterceptor rateLimitingInterceptor;

    public WebMvcConfig(RateLimitingInterceptor rateLimitingInterceptor) {
        this.rateLimitingInterceptor = rateLimitingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("Registering rate limiting interceptor for API endpoints");

        registry.addInterceptor(rateLimitingInterceptor)
                .addPathPatterns("/api/**")  // Apply to all API endpoints
                .excludePathPatterns(
                        "/api/auth/login",      // Don't rate limit login (has separate protection)
                        "/api/auth/register",   // Don't rate limit registration
                        "/api/health",          // Don't rate limit health checks
                        "/actuator/**"          // Don't rate limit actuator endpoints
                );

        log.info("Rate limiting interceptor registered successfully");
    }
}
