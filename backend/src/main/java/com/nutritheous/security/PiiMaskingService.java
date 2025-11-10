package com.nutritheous.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for masking Personally Identifiable Information (PII) in logs and outputs.
 * Prevents sensitive data from being exposed in log files.
 *
 * PII types handled:
 * - Email addresses
 * - Phone numbers
 * - Credit card numbers
 * - Social Security Numbers
 * - IP addresses
 * - JWT tokens
 * - API keys
 */
@Service
@Slf4j
public class PiiMaskingService {

    // Pattern to detect email addresses
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    // Pattern to detect phone numbers (various formats)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?1[-.]?)?\\(?([0-9]{3})\\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\\b"
    );

    // Pattern to detect credit card numbers
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b"
    );

    // Pattern to detect SSN (Social Security Numbers)
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    // Pattern to detect IPv4 addresses
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
    );

    // Pattern to detect JWT tokens
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*\\b"
    );

    // Pattern to detect API keys (common formats)
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9]{32,}\\b"
    );

    /**
     * Masks all PII in the given text.
     *
     * @param text Text that may contain PII
     * @return Text with PII masked
     */
    public String maskPii(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String masked = text;

        // Mask emails
        masked = maskEmails(masked);

        // Mask phone numbers
        masked = maskPhoneNumbers(masked);

        // Mask credit cards
        masked = maskCreditCards(masked);

        // Mask SSNs
        masked = maskSsn(masked);

        // Mask JWT tokens
        masked = maskJwtTokens(masked);

        return masked;
    }

    /**
     * Masks email addresses while preserving the first character and domain.
     * Example: john.doe@example.com → j***@example.com
     */
    public String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@");
        if (parts.length != 2 || parts[0].isEmpty()) {
            return "***@***";
        }

        String localPart = parts[0];
        String domain = parts[1];

        String maskedLocal;
        if (localPart.length() == 1) {
            maskedLocal = "*";
        } else {
            maskedLocal = localPart.charAt(0) + "***";
        }

        return maskedLocal + "@" + domain;
    }

    /**
     * Masks all email addresses in text.
     */
    private String maskEmails(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String email = matcher.group();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(maskEmail(email)));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Masks phone numbers.
     * Example: 555-123-4567 → XXX-XXX-4567
     */
    private String maskPhoneNumbers(String text) {
        return PHONE_PATTERN.matcher(text).replaceAll("XXX-XXX-$3");
    }

    /**
     * Masks credit card numbers, showing only last 4 digits.
     * Example: 4532-1234-5678-9010 → ****-****-****-9010
     */
    private String maskCreditCards(String text) {
        Matcher matcher = CREDIT_CARD_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String card = matcher.group();
            String lastFour = card.replaceAll("[^0-9]", "");
            lastFour = lastFour.substring(Math.max(0, lastFour.length() - 4));
            matcher.appendReplacement(sb, "****-****-****-" + lastFour);
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Masks Social Security Numbers.
     * Example: 123-45-6789 → XXX-XX-6789
     */
    private String maskSsn(String text) {
        Matcher matcher = SSN_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String ssn = matcher.group();
            String[] parts = ssn.split("-");
            if (parts.length == 3) {
                matcher.appendReplacement(sb, "XXX-XX-" + parts[2]);
            }
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Masks IP addresses (preserves first octet).
     * Example: 192.168.1.100 → 192.*.*.*
     */
    public String maskIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }

        Matcher matcher = IP_PATTERN.matcher(ip);
        if (matcher.find()) {
            String[] octets = ip.split("\\.");
            if (octets.length == 4) {
                return octets[0] + ".*.*.*";
            }
        }

        return ip;
    }

    /**
     * Masks JWT tokens, showing only the first few characters.
     * Example: eyJhbGc...xyz123 → eyJh...***
     */
    private String maskJwtTokens(String text) {
        Matcher matcher = JWT_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String token = matcher.group();
            String masked = token.substring(0, Math.min(10, token.length())) + "...***";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Masks API keys and long alphanumeric strings.
     * Shows only first 4 and last 4 characters.
     */
    public String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }

        String start = apiKey.substring(0, 4);
        String end = apiKey.substring(apiKey.length() - 4);

        return start + "..." + end;
    }

    /**
     * Masks a user ID for logging (keeps first 8 characters of UUID).
     */
    public String maskUserId(String userId) {
        if (userId == null || userId.length() < 8) {
            return "****";
        }

        return userId.substring(0, 8) + "...";
    }

    /**
     * Creates a safe log message with masked PII.
     * Use this when logging user data.
     */
    public String createSafeLogMessage(String message) {
        return maskPii(message);
    }
}
