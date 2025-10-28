package com.nutritheous.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single validation issue found in AI-generated nutrition data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssue {

    /**
     * Severity of the issue
     */
    private Severity severity;

    /**
     * Field name that has the issue (e.g., "calories", "protein_g")
     */
    private String field;

    /**
     * Human-readable description of the problem
     */
    private String message;

    /**
     * Suggested corrected value (optional)
     */
    private Double suggestedFix;

    /**
     * Original value that failed validation
     */
    private Double actualValue;

    public enum Severity {
        /**
         * Critical error - data is impossible/invalid (e.g., fiber > carbs)
         */
        ERROR,

        /**
         * Warning - data is suspicious but not impossible (e.g., very high calories)
         */
        WARNING
    }

    /**
     * Create an error-level validation issue
     */
    public static ValidationIssue error(String field, String message) {
        return ValidationIssue.builder()
                .severity(Severity.ERROR)
                .field(field)
                .message(message)
                .build();
    }

    /**
     * Create a warning-level validation issue
     */
    public static ValidationIssue warning(String field, String message) {
        return ValidationIssue.builder()
                .severity(Severity.WARNING)
                .field(field)
                .message(message)
                .build();
    }

    /**
     * Create an error with a suggested fix
     */
    public static ValidationIssue errorWithFix(String field, String message, Double actualValue, Double suggestedFix) {
        return ValidationIssue.builder()
                .severity(Severity.ERROR)
                .field(field)
                .message(message)
                .actualValue(actualValue)
                .suggestedFix(suggestedFix)
                .build();
    }
}
