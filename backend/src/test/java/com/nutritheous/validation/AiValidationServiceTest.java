package com.nutritheous.validation;

import com.nutritheous.common.dto.AnalysisResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for AI validation service.
 * Tests all 6 validation checks to ensure we catch AI hallucinations.
 */
class AiValidationServiceTest {

    private AiValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new AiValidationService();
    }

    // ============================================================================
    // Test 1: Energy Balance (Atwater Factors)
    // ============================================================================

    @Test
    void testEnergyBalance_ValidMeal_NoIssues() {
        // 50g protein (200 cal) + 30g fat (270 cal) + 60g carbs (240 cal) = 710 cal
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(710)
                .proteinG(50.0)
                .fatG(30.0)
                .carbohydratesG(60.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getIssues().size());
    }

    @Test
    void testEnergyBalance_MinorMismatch_NoWarning() {
        // 50g protein (200) + 30g fat (270) + 60g carbs (240) = 710 cal
        // Claimed 730 cal = 2.8% difference (under 20% threshold)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(730)
                .proteinG(50.0)
                .fatG(30.0)
                .carbohydratesG(60.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getIssues().size());
    }

    @Test
    void testEnergyBalance_MajorMismatch_Warning() {
        // 50g protein (200) + 30g fat (270) + 60g carbs (240) = 710 cal
        // Claimed 1000 cal = 40.8% difference (exceeds 20% threshold)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(1000)
                .proteinG(50.0)
                .fatG(30.0)
                .carbohydratesG(60.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());  // Warnings don't make it invalid
        assertEquals(1, result.getWarnings().size());
        assertEquals("calories", result.getWarnings().get(0).getField());
        assertTrue(result.getWarnings().get(0).getMessage().contains("Energy mismatch"));
    }

    @Test
    void testEnergyBalance_NullValues_NoCheck() {
        // Missing macros - can't validate energy balance
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(500)
                .proteinG(null)
                .fatG(20.0)
                .carbohydratesG(50.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getIssues().size());
    }

    // ============================================================================
    // Test 2: Impossible Ratios
    // ============================================================================

    @Test
    void testImpossibleRatios_ProteinExceedsTotal_Error() {
        // 300g protein = 1200 cal, but total is only 800 cal (impossible!)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(800)
                .proteinG(300.0)
                .fatG(10.0)
                .carbohydratesG(20.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("protein_g", result.getErrors().get(0).getField());
        assertTrue(result.getErrors().get(0).getMessage().contains("exceeds total calories"));
    }

    @Test
    void testImpossibleRatios_FatExceedsTotal_Error() {
        // 200g fat = 1800 cal, but total is only 800 cal (impossible!)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(800)
                .proteinG(20.0)
                .fatG(200.0)
                .carbohydratesG(30.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("fat_g", result.getErrors().get(0).getField());
    }

    @Test
    void testImpossibleRatios_CarbsExceedsTotal_Error() {
        // 400g carbs = 1600 cal, but total is only 800 cal (impossible!)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(800)
                .proteinG(20.0)
                .fatG(10.0)
                .carbohydratesG(400.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("carbohydrates_g", result.getErrors().get(0).getField());
    }

    @Test
    void testImpossibleRatios_WithinTolerance_NoError() {
        // 90g protein = 360 cal, total = 400 cal (90% = within 10% tolerance)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(400)
                .proteinG(90.0)
                .fatG(5.0)
                .carbohydratesG(5.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getErrors().size());
    }

    // ============================================================================
    // Test 3: Fiber vs Carbs
    // ============================================================================

    @Test
    void testFiberVsCarbs_FiberExceedsCarbs_Error() {
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(500)
                .fiberG(50.0)
                .carbohydratesG(30.0)  // Fiber can't be more than carbs!
                .build();

        ValidationResult result = validationService.validate(response);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("fiber_g", result.getErrors().get(0).getField());
        assertTrue(result.getErrors().get(0).getMessage().contains("cannot exceed total carbohydrates"));
    }

    @Test
    void testFiberVsCarbs_FiberEqualsCarbs_Valid() {
        // Edge case: pure fiber (like psyllium husk supplement)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(100)
                .fiberG(25.0)
                .carbohydratesG(25.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
    }

    @Test
    void testFiberVsCarbs_NormalRatio_Valid() {
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(500)
                .fiberG(12.0)
                .carbohydratesG(60.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getIssues().size());
    }

    // ============================================================================
    // Test 4: Sugar vs Carbs
    // ============================================================================

    @Test
    void testSugarVsCarbs_SugarExceedsCarbs_Error() {
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(500)
                .sugarG(80.0)
                .carbohydratesG(60.0)  // Sugar can't be more than carbs!
                .build();

        ValidationResult result = validationService.validate(response);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("sugar_g", result.getErrors().get(0).getField());
        assertTrue(result.getErrors().get(0).getMessage().contains("cannot exceed total carbohydrates"));
    }

    @Test
    void testSugarVsCarbs_SugarEqualsCarbs_Valid() {
        // Edge case: pure sugar (like honey or syrup)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(300)
                .sugarG(75.0)
                .carbohydratesG(75.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
    }

    @Test
    void testSugarVsCarbs_NormalRatio_Valid() {
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(500)
                .sugarG(15.0)
                .carbohydratesG(60.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getIssues().size());
    }

    // ============================================================================
    // Test 5: Saturated Fat vs Total Fat
    // ============================================================================

    @Test
    void testSaturatedFatVsTotalFat_ExceedsTotal_Error() {
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(500)
                .saturatedFatG(40.0)
                .fatG(30.0)  // Saturated fat can't be more than total fat!
                .build();

        ValidationResult result = validationService.validate(response);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("saturated_fat_g", result.getErrors().get(0).getField());
        assertTrue(result.getErrors().get(0).getMessage().contains("cannot exceed total fat"));
    }

    @Test
    void testSaturatedFatVsTotalFat_EqualsTotal_Valid() {
        // Edge case: pure saturated fat (like coconut oil)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(500)
                .saturatedFatG(30.0)
                .fatG(30.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
    }

    @Test
    void testSaturatedFatVsTotalFat_NormalRatio_Valid() {
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(500)
                .saturatedFatG(8.0)
                .fatG(25.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getIssues().size());
    }

    // ============================================================================
    // Test 6: Outlier Detection
    // ============================================================================

    @Test
    void testOutliers_VeryHighCalories_Warning() {
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(3000)  // > 2500 threshold
                .proteinG(100.0)
                .fatG(100.0)
                .carbohydratesG(300.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());  // Still valid, just a warning
        assertEquals(1, result.getWarnings().size());
        assertEquals("calories", result.getWarnings().get(0).getField());
        assertTrue(result.getWarnings().get(0).getMessage().contains("Very high calorie count"));
    }

    @Test
    void testOutliers_VeryHighSodium_Warning() {
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(800)
                .sodiumMg(4000.0)  // > 3000 threshold
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(1, result.getWarnings().size());
        assertEquals("sodium_mg", result.getWarnings().get(0).getField());
        assertTrue(result.getWarnings().get(0).getMessage().contains("Very high sodium"));
    }

    @Test
    void testOutliers_VeryHighFiber_Warning() {
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(600)
                .fiberG(40.0)  // > 30 threshold
                .carbohydratesG(100.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(1, result.getWarnings().size());
        assertEquals("fiber_g", result.getWarnings().get(0).getField());
        assertTrue(result.getWarnings().get(0).getMessage().contains("Very high fiber"));
    }

    @Test
    void testOutliers_VeryHighProtein_Warning() {
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(800)
                .proteinG(180.0)  // > 150 threshold
                .fatG(10.0)
                .carbohydratesG(20.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(1, result.getWarnings().size());
        assertEquals("protein_g", result.getWarnings().get(0).getField());
        assertTrue(result.getWarnings().get(0).getMessage().contains("Very high protein"));
    }

    @Test
    void testOutliers_MultipleWarnings_AllReported() {
        // High calories AND high sodium AND high protein AND energy mismatch
        // 200*4 + 50*9 + 300*4 = 2450 cal, but claimed 3500 cal (42.8% diff)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(3500)
                .proteinG(200.0)
                .fatG(50.0)
                .carbohydratesG(300.0)
                .sodiumMg(5000.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());  // Warnings don't invalidate
        assertEquals(4, result.getWarnings().size());  // 4 warnings: high cal, high protein, high sodium, energy mismatch

        List<String> warningFields = result.getWarnings().stream()
                .map(ValidationIssue::getField)
                .toList();

        assertTrue(warningFields.contains("calories"));
        assertTrue(warningFields.contains("protein_g"));
        assertTrue(warningFields.contains("sodium_mg"));
        // Note: calories field appears twice (outlier + energy mismatch)
        assertEquals(2, warningFields.stream().filter(f -> f.equals("calories")).count());
    }

    // ============================================================================
    // Complex Test Cases
    // ============================================================================

    @Test
    void testMultipleErrors_AllReported() {
        // Fiber > carbs AND sugar > carbs AND saturated fat > total fat
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(500)
                .fiberG(100.0)
                .sugarG(80.0)
                .carbohydratesG(60.0)
                .saturatedFatG(50.0)
                .fatG(30.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertFalse(result.isValid());
        assertEquals(3, result.getErrors().size());
    }

    @Test
    void testMixedErrorsAndWarnings() {
        // ERROR: fiber > carbs
        // WARNINGS: high calories, high fiber outlier, energy mismatch (100*4 + 150*9 + 50*4 = 1950 vs 3000 = 35% diff)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(3000)
                .fiberG(100.0)
                .carbohydratesG(50.0)
                .proteinG(100.0)
                .fatG(150.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertFalse(result.isValid());  // Errors make it invalid
        assertEquals(1, result.getErrors().size());
        assertEquals(3, result.getWarnings().size());  // high calories + high fiber + energy mismatch
        assertTrue(result.hasErrors());
        assertTrue(result.hasWarnings());
    }

    @Test
    void testCompletelyValid_NoIssues() {
        // Realistic meal: chicken breast with rice and vegetables
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(450)
                .proteinG(35.0)
                .fatG(8.0)
                .carbohydratesG(50.0)
                .fiberG(6.0)
                .sugarG(3.0)
                .saturatedFatG(2.0)
                .sodiumMg(600.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getIssues().size());
        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    void testNullCalories_SkipsImpossibleRatioCheck() {
        // Can't check impossible ratios without calories
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(null)
                .proteinG(300.0)  // Would be error if calories were set
                .fatG(10.0)
                .carbohydratesG(20.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getErrors().size());
    }

    @Test
    void testZeroValues_NoErrors() {
        // Edge case: zero nutrients (water, coffee, tea)
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(0)
                .proteinG(0.0)
                .fatG(0.0)
                .carbohydratesG(0.0)
                .fiberG(0.0)
                .sugarG(0.0)
                .saturatedFatG(0.0)
                .sodiumMg(0.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getIssues().size());
    }

    @Test
    void testAtraterFactors_ExactMatch() {
        // Perfect Atwater calculation:
        // 25g protein (100 cal) + 10g fat (90 cal) + 30g carbs (120 cal) = 310 cal
        AnalysisResponse response = AnalysisResponse.builder()
                .calories(310)
                .proteinG(25.0)
                .fatG(10.0)
                .carbohydratesG(30.0)
                .build();

        ValidationResult result = validationService.validate(response);

        assertTrue(result.isValid());
        assertEquals(0, result.getIssues().size());
    }
}
