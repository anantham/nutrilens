package com.nutritheous.validation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ValidationResult helper methods and filtering.
 */
class ValidationResultTest {

    @Test
    void testSuccess_CreatesValidResult() {
        ValidationResult result = ValidationResult.success();

        assertTrue(result.isValid());
        assertNotNull(result.getIssues());
        assertEquals(0, result.getIssues().size());
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    void testGetErrors_FiltersOnlyErrors() {
        ValidationIssue error1 = ValidationIssue.error("field1", "Error 1");
        ValidationIssue error2 = ValidationIssue.error("field2", "Error 2");
        ValidationIssue warning1 = ValidationIssue.warning("field3", "Warning 1");

        ValidationResult result = ValidationResult.builder()
                .valid(false)
                .issues(Arrays.asList(error1, warning1, error2))
                .build();

        List<ValidationIssue> errors = result.getErrors();

        assertEquals(2, errors.size());
        assertTrue(errors.contains(error1));
        assertTrue(errors.contains(error2));
        assertFalse(errors.contains(warning1));
    }

    @Test
    void testGetWarnings_FiltersOnlyWarnings() {
        ValidationIssue error1 = ValidationIssue.error("field1", "Error 1");
        ValidationIssue warning1 = ValidationIssue.warning("field2", "Warning 1");
        ValidationIssue warning2 = ValidationIssue.warning("field3", "Warning 2");

        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .issues(Arrays.asList(error1, warning1, warning2))
                .build();

        List<ValidationIssue> warnings = result.getWarnings();

        assertEquals(2, warnings.size());
        assertTrue(warnings.contains(warning1));
        assertTrue(warnings.contains(warning2));
        assertFalse(warnings.contains(error1));
    }

    @Test
    void testHasErrors_TrueWhenErrorsPresent() {
        ValidationResult result = ValidationResult.builder()
                .valid(false)
                .issues(Arrays.asList(
                        ValidationIssue.error("field1", "Error"),
                        ValidationIssue.warning("field2", "Warning")
                ))
                .build();

        assertTrue(result.hasErrors());
    }

    @Test
    void testHasErrors_FalseWhenOnlyWarnings() {
        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .issues(Arrays.asList(
                        ValidationIssue.warning("field1", "Warning 1"),
                        ValidationIssue.warning("field2", "Warning 2")
                ))
                .build();

        assertFalse(result.hasErrors());
    }

    @Test
    void testHasWarnings_TrueWhenWarningsPresent() {
        ValidationResult result = ValidationResult.builder()
                .valid(false)
                .issues(Arrays.asList(
                        ValidationIssue.error("field1", "Error"),
                        ValidationIssue.warning("field2", "Warning")
                ))
                .build();

        assertTrue(result.hasWarnings());
    }

    @Test
    void testHasWarnings_FalseWhenOnlyErrors() {
        ValidationResult result = ValidationResult.builder()
                .valid(false)
                .issues(Arrays.asList(
                        ValidationIssue.error("field1", "Error 1"),
                        ValidationIssue.error("field2", "Error 2")
                ))
                .build();

        assertFalse(result.hasWarnings());
    }

    @Test
    void testEmptyIssues_NoErrorsOrWarnings() {
        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .build();

        assertEquals(0, result.getErrors().size());
        assertEquals(0, result.getWarnings().size());
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    void testBuilder_WithDefaultIssues() {
        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .build();

        assertNotNull(result.getIssues());
        assertEquals(0, result.getIssues().size());
    }

    @Test
    void testIsValid_FalseWithErrors() {
        ValidationResult result = ValidationResult.builder()
                .valid(false)
                .issues(Arrays.asList(
                        ValidationIssue.error("field1", "Critical error")
                ))
                .build();

        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
    }

    @Test
    void testIsValid_TrueWithOnlyWarnings() {
        ValidationResult result = ValidationResult.builder()
                .valid(true)
                .issues(Arrays.asList(
                        ValidationIssue.warning("field1", "Just a warning")
                ))
                .build();

        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        assertFalse(result.hasErrors());
    }
}
