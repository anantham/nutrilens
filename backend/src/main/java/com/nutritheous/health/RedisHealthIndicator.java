package com.nutritheous.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Redis cache service.
 * Monitors Redis connectivity and cache availability.
 */
@Component
@Slf4j
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        try {
            // Try to get a connection and ping Redis
            RedisConnection connection = redisConnectionFactory.getConnection();

            try {
                String pong = connection.ping();

                if ("PONG".equalsIgnoreCase(pong)) {
                    return Health.up()
                            .withDetail("status", "Redis operational")
                            .withDetail("response", pong)
                            .build();
                } else {
                    return Health.down()
                            .withDetail("status", "Unexpected Redis response")
                            .withDetail("response", pong)
                            .build();
                }
            } finally {
                connection.close();
            }

        } catch (Exception e) {
            log.error("Error checking Redis health: {}", e.getMessage());
            return Health.down()
                    .withDetail("status", "Redis unavailable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
