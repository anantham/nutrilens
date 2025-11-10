package com.nutritheous.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Health indicator for PostgreSQL database.
 * Monitors database connectivity and query responsiveness.
 */
@Component
@Slf4j
public class PostgreSQLHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public PostgreSQLHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {

            // Execute a simple query to verify database is responsive
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT version()")) {

                if (resultSet.next()) {
                    String version = resultSet.getString(1);

                    return Health.up()
                            .withDetail("status", "Database operational")
                            .withDetail("database", "PostgreSQL")
                            .withDetail("version", version)
                            .build();
                } else {
                    return Health.down()
                            .withDetail("status", "Database query returned no results")
                            .build();
                }
            }

        } catch (Exception e) {
            log.error("Error checking PostgreSQL health: {}", e.getMessage());
            return Health.down()
                    .withDetail("status", "Database unavailable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
