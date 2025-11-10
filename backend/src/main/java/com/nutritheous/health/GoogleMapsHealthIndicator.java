package com.nutritheous.health;

import com.nutritheous.service.LocationContextService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Google Maps API service.
 * Monitors circuit breaker state and API configuration status.
 */
@Component
@Slf4j
public class GoogleMapsHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final LocationContextService locationContextService;

    public GoogleMapsHealthIndicator(
            CircuitBreakerRegistry circuitBreakerRegistry,
            LocationContextService locationContextService) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.locationContextService = locationContextService;
    }

    @Override
    public Health health() {
        try {
            // First check if API is configured
            if (!locationContextService.isConfigured()) {
                return Health.down()
                        .withDetail("status", "API key not configured")
                        .withDetail("configured", false)
                        .build();
            }

            // Get the circuit breaker instance for Google Maps
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("googlemaps");

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
                    .withDetail("configured", true)
                    .withDetail("circuitBreakerState", state.name())
                    .withDetail("failureRate", String.format("%.2f%%", metrics.getFailureRate()))
                    .withDetail("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls())
                    .withDetail("numberOfFailedCalls", metrics.getNumberOfFailedCalls())
                    .withDetail("numberOfNotPermittedCalls", metrics.getNumberOfNotPermittedCalls())
                    .build();

        } catch (Exception e) {
            log.error("Error checking Google Maps health: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
