package com.nutritheous.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service for sanitizing user input to prevent XSS and injection attacks.
 * Provides methods for cleaning HTML, SQL injection prevention, and general text sanitization.
 */
@Service
@Slf4j
public class InputSanitizationService {

    // Pattern to detect HTML tags
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    // Pattern to detect script tags and javascript: URLs
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<script[^>]*>.*?</script>|javascript:|on\\w+\\s*=",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to detect SQL injection attempts
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            ".*(union|select|insert|update|delete|drop|create|alter|exec|script|javascript|<script).*",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to detect excessive whitespace
    private static final Pattern EXCESSIVE_WHITESPACE = Pattern.compile("\\s{2,}");

    // Pattern to detect control characters (except newline and tab)
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    /**
     * Sanitizes user-provided meal description by removing HTML and potentially dangerous content.
     * Preserves line breaks and basic formatting.
     *
     * @param description Raw user input
     * @return Sanitized description safe for storage and display
     */
    public String sanitizeMealDescription(String description) {
        if (description == null || description.isBlank()) {
            return description;
        }

        String sanitized = description;

        // Remove script tags and JavaScript
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");

        // Remove all HTML tags
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");

        // Remove control characters (except newlines and tabs)
        sanitized = CONTROL_CHARACTERS.matcher(sanitized).replaceAll("");

        // Normalize whitespace (but preserve single newlines)
        sanitized = EXCESSIVE_WHITESPACE.matcher(sanitized).replaceAll(" ");

        // Trim and limit length
        sanitized = sanitized.trim();

        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000);
            log.warn("Description truncated to 1000 characters");
        }

        return sanitized;
    }

    /**
     * Sanitizes general text input by removing HTML and control characters.
     *
     * @param input Raw user input
     * @return Sanitized text
     */
    public String sanitizeText(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String sanitized = input;

        // Remove HTML tags
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");

        // Remove control characters
        sanitized = CONTROL_CHARACTERS.matcher(sanitized).replaceAll("");

        // Normalize whitespace
        sanitized = EXCESSIVE_WHITESPACE.matcher(sanitized).replaceAll(" ");

        return sanitized.trim();
    }

    /**
     * Checks if input contains potentially malicious SQL injection patterns.
     * This is a basic check; use parameterized queries for actual SQL injection prevention.
     *
     * @param input User input to check
     * @return true if input appears to contain SQL injection attempt
     */
    public boolean containsSqlInjection(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        return SQL_INJECTION_PATTERN.matcher(input).matches();
    }

    /**
     * Checks if input contains potentially malicious XSS patterns.
     *
     * @param input User input to check
     * @return true if input appears to contain XSS attempt
     */
    public boolean containsXss(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        return SCRIPT_PATTERN.matcher(input).find();
    }

    /**
     * Escapes HTML special characters to prevent XSS when displaying user input.
     * Use this when you need to preserve the exact input but display it safely.
     *
     * @param input Raw user input
     * @return HTML-escaped text
     */
    public String escapeHtml(String input) {
        if (input == null) {
            return null;
        }

        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }

    /**
     * Validates that an email address is in a safe format.
     * Prevents email header injection attacks.
     *
     * @param email Email address to validate
     * @return true if email is safe
     */
    public boolean isSafeEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        // Check for header injection characters
        if (email.contains("\n") || email.contains("\r") || email.contains("%0a") || email.contains("%0d")) {
            log.warn("Detected potential email header injection attempt: {}", email);
            return false;
        }

        // Basic email format validation (Spring Boot's @Email does more comprehensive validation)
        Pattern emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
        return emailPattern.matcher(email).matches();
    }

    /**
     * Sanitizes user-provided ingredient name.
     *
     * @param ingredientName Raw ingredient name
     * @return Sanitized ingredient name
     */
    public String sanitizeIngredientName(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return ingredientName;
        }

        String sanitized = sanitizeText(ingredientName);

        // Limit length
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
            log.warn("Ingredient name truncated to 255 characters");
        }

        return sanitized;
    }

    /**
     * Sanitizes health notes or other free-text fields.
     *
     * @param notes Raw notes
     * @return Sanitized notes
     */
    public String sanitizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return notes;
        }

        String sanitized = notes;

        // Remove scripts and HTML
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");

        // Remove control characters but preserve newlines
        sanitized = sanitized.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        // Normalize excessive whitespace
        sanitized = EXCESSIVE_WHITESPACE.matcher(sanitized).replaceAll(" ");

        return sanitized.trim();
    }
}
