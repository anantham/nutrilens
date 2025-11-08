package com.nutritheous.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ValidationIssue factory methods and builders.
 */
class ValidationIssueTest {

    @Test
    void testError_CreatesErrorSeverity() {
        ValidationIssue issue = ValidationIssue.error("calories", "Invalid value");

        assertEquals(ValidationIssue.Severity.ERROR, issue.getSeverity());
        assertEquals("calories", issue.getField());
        assertEquals("Invalid value", issue.getMessage());
        assertNull(issue.getSuggestedFix());
        assertNull(issue.getActualValue());
    }

    @Test
    void testWarning_CreatesWarningSeverity() {
        ValidationIssue issue = ValidationIssue.warning("sodium_mg", "High sodium");

        assertEquals(ValidationIssue.Severity.WARNING, issue.getSeverity());
        assertEquals("sodium_mg", issue.getField());
        assertEquals("High sodium", issue.getMessage());
        assertNull(issue.getSuggestedFix());
        assertNull(issue.getActualValue());
    }

    @Test
    void testErrorWithFix_IncludesAllFields() {
        ValidationIssue issue = ValidationIssue.errorWithFix(
                "calories",
                "Energy mismatch",
                1000.0,
                710.0
        );

        assertEquals(ValidationIssue.Severity.ERROR, issue.getSeverity());
        assertEquals("calories", issue.getField());
        assertEquals("Energy mismatch", issue.getMessage());
        assertEquals(1000.0, issue.getActualValue());
        assertEquals(710.0, issue.getSuggestedFix());
    }

    @Test
    void testBuilder_AllFieldsSet() {
        ValidationIssue issue = ValidationIssue.builder()
                .severity(ValidationIssue.Severity.WARNING)
                .field("protein_g")
                .message("Very high protein")
                .actualValue(200.0)
                .suggestedFix(150.0)
                .build();

        assertEquals(ValidationIssue.Severity.WARNING, issue.getSeverity());
        assertEquals("protein_g", issue.getField());
        assertEquals("Very high protein", issue.getMessage());
        assertEquals(200.0, issue.getActualValue());
        assertEquals(150.0, issue.getSuggestedFix());
    }

    @Test
    void testBuilder_MinimalFields() {
        ValidationIssue issue = ValidationIssue.builder()
                .severity(ValidationIssue.Severity.ERROR)
                .field("fiber_g")
                .message("Fiber exceeds carbs")
                .build();

        assertNotNull(issue);
        assertEquals(ValidationIssue.Severity.ERROR, issue.getSeverity());
        assertNull(issue.getSuggestedFix());
        assertNull(issue.getActualValue());
    }

    @Test
    void testSeverityEnum_HasCorrectValues() {
        assertEquals(2, ValidationIssue.Severity.values().length);

        ValidationIssue.Severity error = ValidationIssue.Severity.ERROR;
        ValidationIssue.Severity warning = ValidationIssue.Severity.WARNING;

        assertNotEquals(error, warning);
    }
}
