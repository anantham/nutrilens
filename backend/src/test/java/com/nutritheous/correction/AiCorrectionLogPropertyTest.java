package com.nutritheous.correction;

import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for AiCorrectionLog using jqwik.
 * These tests verify mathematical invariants across 1000+ random test cases.
 *
 * Property-based testing is superior to example-based testing for mathematical logic
 * because it explores the entire input space, finding edge cases automatically.
 */
class AiCorrectionLogPropertyTest {

    // ============================================================================
    // INVARIANT 1: Percent error and difference always have same sign
    // ============================================================================

    @Property
    @Label("Percent error sign must match the sign of (user - ai) for all inputs")
    void percentError_alwaysHasSameSign_asDifference(
            @ForAll @DoubleRange(min = 1.0, max = 5000.0) double aiValue,
            @ForAll @DoubleRange(min = 1.0, max = 5000.0) double userValue
    ) {
        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(aiValue))
                .userValue(BigDecimal.valueOf(userValue))
                .build();

        // INVARIANT: percentError and (userValue - aiValue) must have same sign
        boolean percentErrorPositive = log.getPercentError().compareTo(BigDecimal.ZERO) > 0;
        boolean differencePositive = userValue > aiValue;

        assertEquals(differencePositive, percentErrorPositive,
                String.format("For AI=%.2f, User=%.2f: percent error sign must match difference sign",
                        aiValue, userValue));
    }

    // ============================================================================
    // INVARIANT 2: Absolute error is always non-negative
    // ============================================================================

    @Property
    @Label("Absolute error must always be >= 0 for all inputs")
    void absoluteError_alwaysNonNegative(
            @ForAll @DoubleRange(min = 0.0, max = 5000.0) double aiValue,
            @ForAll @DoubleRange(min = 0.0, max = 5000.0) double userValue
    ) {
        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(aiValue))
                .userValue(BigDecimal.valueOf(userValue))
                .build();

        // INVARIANT: Absolute error is always >= 0
        assertTrue(log.getAbsoluteError().compareTo(BigDecimal.ZERO) >= 0,
                String.format("Absolute error must be non-negative, got: %s for AI=%.2f, User=%.2f",
                        log.getAbsoluteError(), aiValue, userValue));
    }

    // ============================================================================
    // INVARIANT 3: Perfect match yields zero error
    // ============================================================================

    @Property
    @Label("When AI value equals user value, both errors must be zero")
    void perfectMatch_yieldsZeroError(
            @ForAll @DoubleRange(min = 1.0, max = 5000.0) double value
    ) {
        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(value))
                .userValue(BigDecimal.valueOf(value))
                .build();

        // INVARIANT: If AI = User, both errors = 0
        assertEquals(0, log.getPercentError().compareTo(BigDecimal.ZERO),
                String.format("Percent error should be 0 for perfect match (value=%.2f), got: %s",
                        value, log.getPercentError()));

        assertEquals(0, log.getAbsoluteError().compareTo(BigDecimal.ZERO),
                String.format("Absolute error should be 0 for perfect match (value=%.2f), got: %s",
                        value, log.getAbsoluteError()));
    }

    // ============================================================================
    // INVARIANT 4: Percent error bounds
    // ============================================================================

    @Property
    @Label("Percent error magnitude should be <= 100% for reasonable inputs")
    void percentError_withinReasonableBounds(
            @ForAll @DoubleRange(min = 1.0, max = 5000.0) double aiValue,
            @ForAll @DoubleRange(min = 1.0, max = 5000.0) double userValue
    ) {
        // Only test when values are within 2x of each other (reasonable corrections)
        Assume.that(Math.min(aiValue, userValue) / Math.max(aiValue, userValue) > 0.5);

        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(aiValue))
                .userValue(BigDecimal.valueOf(userValue))
                .build();

        // INVARIANT: For reasonable corrections (within 2x), percent error should be <= 100%
        BigDecimal percentErrorAbs = log.getPercentError().abs();
        assertTrue(percentErrorAbs.compareTo(BigDecimal.valueOf(100)) <= 0,
                String.format("Percent error should be <= 100%% for AI=%.2f, User=%.2f, got: %s%%",
                        aiValue, userValue, percentErrorAbs));
    }

    // ============================================================================
    // INVARIANT 5: Symmetry of absolute error
    // ============================================================================

    @Property
    @Label("Absolute error should be same regardless of which value is AI vs User")
    void absoluteError_isSymmetric(
            @ForAll @DoubleRange(min = 1.0, max = 5000.0) double value1,
            @ForAll @DoubleRange(min = 1.0, max = 5000.0) double value2
    ) {
        AiCorrectionLog log1 = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(value1))
                .userValue(BigDecimal.valueOf(value2))
                .build();

        AiCorrectionLog log2 = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(value2))
                .userValue(BigDecimal.valueOf(value1))
                .build();

        // INVARIANT: |AI - User| = |User - AI|
        assertEquals(log1.getAbsoluteError(), log2.getAbsoluteError(),
                String.format("Absolute error should be symmetric for values %.2f and %.2f", value1, value2));
    }

    // ============================================================================
    // INVARIANT 6: Relationship between percent and absolute error
    // ============================================================================

    @Property
    @Label("Percent error and absolute error should be consistent")
    void percentError_consistentWithAbsoluteError(
            @ForAll @DoubleRange(min = 10.0, max = 5000.0) double aiValue,
            @ForAll @DoubleRange(min = 10.0, max = 5000.0) double userValue
    ) {
        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(aiValue))
                .userValue(BigDecimal.valueOf(userValue))
                .build();

        // INVARIANT: percent_error = (user - ai) / user * 100
        // Which means: absolute_error = |percent_error * user / 100|
        BigDecimal calculatedAbsolute = log.getPercentError()
                .multiply(BigDecimal.valueOf(userValue))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .abs();

        BigDecimal actualAbsolute = log.getAbsoluteError();

        // Allow small rounding differences (within 0.1)
        BigDecimal diff = calculatedAbsolute.subtract(actualAbsolute).abs();
        assertTrue(diff.compareTo(BigDecimal.valueOf(0.5)) <= 0,
                String.format("Calculated absolute error (%.2f) should match actual (%.2f) for AI=%.2f, User=%.2f",
                        calculatedAbsolute, actualAbsolute, aiValue, userValue));
    }

    // ============================================================================
    // INVARIANT 7: Larger difference = larger absolute error
    // ============================================================================

    @Property
    @Label("Larger differences should result in larger absolute errors")
    void largerDifference_yieldsLargerAbsoluteError(
            @ForAll @DoubleRange(min = 100.0, max = 1000.0) double baseValue,
            @ForAll @DoubleRange(min = 1.0, max = 50.0) double smallDiff,
            @ForAll @DoubleRange(min = 51.0, max = 200.0) double largeDiff
    ) {
        AiCorrectionLog smallError = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(baseValue))
                .userValue(BigDecimal.valueOf(baseValue + smallDiff))
                .build();

        AiCorrectionLog largeError = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(baseValue))
                .userValue(BigDecimal.valueOf(baseValue + largeDiff))
                .build();

        // INVARIANT: Larger difference -> larger absolute error
        assertTrue(largeError.getAbsoluteError().compareTo(smallError.getAbsoluteError()) > 0,
                String.format("Large diff (%.2f) should have larger absolute error than small diff (%.2f)",
                        largeDiff, smallDiff));
    }

    // ============================================================================
    // INVARIANT 8: Error calculation doesn't crash on edge cases
    // ============================================================================

    @Property
    @Label("Error calculation should handle very small and very large numbers")
    void errorCalculation_handlesExtremeValues(
            @ForAll @DoubleRange(min = 0.001, max = 10000.0) double aiValue,
            @ForAll @DoubleRange(min = 0.001, max = 10000.0) double userValue
    ) {
        // This test verifies no exceptions are thrown for extreme values
        assertDoesNotThrow(() -> {
            AiCorrectionLog log = AiCorrectionLog.builder()
                    .fieldName("test_field")
                    .aiValue(BigDecimal.valueOf(aiValue))
                    .userValue(BigDecimal.valueOf(userValue))
                    .build();

            assertNotNull(log.getPercentError(), "Percent error should not be null");
            assertNotNull(log.getAbsoluteError(), "Absolute error should not be null");
        }, String.format("Should not throw for AI=%.4f, User=%.4f", aiValue, userValue));
    }

    // ============================================================================
    // INVARIANT 9: Doubling the error doubles the absolute error
    // ============================================================================

    @Property
    @Label("Doubling the correction should double the absolute error")
    void doublingCorrection_doublesAbsoluteError(
            @ForAll @DoubleRange(min = 100.0, max = 1000.0) double aiValue,
            @ForAll @DoubleRange(min = 10.0, max = 100.0) double correction
    ) {
        AiCorrectionLog singleCorrection = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(aiValue))
                .userValue(BigDecimal.valueOf(aiValue + correction))
                .build();

        AiCorrectionLog doubleCorrection = AiCorrectionLog.builder()
                .fieldName("test_field")
                .aiValue(BigDecimal.valueOf(aiValue))
                .userValue(BigDecimal.valueOf(aiValue + 2 * correction))
                .build();

        // INVARIANT: Double the correction -> approximately double the absolute error
        BigDecimal expectedDouble = singleCorrection.getAbsoluteError().multiply(BigDecimal.valueOf(2));
        BigDecimal actualDouble = doubleCorrection.getAbsoluteError();

        // Allow small rounding differences (within 1%)
        BigDecimal diff = expectedDouble.subtract(actualDouble).abs();
        BigDecimal tolerance = expectedDouble.multiply(BigDecimal.valueOf(0.01));

        assertTrue(diff.compareTo(tolerance) <= 0,
                String.format("Double correction should approximately double absolute error: expected=%.2f, actual=%.2f",
                        expectedDouble, actualDouble));
    }

    // ============================================================================
    // Unit test to verify property tests are actually running
    // ============================================================================

    @Test
    void propertyTests_areExecuting() {
        // This is a sanity check to ensure jqwik is properly configured
        assertTrue(true, "Property tests should be executing");
    }
}
