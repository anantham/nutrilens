package com.nutritheous.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Compression;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for HTTP response compression.
 * Compresses responses to reduce bandwidth and improve client load times.
 *
 * Performance impact:
 * - Typical JSON responses compress 70-90% (e.g., 10KB → 1-3KB)
 * - CPU overhead is minimal (< 5ms for most responses)
 * - Significantly reduces bandwidth costs
 * - Improves page load times, especially on mobile networks
 */
@Configuration
@Slf4j
public class CompressionConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> compressionCustomizer() {
        log.info("🗜️  Configuring HTTP response compression");

        return factory -> {
            Compression compression = new Compression();

            // Enable compression
            compression.setEnabled(true);

            // Minimum response size to compress (default 2048 bytes)
            // Don't compress tiny responses (overhead not worth it)
            compression.setMinResponseSize(2048); // 2 KB

            // MIME types to compress
            compression.setMimeTypes(new String[]{
                    "text/html",
                    "text/xml",
                    "text/plain",
                    "text/css",
                    "text/javascript",
                    "application/javascript",
                    "application/json",
                    "application/xml",
                    "application/x-javascript",
                    "image/svg+xml"
            });

            // Exclude specific paths if needed (e.g., file downloads that are already compressed)
            // compression.setExcludedUserAgents(...);

            factory.setCompression(compression);

            log.info("✅ HTTP compression enabled");
            log.info("   Min response size: {} bytes", compression.getMinResponseSize());
            log.info("   MIME types compressed: {}", String.join(", ", compression.getMimeTypes()));
        };
    }
}
