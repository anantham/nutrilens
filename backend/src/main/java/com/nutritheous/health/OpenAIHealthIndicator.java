package com.nutritheous.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for OpenAI API service.
 * Monitors circuit breaker state and provides health status for monitoring systems.
 */
@Component
@Slf4j
public class OpenAIHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public OpenAIHealthIndicator(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public Health health() {
        try {
            // Get the circuit breaker instance for OpenAI
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("openai");

            CircuitBreaker.State state = circuitBreaker.getState();
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();

            // Build health response with circuit breaker details
            Health.Builder healthBuilder = switch (state) {
                case CLOSED -> Health.up()
                        .withDetail("status", "Circuit closed - service operational");
                case HALF_OPEN -> Health.up()
                        .withDetail("status", "Circuit half-open - testing service recovery");
                case OPEN -> Health.down()
                        .withDetail("status", "Circuit open - service unavailable");
                case DISABLED, FORCED_OPEN, METRICS_ONLY -> Health.unknown()
                        .withDetail("status", "Circuit state: " + state);
            };

            return healthBuilder
                    .withDetail("circuitBreakerState", state.name())
                    .withDetail("failureRate", String.format("%.2f%%", metrics.getFailureRate()))
                    .withDetail("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls())
                    .withDetail("numberOfFailedCalls", metrics.getNumberOfFailedCalls())
                    .withDetail("numberOfNotPermittedCalls", metrics.getNumberOfNotPermittedCalls())
                    .build();

        } catch (Exception e) {
            log.error("Error checking OpenAI health: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
