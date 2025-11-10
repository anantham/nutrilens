package com.nutritheous.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter that adds security headers to all HTTP responses.
 * These headers protect against common web vulnerabilities.
 *
 * Headers added:
 * - X-Content-Type-Options: Prevents MIME type sniffing
 * - X-Frame-Options: Prevents clickjacking attacks
 * - X-XSS-Protection: Enables XSS filter in older browsers
 * - Strict-Transport-Security: Enforces HTTPS
 * - Content-Security-Policy: Restricts resource loading
 * - Referrer-Policy: Controls referrer information
 * - Permissions-Policy: Controls browser features
 */
@Component
@Slf4j
public class SecurityHeadersFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("🛡️  Initializing Security Headers Filter");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Prevent MIME type sniffing
        // Ensures browsers respect the Content-Type header
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking attacks
        // Prevents the page from being displayed in an iframe
        httpResponse.setHeader("X-Frame-Options", "DENY");

        // Enable XSS protection (legacy browsers)
        // Modern browsers use CSP instead
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

        // Enforce HTTPS (only in production)
        // Remove this in local development if not using HTTPS
        // httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // Content Security Policy
        // Restricts which resources can be loaded
        httpResponse.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "font-src 'self' data:; " +
                "connect-src 'self' https:; " +
                "frame-ancestors 'none';"
        );

        // Referrer Policy
        // Controls how much referrer information is sent
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions Policy (formerly Feature Policy)
        // Disables potentially dangerous browser features
        httpResponse.setHeader("Permissions-Policy",
                "camera=(), " +
                "microphone=(), " +
                "geolocation=(), " +
                "payment=(), " +
                "usb=(), " +
                "magnetometer=(), " +
                "gyroscope=(), " +
                "accelerometer=()"
        );

        // Remove server information header (if present)
        // Prevents information disclosure about server technology
        httpResponse.setHeader("Server", "");

        // Cache control for sensitive endpoints
        // Prevent caching of authenticated responses
        String path = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
        if (path.startsWith("/api/") && !path.startsWith("/api/health") && !path.startsWith("/actuator/")) {
            httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setHeader("Expires", "0");
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        log.info("🛡️  Destroying Security Headers Filter");
    }
}
