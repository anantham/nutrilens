package com.nutritheous.ingredient;

import com.nutritheous.auth.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for learning ingredient nutrition values from user corrections over time.
 * Uses Welford's online algorithm for numerically stable running averages.
 *
 * <p>Learning Flow:
 * 1. User corrects an ingredient's nutrition values
 * 2. System normalizes ingredient name and finds existing library entry
 * 3. If found, updates running averages using Welford's algorithm
 * 4. If not found, creates new library entry
 * 5. Calculates confidence score based on sample size and variability
 * 6. Updates typical quantity with weighted average
 *
 * <p>Mathematical Background:
 * Welford's algorithm computes running mean and variance with O(1) space complexity.
 * Formula: mean_new = mean_old + (x - mean_old) / n
 *          M2_new = M2_old + (x - mean_old) * (x - mean_new)
 *          variance = M2 / (n - 1)
 *
 * @see <a href="https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Welford's_online_algorithm">Welford's Algorithm</a>
 */
@Service
@Slf4j
public class IngredientLearningService {

    private final UserIngredientLibraryRepository libraryRepository;
    private final IngredientNormalizationService normalizationService;

    public IngredientLearningService(
            UserIngredientLibraryRepository libraryRepository,
            IngredientNormalizationService normalizationService
    ) {
        this.libraryRepository = libraryRepository;
        this.normalizationService = normalizationService;
    }

    /**
     * Learn from a user correction to an ingredient.
     *
     * When a user corrects AI-suggested ingredient values, this method:
     * 1. Normalizes the ingredient name
     * 2. Finds or creates library entry
     * 3. Updates running averages with Welford's algorithm
     * 4. Calculates new confidence score
     * 5. Updates typical quantity
     *
     * @param ingredient The corrected ingredient
     * @param user The user who made the correction
     */
    @Transactional
    public void learnFromCorrection(MealIngredient ingredient, User user) {
        if (ingredient == null || user == null) {
            log.warn("Cannot learn from null ingredient or user");
            return;
        }

        // Normalize ingredient name
        String normalizedName = normalizationService.normalize(ingredient.getIngredientName());
        if (normalizedName.isBlank()) {
            log.warn("Cannot learn from ingredient with blank name: {}", ingredient.getIngredientName());
            return;
        }

        // Convert nutrition to per-100g basis for fair comparison
        NutritionPer100g nutritionPer100g = calculateNutritionPer100g(ingredient);
        if (nutritionPer100g == null) {
            log.warn("Cannot calculate per-100g nutrition for ingredient: {}", ingredient.getIngredientName());
            return;
        }

        // Find existing library entry (using fuzzy matching)
        List<UserIngredientLibrary> userIngredients = libraryRepository.findByUserIdOrderByConfidenceScoreDesc(user.getId());
        Optional<UserIngredientLibrary> existingOpt = normalizationService.findBestMatch(
                normalizedName,
                userIngredients,
                2  // Max Levenshtein distance
        );

        UserIngredientLibrary libraryEntry;
        if (existingOpt.isPresent()) {
            // Update existing entry
            libraryEntry = existingOpt.get();
            updateExistingIngredient(libraryEntry, nutritionPer100g, ingredient);
            log.info("Updated existing ingredient '{}' for user {} (sample size: {})",
                    libraryEntry.getIngredientName(), user.getId(), libraryEntry.getSampleSize());
        } else {
            // Create new entry
            libraryEntry = createNewIngredient(user, ingredient, normalizedName, nutritionPer100g);
            log.info("Created new ingredient '{}' for user {} (first observation)",
                    libraryEntry.getIngredientName(), user.getId());
        }

        libraryRepository.save(libraryEntry);
    }

    /**
     * Create a new ingredient library entry from first observation.
     */
    private UserIngredientLibrary createNewIngredient(
            User user,
            MealIngredient ingredient,
            String normalizedName,
            NutritionPer100g nutritionPer100g
    ) {
        return UserIngredientLibrary.builder()
                .user(user)
                .ingredientName(ingredient.getIngredientName())
                .ingredientCategory(ingredient.getIngredientCategory())
                .normalizedName(normalizedName)
                .avgCaloriesPer100g(nutritionPer100g.getCalories())
                .avgProteinPer100g(nutritionPer100g.getProtein())
                .avgFatPer100g(nutritionPer100g.getFat())
                .avgCarbsPer100g(nutritionPer100g.getCarbs())
                .stdDevCalories(0.0)  // First observation - no variability yet
                .sampleSize(1)
                .confidenceScore(calculateConfidenceScore(1, 0.0))  // Low confidence with only 1 sample
                .typicalQuantity(ingredient.getQuantity())
                .typicalUnit(ingredient.getUnit())
                .lastUsed(LocalDateTime.now())
                .build();
    }

    /**
     * Update an existing ingredient with new observation using Welford's algorithm.
     */
    private void updateExistingIngredient(
            UserIngredientLibrary libraryEntry,
            NutritionPer100g nutritionPer100g,
            MealIngredient ingredient
    ) {
        int currentSampleSize = libraryEntry.getSampleSize();

        // Update calories with Welford's algorithm
        if (nutritionPer100g.getCalories() != null && libraryEntry.getAvgCaloriesPer100g() != null) {
            WelfordResult caloriesResult = welfordUpdate(
                    libraryEntry.getAvgCaloriesPer100g(),
                    libraryEntry.getStdDevCalories() != null ? libraryEntry.getStdDevCalories() : 0.0,
                    currentSampleSize,
                    nutritionPer100g.getCalories()
            );
            libraryEntry.setAvgCaloriesPer100g(caloriesResult.getMean());
            libraryEntry.setStdDevCalories(caloriesResult.getStdDev());
        }

        // Update protein (simple running average - variance not tracked)
        if (nutritionPer100g.getProtein() != null && libraryEntry.getAvgProteinPer100g() != null) {
            double newProtein = runningAverage(
                    libraryEntry.getAvgProteinPer100g(),
                    nutritionPer100g.getProtein(),
                    currentSampleSize
            );
            libraryEntry.setAvgProteinPer100g(newProtein);
        }

        // Update fat
        if (nutritionPer100g.getFat() != null && libraryEntry.getAvgFatPer100g() != null) {
            double newFat = runningAverage(
                    libraryEntry.getAvgFatPer100g(),
                    nutritionPer100g.getFat(),
                    currentSampleSize
            );
            libraryEntry.setAvgFatPer100g(newFat);
        }

        // Update carbs
        if (nutritionPer100g.getCarbs() != null && libraryEntry.getAvgCarbsPer100g() != null) {
            double newCarbs = runningAverage(
                    libraryEntry.getAvgCarbsPer100g(),
                    nutritionPer100g.getCarbs(),
                    currentSampleSize
            );
            libraryEntry.setAvgCarbsPer100g(newCarbs);
        }

        // Increment sample size
        libraryEntry.setSampleSize(currentSampleSize + 1);

        // Recalculate confidence score
        double newConfidence = calculateConfidenceScore(
                libraryEntry.getSampleSize(),
                libraryEntry.getStdDevCalories() != null ? libraryEntry.getStdDevCalories() : 0.0
        );
        libraryEntry.setConfidenceScore(newConfidence);

        // Update typical quantity (weighted average: 70% old, 30% new)
        if (ingredient.getQuantity() != null && libraryEntry.getTypicalQuantity() != null) {
            double newQuantity = updateTypicalQuantity(
                    libraryEntry.getTypicalQuantity(),
                    ingredient.getQuantity()
            );
            libraryEntry.setTypicalQuantity(newQuantity);
        }

        // Update typical unit (prefer more recent unit if changed)
        if (ingredient.getUnit() != null) {
            libraryEntry.setTypicalUnit(ingredient.getUnit());
        }

        // Update last used timestamp
        libraryEntry.setLastUsed(LocalDateTime.now());

        log.debug("Updated ingredient '{}': avgCal={}, stdDev={}, samples={}, confidence={}",
                libraryEntry.getIngredientName(),
                String.format("%.1f", libraryEntry.getAvgCaloriesPer100g()),
                String.format("%.1f", libraryEntry.getStdDevCalories()),
                libraryEntry.getSampleSize(),
                String.format("%.3f", libraryEntry.getConfidenceScore()));
    }

    /**
     * Welford's online algorithm for running mean and standard deviation.
     *
     * This algorithm is numerically stable and uses O(1) space complexity.
     * It updates mean and variance incrementally without storing all historical values.
     *
     * @param currentMean Current mean value
     * @param currentStdDev Current standard deviation
     * @param n Current sample size
     * @param newValue New observation to incorporate
     * @return Updated mean and standard deviation
     */
    private WelfordResult welfordUpdate(
            double currentMean,
            double currentStdDev,
            int n,
            double newValue
    ) {
        if (n < 1) {
            // First observation
            return new WelfordResult(newValue, 0.0);
        }

        // Current M2 (sum of squared differences from mean)
        double currentM2 = (n > 1) ? currentStdDev * currentStdDev * (n - 1) : 0.0;

        // Update mean
        double delta = newValue - currentMean;
        double newMean = currentMean + delta / (n + 1);

        // Update M2
        double delta2 = newValue - newMean;
        double newM2 = currentM2 + delta * delta2;

        // Calculate new standard deviation
        double newStdDev = (n > 0) ? Math.sqrt(newM2 / n) : 0.0;

        return new WelfordResult(newMean, newStdDev);
    }

    /**
     * Simple running average formula: mean_new = mean_old + (x - mean_old) / (n + 1)
     */
    private double runningAverage(double currentAvg, double newValue, int n) {
        if (n < 1) {
            return newValue;
        }
        return currentAvg + (newValue - currentAvg) / (n + 1);
    }

    /**
     * Calculate confidence score (0-1) based on sample size and variability.
     *
     * Formula: confidence = (1 - e^(-n/5)) * consistency_factor
     *
     * Sample size factor:
     * - Asymptotically approaches 1 as n → ∞
     * - Reaches ~0.63 at n=5, ~0.86 at n=10, ~0.95 at n=15
     *
     * Consistency factor (based on standard deviation):
     * - stdDev < 5:  1.0 (very consistent)
     * - stdDev < 10: 0.9 (consistent)
     * - stdDev < 20: 0.7 (moderate variability)
     * - stdDev < 30: 0.5 (high variability)
     * - stdDev ≥ 30: 0.3 (very high variability)
     *
     * @param sampleSize Number of observations
     * @param stdDev Standard deviation of calories per 100g
     * @return Confidence score between 0 and 1
     */
    private double calculateConfidenceScore(int sampleSize, double stdDev) {
        if (sampleSize < 1) {
            return 0.0;
        }

        // Sample size factor: asymptotically approaches 1
        // Formula: 1 - e^(-n/5)
        double sampleFactor = 1.0 - Math.exp(-sampleSize / 5.0);

        // Consistency factor: lower variability = higher confidence
        double consistencyFactor;
        if (stdDev < 5.0) {
            consistencyFactor = 1.0;   // Very consistent (e.g., "white rice")
        } else if (stdDev < 10.0) {
            consistencyFactor = 0.9;   // Consistent
        } else if (stdDev < 20.0) {
            consistencyFactor = 0.7;   // Moderate variability
        } else if (stdDev < 30.0) {
            consistencyFactor = 0.5;   // High variability (e.g., "curry" - depends on recipe)
        } else {
            consistencyFactor = 0.3;   // Very high variability
        }

        double confidence = sampleFactor * consistencyFactor;

        log.debug("Confidence calculation: sampleSize={}, stdDev={}, sampleFactor={}, consistencyFactor={}, confidence={}",
                sampleSize, String.format("%.1f", stdDev),
                String.format("%.3f", sampleFactor), consistencyFactor, String.format("%.3f", confidence));

        return confidence;
    }

    /**
     * Update typical quantity using weighted average (70% old, 30% new).
     *
     * This gives more weight to historical data to avoid being swayed by outliers,
     * while still adapting to changes in user's typical portions over time.
     *
     * @param currentQuantity Current typical quantity
     * @param newQuantity New observed quantity
     * @return Updated typical quantity
     */
    private double updateTypicalQuantity(double currentQuantity, double newQuantity) {
        return 0.7 * currentQuantity + 0.3 * newQuantity;
    }

    /**
     * Convert ingredient nutrition to per-100g basis.
     *
     * This standardization allows fair comparison between different servings
     * of the same ingredient.
     *
     * Example:
     * - Input: 2 idli (100g total) with 156 calories
     * - Output: 156 calories per 100g
     *
     * @param ingredient Ingredient with absolute nutrition values
     * @return Nutrition values per 100g, or null if conversion not possible
     */
    private NutritionPer100g calculateNutritionPer100g(MealIngredient ingredient) {
        Double quantity = ingredient.getQuantity();
        String unit = ingredient.getUnit();

        if (quantity == null || quantity <= 0) {
            log.debug("Cannot convert to per-100g: missing or invalid quantity");
            return null;
        }

        // Estimate weight in grams based on unit
        Double weightInGrams = estimateWeightInGrams(quantity, unit);
        if (weightInGrams == null || weightInGrams <= 0) {
            log.debug("Cannot convert to per-100g: unable to estimate weight for unit '{}'", unit);
            return null;
        }

        // Calculate per-100g values
        double factor = 100.0 / weightInGrams;

        NutritionPer100g nutrition = new NutritionPer100g();
        nutrition.setCalories(ingredient.getCalories() != null ? ingredient.getCalories() * factor : null);
        nutrition.setProtein(ingredient.getProteinG() != null ? ingredient.getProteinG() * factor : null);
        nutrition.setFat(ingredient.getFatG() != null ? ingredient.getFatG() * factor : null);
        nutrition.setCarbs(ingredient.getCarbohydratesG() != null ? ingredient.getCarbohydratesG() * factor : null);

        log.debug("Converted {} {} to per-100g: {} cal/100g",
                String.format("%.1f", quantity), unit,
                nutrition.getCalories() != null ? String.format("%.1f", nutrition.getCalories()) : "?");

        return nutrition;
    }

    /**
     * Estimate weight in grams based on quantity and unit.
     *
     * Handles various units:
     * - Weight units: g, kg, oz, lb
     * - Volume units: ml, l, cup, tbsp, tsp (assumes water density)
     * - Count units: piece (uses heuristics)
     * - Serving: assumes 100g
     *
     * @param quantity Quantity value
     * @param unit Unit of measurement
     * @return Estimated weight in grams, or null if unknown unit
     */
    private Double estimateWeightInGrams(Double quantity, String unit) {
        if (quantity == null || unit == null) {
            return null;
        }

        String normalizedUnit = unit.toLowerCase().trim();

        // Weight units
        switch (normalizedUnit) {
            case "g":
            case "gram":
            case "grams":
                return quantity;
            case "kg":
            case "kilogram":
            case "kilograms":
                return quantity * 1000.0;
            case "oz":
            case "ounce":
            case "ounces":
                return quantity * 28.35;
            case "lb":
            case "pound":
            case "pounds":
                return quantity * 453.59;
        }

        // Volume units (assume water density: 1ml = 1g)
        switch (normalizedUnit) {
            case "ml":
            case "milliliter":
            case "milliliters":
                return quantity;
            case "l":
            case "liter":
            case "liters":
                return quantity * 1000.0;
            case "cup":
            case "cups":
                return quantity * 240.0;
            case "tbsp":
            case "tablespoon":
            case "tablespoons":
                return quantity * 15.0;
            case "tsp":
            case "teaspoon":
            case "teaspoons":
                return quantity * 5.0;
        }

        // Count units (heuristics - not perfect)
        switch (normalizedUnit) {
            case "piece":
            case "pieces":
            case "item":
            case "items":
                // Heuristic: assume 50g per piece (e.g., 1 idli ≈ 50g, 1 dosa ≈ 50-80g)
                return quantity * 50.0;
            case "slice":
            case "slices":
                return quantity * 30.0;  // Average slice (bread, tomato, etc.)
            case "serving":
            case "servings":
                return quantity * 100.0;  // Assume 100g per serving
        }

        // Unknown unit
        log.debug("Unknown unit for weight estimation: '{}'", unit);
        return null;
    }

    /**
     * Result of Welford's algorithm update.
     */
    @Data
    @AllArgsConstructor
    private static class WelfordResult {
        private double mean;
        private double stdDev;
    }

    /**
     * Nutrition values per 100g (for standardized comparison).
     */
    @Data
    private static class NutritionPer100g {
        private Double calories;
        private Double protein;
        private Double fat;
        private Double carbs;
    }
}
