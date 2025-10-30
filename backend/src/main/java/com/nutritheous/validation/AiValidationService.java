package com.nutritheous.validation;

import com.nutritheous.common.dto.AnalysisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for validating AI-generated nutrition data to catch hallucinations and errors
 * before saving to database.
 *
 * Performs 6 types of sanity checks:
 * 1. Energy balance (Atwater factors)
 * 2. Impossible ratios (protein cals > total cals)
 * 3. Fiber > carbs
 * 4. Sugar > carbs
 * 5. Saturated fat > total fat
 * 6. Outlier detection (very high values)
 */
@Service
public class AiValidationService {

    private static final Logger logger = LoggerFactory.getLogger(AiValidationService.class);

    // Atwater factors for energy calculation
    private static final double PROTEIN_CAL_PER_G = 4.0;
    private static final double FAT_CAL_PER_G = 9.0;
    private static final double CARB_CAL_PER_G = 4.0;

    // Validation thresholds
    private static final double ENERGY_MISMATCH_THRESHOLD_PERCENT = 20.0;
    private static final int HIGH_CALORIE_THRESHOLD = 2500;
    private static final double HIGH_SODIUM_THRESHOLD = 3000.0;
    private static final double HIGH_FIBER_THRESHOLD = 30.0;
    private static final double HIGH_PROTEIN_THRESHOLD = 150.0;

    /**
     * Validate AI-generated nutrition data
     *
     * @param response AI analysis response to validate
     * @return ValidationResult with list of issues found
     */
    public ValidationResult validate(AnalysisResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Check 1: Energy balance (Atwater factors)
        issues.addAll(checkEnergyBalance(response));

        // Check 2: Impossible ratios
        issues.addAll(checkImpossibleRatios(response));

        // Check 3: Fiber > Carbs
        issues.addAll(checkFiberVsCarbs(response));

        // Check 4: Sugar > Carbs
        issues.addAll(checkSugarVsCarbs(response));

        // Check 5: Saturated fat > Total fat
        issues.addAll(checkSaturatedFatVsTotalFat(response));

        // Check 6: Outlier detection
        issues.addAll(checkOutliers(response));

        boolean valid = issues.stream().noneMatch(i -> i.getSeverity() == ValidationIssue.Severity.ERROR);

        ValidationResult result = ValidationResult.builder()
                .valid(valid)
                .issues(issues)
                .build();

        if (!valid) {
            logger.warn("AI validation failed with {} errors and {} warnings",
                    result.getErrors().size(), result.getWarnings().size());
        } else if (result.hasWarnings()) {
            logger.info("AI validation passed with {} warnings", result.getWarnings().size());
        }

        return result;
    }

    /**
     * Check 1: Energy balance using Atwater factors
     * Protein (4 cal/g) + Fat (9 cal/g) + Carbs (4 cal/g) should approximately equal total calories
     */
    private List<ValidationIssue> checkEnergyBalance(AnalysisResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();

        Integer calories = response.getCalories();
        Double proteinG = response.getProteinG();
        Double fatG = response.getFatG();
        Double carbohydratesG = response.getCarbohydratesG();

        // Only check if all values are present
        if (calories != null && proteinG != null && fatG != null && carbohydratesG != null) {
            double calculatedCalories = (proteinG * PROTEIN_CAL_PER_G) +
                                       (fatG * FAT_CAL_PER_G) +
                                       (carbohydratesG * CARB_CAL_PER_G);

            double percentDiff = Math.abs(calories - calculatedCalories) / calories * 100.0;

            if (percentDiff > ENERGY_MISMATCH_THRESHOLD_PERCENT) {
                issues.add(ValidationIssue.builder()
                        .severity(ValidationIssue.Severity.WARNING)
                        .field("calories")
                        .message(String.format(
                                "Energy mismatch: Claimed %d cal but macros calculate to %.0f cal (%.1f%% difference)",
                                calories, calculatedCalories, percentDiff))
                        .actualValue(calories.doubleValue())
                        .suggestedFix(calculatedCalories)
                        .build());
            }
        }

        return issues;
    }

    /**
     * Check 2: Impossible ratios - macronutrient calories cannot exceed total calories
     */
    private List<ValidationIssue> checkImpossibleRatios(AnalysisResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();

        Integer calories = response.getCalories();
        if (calories == null) {
            return issues;
        }

        // Protein calories can't exceed total calories
        if (response.getProteinG() != null) {
            double proteinCalories = response.getProteinG() * PROTEIN_CAL_PER_G;
            if (proteinCalories > calories * 1.1) { // 10% tolerance
                issues.add(ValidationIssue.error(
                        "protein_g",
                        String.format("Protein alone provides %.0f cal, which exceeds total calories (%d cal)",
                                proteinCalories, calories)));
            }
        }

        // Fat calories can't exceed total calories
        if (response.getFatG() != null) {
            double fatCalories = response.getFatG() * FAT_CAL_PER_G;
            if (fatCalories > calories * 1.1) { // 10% tolerance
                issues.add(ValidationIssue.error(
                        "fat_g",
                        String.format("Fat alone provides %.0f cal, which exceeds total calories (%d cal)",
                                fatCalories, calories)));
            }
        }

        // Carb calories can't exceed total calories
        if (response.getCarbohydratesG() != null) {
            double carbCalories = response.getCarbohydratesG() * CARB_CAL_PER_G;
            if (carbCalories > calories * 1.1) { // 10% tolerance
                issues.add(ValidationIssue.error(
                        "carbohydrates_g",
                        String.format("Carbs alone provide %.0f cal, which exceeds total calories (%d cal)",
                                carbCalories, calories)));
            }
        }

        return issues;
    }

    /**
     * Check 3: Fiber cannot exceed total carbohydrates (fiber is a subset of carbs)
     */
    private List<ValidationIssue> checkFiberVsCarbs(AnalysisResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();

        Double fiberG = response.getFiberG();
        Double carbohydratesG = response.getCarbohydratesG();

        if (fiberG != null && carbohydratesG != null && fiberG > carbohydratesG) {
            issues.add(ValidationIssue.error(
                    "fiber_g",
                    String.format("Fiber (%.1fg) cannot exceed total carbohydrates (%.1fg)",
                            fiberG, carbohydratesG)));
        }

        return issues;
    }

    /**
     * Check 4: Sugar cannot exceed total carbohydrates (sugar is a subset of carbs)
     */
    private List<ValidationIssue> checkSugarVsCarbs(AnalysisResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();

        Double sugarG = response.getSugarG();
        Double carbohydratesG = response.getCarbohydratesG();

        if (sugarG != null && carbohydratesG != null && sugarG > carbohydratesG) {
            issues.add(ValidationIssue.error(
                    "sugar_g",
                    String.format("Sugar (%.1fg) cannot exceed total carbohydrates (%.1fg)",
                            sugarG, carbohydratesG)));
        }

        return issues;
    }

    /**
     * Check 5: Saturated fat cannot exceed total fat (saturated fat is a subset of total fat)
     */
    private List<ValidationIssue> checkSaturatedFatVsTotalFat(AnalysisResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();

        Double saturatedFatG = response.getSaturatedFatG();
        Double fatG = response.getFatG();

        if (saturatedFatG != null && fatG != null && saturatedFatG > fatG) {
            issues.add(ValidationIssue.error(
                    "saturated_fat_g",
                    String.format("Saturated fat (%.1fg) cannot exceed total fat (%.1fg)",
                            saturatedFatG, fatG)));
        }

        return issues;
    }

    /**
     * Check 6: Outlier detection - flag unusually high values that might be hallucinations
     */
    private List<ValidationIssue> checkOutliers(AnalysisResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Very high calorie count
        if (response.getCalories() != null && response.getCalories() > HIGH_CALORIE_THRESHOLD) {
            issues.add(ValidationIssue.warning(
                    "calories",
                    String.format("Very high calorie count (%d cal) - verify this is a large meal or multiple servings",
                            response.getCalories())));
        }

        // Very high sodium (likely restaurant/processed food)
        if (response.getSodiumMg() != null && response.getSodiumMg() > HIGH_SODIUM_THRESHOLD) {
            issues.add(ValidationIssue.warning(
                    "sodium_mg",
                    String.format("Very high sodium (%.0f mg) - typical of restaurant or heavily processed food",
                            response.getSodiumMg())));
        }

        // Very high fiber (unusual unless large salad/vegetable meal)
        if (response.getFiberG() != null && response.getFiberG() > HIGH_FIBER_THRESHOLD) {
            issues.add(ValidationIssue.warning(
                    "fiber_g",
                    String.format("Very high fiber (%.1fg) - verify this is a large vegetable-heavy meal",
                            response.getFiberG())));
        }

        // Very high protein (unusual unless pure protein meal)
        if (response.getProteinG() != null && response.getProteinG() > HIGH_PROTEIN_THRESHOLD) {
            issues.add(ValidationIssue.warning(
                    "protein_g",
                    String.format("Very high protein (%.1fg) - verify this is accurate",
                            response.getProteinG())));
        }

        return issues;
    }
}
