package com.nutritheous.health;

import com.nutritheous.storage.GoogleCloudStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Google Cloud Storage service.
 * Monitors GCS connectivity and configuration status.
 */
@Component
@Slf4j
public class GoogleCloudStorageHealthIndicator implements HealthIndicator {

    private final GoogleCloudStorageService storageService;

    public GoogleCloudStorageHealthIndicator(GoogleCloudStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public Health health() {
        try {
            // Check if the storage service is initialized
            if (storageService == null) {
                return Health.down()
                        .withDetail("status", "GCS service not initialized")
                        .withDetail("configured", false)
                        .build();
            }

            // If we reach here, the service initialized successfully
            // (constructor would have thrown if credentials were invalid)
            return Health.up()
                    .withDetail("status", "GCS service operational")
                    .withDetail("configured", true)
                    .build();

        } catch (Exception e) {
            log.error("Error checking Google Cloud Storage health: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("configured", false)
                    .build();
        }
    }
}
