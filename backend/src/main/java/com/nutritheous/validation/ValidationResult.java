package com.nutritheous.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of validating AI-generated nutrition data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    /**
     * True if data passes validation (no ERROR-level issues)
     */
    private boolean valid;

    /**
     * List of validation issues found (errors and warnings)
     */
    @Builder.Default
    private List<ValidationIssue> issues = new ArrayList<>();

    /**
     * Get only ERROR-level issues
     */
    public List<ValidationIssue> getErrors() {
        return issues.stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.ERROR)
                .collect(Collectors.toList());
    }

    /**
     * Get only WARNING-level issues
     */
    public List<ValidationIssue> getWarnings() {
        return issues.stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING)
                .collect(Collectors.toList());
    }

    /**
     * Check if there are any errors
     */
    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.getSeverity() == ValidationIssue.Severity.ERROR);
    }

    /**
     * Check if there are any warnings
     */
    public boolean hasWarnings() {
        return issues.stream().anyMatch(i -> i.getSeverity() == ValidationIssue.Severity.WARNING);
    }

    /**
     * Create a successful validation result with no issues
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
                .valid(true)
                .issues(new ArrayList<>())
                .build();
    }
}
