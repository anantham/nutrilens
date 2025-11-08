# Phase 3B & 3C: Ingredient Learning System - Implementation Plan

**Goal**: Build personal ingredient library from user corrections using statistical learning algorithms, then enable intelligent auto-fill predictions and recipe pattern detection.

**Timeline**: 4 weeks (2 sub-phases)

---

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Phase 3B: Learning from Corrections](#phase-3b-learning-from-corrections-week-3-4)
4. [Phase 3C: Predictions & Auto-fill](#phase-3c-predictions--auto-fill-week-5-6)
5. [Mathematical Foundations](#mathematical-foundations)
6. [Database Optimizations](#database-optimizations)
7. [Success Metrics](#success-metrics)
8. [Testing Strategy](#testing-strategy)

---

## Overview

### Recap: What We Built in Phase 3A

✅ **Database Schema**: 3 tables for ingredient tracking
✅ **AI Extraction**: Meals decomposed into individual ingredients
✅ **User Editing**: Users can correct quantities and nutrition
✅ **Foundation**: MealIngredient entities with `is_user_corrected` flag

### What's Missing (Phase 3B & 3C)

❌ **No Learning**: Corrections aren't saved to personal library
❌ **No Memory**: System doesn't remember user's typical portions
❌ **No Improvement**: AI estimates don't get better over time
❌ **No Auto-fill**: Users must manually enter ingredients every time
❌ **No Patterns**: System doesn't learn recipe combinations

### The Solution: Statistical Learning

```
USER CORRECTION FLOW:
┌─────────────────────────────────────────────────────────────┐
│ User eats idli meal → AI estimates ingredients             │
│ - Idli: 2 pieces, 78 cal                                   │
│ - Sambar: 150g, 85 cal                                     │
│ - Chutney: 30g, 68 cal (AI estimate)                       │
└─────────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────────┐
│ User corrects: "Chutney was 50g, not 30g"                  │
│ System marks: is_user_corrected = true                     │
└─────────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────────┐
│ PHASE 3B: IngredientLearningService                        │
│ - Normalize name: "coconut chutney" → "chutney"            │
│ - Lookup in user_ingredient_library                        │
│ - Calculate per-100g nutrition from correction             │
│ - Update running average (Welford's algorithm)             │
│ - Update std deviation, sample size, confidence            │
│ - Store typical quantity (50g for chutney)                 │
└─────────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────────┐
│ USER'S PERSONAL LIBRARY:                                    │
│ Chutney (coconut):                                          │
│ - Avg: 136 cal per 100g (learned from 5 corrections)       │
│ - Typical quantity: 50g                                     │
│ - Confidence: 0.85 (high - consistent corrections)         │
│ - Sample size: 5                                            │
│ - Std dev: 8.2 cal (low variability = trustworthy)         │
└─────────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────────┐
│ PHASE 3C: Next time user eats idli...                      │
│ - AI sees "chutney" in image                               │
│ - System: "Found in your library! Auto-fill?"             │
│ - Suggests: 50g, 68 cal (from your history)               │
│ - User: Accepts or adjusts                                 │
│ - Accuracy improves: 95% vs 70% for new foods              │
└─────────────────────────────────────────────────────────────┘
```

---

## Architecture

### System Overview

```
┌────────────────────────────────────────────────────────────────┐
│                     User Interface (Flutter)                    │
├────────────────────────────────────────────────────────────────┤
│  • Ingredient editing (existing)                               │
│  • Auto-fill suggestions (NEW - Phase 3C)                     │
│  • "Use my typical portion" button (NEW)                      │
│  • Learning progress dashboard (NEW)                          │
└────────────────────────────────────────────────────────────────┘
                              ↓ HTTP
┌────────────────────────────────────────────────────────────────┐
│                    Spring Boot Backend                          │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  IngredientController (existing)                          │ │
│  │  + NEW: GET /api/ingredients/suggestions/{name}          │ │
│  │  + NEW: GET /api/ingredients/library/stats               │ │
│  └──────────────────────────────────────────────────────────┘ │
│                              ↓                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  IngredientExtractionService (existing)                   │ │
│  │  - updateIngredient() ← triggers learning                │ │
│  └──────────────────────────────────────────────────────────┘ │
│                              ↓                                  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  PHASE 3B: IngredientLearningService                    │  │
│  │  - learnFromCorrection()                                 │  │
│  │  - updateRunningAverage() (Welford's algorithm)         │  │
│  │  - calculateConfidence()                                 │  │
│  │  - normalizeIngredientName()                             │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              ↓                                  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  IngredientNormalizationService                          │  │
│  │  - fuzzyMatch("idly" → "idli")                          │  │
│  │  - handleTypos(), removePluralS()                       │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              ↓                                  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  PHASE 3C: IngredientPredictionService                  │  │
│  │  - predictQuantity()                                     │  │
│  │  - suggestFromLibrary()                                  │  │
│  │  - getConfidenceScore()                                  │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              ↓                                  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  RecipePatternService                                    │  │
│  │  - detectPatterns() (idli → sambar + chutney)          │  │
│  │  - suggestMissingIngredients()                          │  │
│  │  - updateRecipePattern()                                 │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              ↓                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Repositories (existing from Phase 3A)                   │ │
│  │  - MealIngredientRepository                             │ │
│  │  - UserIngredientLibraryRepository                      │ │
│  │  - UserRecipePatternRepository                          │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database                          │
├────────────────────────────────────────────────────────────────┤
│  • user_ingredient_library (gets updated with corrections)    │
│  • user_recipe_patterns (learns common combinations)          │
│  • meal_ingredients (source of corrections)                   │
└────────────────────────────────────────────────────────────────┘
```

---

## Phase 3B: Learning from Corrections (Week 3-4)

### Goal
Automatically build personal ingredient library when users correct AI estimates, using statistical algorithms for accuracy and confidence scoring.

### Tasks

#### 1. IngredientLearningService (Core Logic)

**Purpose**: Update `user_ingredient_library` when user corrects an ingredient.

`IngredientLearningService.java`:
```java
@Service
@Slf4j
@Transactional
public class IngredientLearningService {

    private final UserIngredientLibraryRepository ingredientLibraryRepository;
    private final IngredientNormalizationService normalizationService;
    private final MealIngredientRepository mealIngredientRepository;

    public IngredientLearningService(
            UserIngredientLibraryRepository ingredientLibraryRepository,
            IngredientNormalizationService normalizationService,
            MealIngredientRepository mealIngredientRepository) {
        this.ingredientLibraryRepository = ingredientLibraryRepository;
        this.normalizationService = normalizationService;
        this.mealIngredientRepository = mealIngredientRepository;
    }

    /**
     * Learn from user correction.
     * Updates personal ingredient library with running averages.
     *
     * This is triggered when:
     * 1. User edits an existing AI-extracted ingredient
     * 2. User adds a completely new ingredient
     *
     * Algorithm: Welford's online algorithm for running mean and variance
     */
    public void learnFromCorrection(MealIngredient correctedIngredient, User user) {
        log.info("Learning from correction for ingredient: {} (user: {})",
                correctedIngredient.getIngredientName(), user.getId());

        // Step 1: Normalize ingredient name for matching
        String normalizedName = normalizationService.normalize(
                correctedIngredient.getIngredientName());

        log.debug("Normalized '{}' → '{}'",
                correctedIngredient.getIngredientName(), normalizedName);

        // Step 2: Convert nutrition to per-100g basis
        NutritionPer100g nutritionPer100g = calculateNutritionPer100g(correctedIngredient);

        if (nutritionPer100g == null) {
            log.warn("Cannot calculate per-100g nutrition for {}, skipping learning",
                    correctedIngredient.getIngredientName());
            return;
        }

        // Step 3: Find existing library entry or create new
        UserIngredientLibrary libraryEntry = ingredientLibraryRepository
                .findByUserIdAndNormalizedName(user.getId(), normalizedName)
                .orElse(null);

        if (libraryEntry == null) {
            // First time learning this ingredient
            libraryEntry = createNewLibraryEntry(
                    user, correctedIngredient, normalizedName, nutritionPer100g);
            log.info("Created new library entry for '{}'", normalizedName);
        } else {
            // Update existing entry with new data point
            updateExistingLibraryEntry(
                    libraryEntry, correctedIngredient, nutritionPer100g);
            log.info("Updated library entry for '{}' (sample size now: {})",
                    normalizedName, libraryEntry.getSampleSize());
        }

        // Step 4: Calculate confidence score
        double confidence = calculateConfidenceScore(
                libraryEntry.getSampleSize(),
                libraryEntry.getStdDevCalories());
        libraryEntry.setConfidenceScore(confidence);

        // Step 5: Update typical quantity (weighted average)
        updateTypicalQuantity(libraryEntry, correctedIngredient);

        // Step 6: Save to database
        libraryEntry.setLastUsed(LocalDateTime.now());
        ingredientLibraryRepository.save(libraryEntry);

        log.info("Learning complete for '{}': confidence={:.2f}, sample_size={}",
                normalizedName, confidence, libraryEntry.getSampleSize());
    }

    /**
     * Calculate nutrition per 100g from ingredient.
     */
    private NutritionPer100g calculateNutritionPer100g(MealIngredient ingredient) {
        if (ingredient.getQuantity() == null || ingredient.getQuantity() == 0) {
            return null;
        }

        double quantityInG = convertToGrams(ingredient.getQuantity(), ingredient.getUnit());
        if (quantityInG == 0) {
            return null;
        }

        return NutritionPer100g.builder()
                .caloriesPer100g(scaleToPercentG(ingredient.getCalories(), quantityInG))
                .proteinPer100g(scaleToPercentG(ingredient.getProteinG(), quantityInG))
                .fatPer100g(scaleToPercentG(ingredient.getFatG(), quantityInG))
                .carbsPer100g(scaleToPercentG(ingredient.getCarbohydratesG(), quantityInG))
                .fiberPer100g(scaleToPercentG(ingredient.getFiberG(), quantityInG))
                .sugarPer100g(scaleToPercentG(ingredient.getSugarG(), quantityInG))
                .sodiumPer100mg(scaleToPercentG(ingredient.getSodiumMg(), quantityInG))
                .build();
    }

    private double convertToGrams(double quantity, String unit) {
        // Convert various units to grams
        return switch (unit.toLowerCase()) {
            case "g" -> quantity;
            case "kg" -> quantity * 1000;
            case "ml" -> quantity; // Assume 1ml = 1g for simplicity
            case "l" -> quantity * 1000;
            case "oz" -> quantity * 28.35;
            case "lb" -> quantity * 453.592;
            case "cup" -> quantity * 240; // US cup
            case "tbsp" -> quantity * 15;
            case "tsp" -> quantity * 5;
            case "piece", "unit" -> {
                // For pieces, we can't convert without knowing piece weight
                // Try to infer from other corrections
                yield inferPieceWeight(quantity);
            }
            default -> {
                log.warn("Unknown unit: {}, assuming grams", unit);
                yield quantity;
            }
        };
    }

    private double inferPieceWeight(double quantity) {
        // Default piece weights for common items
        // This is a simplified approach - could be made smarter
        return quantity * 50.0; // Assume 50g per piece
    }

    private Double scaleToPercentG(Double value, double quantityInG) {
        if (value == null) return null;
        return (value / quantityInG) * 100.0;
    }

    /**
     * Create new library entry (first correction for this ingredient).
     */
    private UserIngredientLibrary createNewLibraryEntry(
            User user,
            MealIngredient ingredient,
            String normalizedName,
            NutritionPer100g nutritionPer100g) {

        return UserIngredientLibrary.builder()
                .user(user)
                .ingredientName(ingredient.getIngredientName())
                .ingredientCategory(ingredient.getIngredientCategory())
                .normalizedName(normalizedName)

                // Initial values (n=1)
                .avgCaloriesPer100g(nutritionPer100g.getCaloriesPer100g())
                .avgProteinPer100g(nutritionPer100g.getProteinPer100g())
                .avgFatPer100g(nutritionPer100g.getFatPer100g())
                .avgCarbsPer100g(nutritionPer100g.getCarbsPer100g())

                // Variability (undefined for n=1)
                .stdDevCalories(0.0)
                .sampleSize(1)

                // Typical usage
                .typicalQuantity(ingredient.getQuantity())
                .typicalUnit(ingredient.getUnit())

                .build();
    }

    /**
     * Update existing library entry with new data point.
     * Uses Welford's online algorithm for running mean and variance.
     */
    private void updateExistingLibraryEntry(
            UserIngredientLibrary entry,
            MealIngredient newData,
            NutritionPer100g newNutritionPer100g) {

        int n = entry.getSampleSize();
        int newN = n + 1;

        // WELFORD'S ALGORITHM for running average and variance
        // See: https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Welford's_online_algorithm

        // Update calories (with variance tracking)
        if (entry.getAvgCaloriesPer100g() != null && newNutritionPer100g.getCaloriesPer100g() != null) {
            WelfordResult result = welfordUpdate(
                    entry.getAvgCaloriesPer100g(),
                    entry.getStdDevCalories(),
                    n,
                    newNutritionPer100g.getCaloriesPer100g()
            );
            entry.setAvgCaloriesPer100g(result.newMean);
            entry.setStdDevCalories(result.newStdDev);
        }

        // Update protein (simple running average)
        if (entry.getAvgProteinPer100g() != null && newNutritionPer100g.getProteinPer100g() != null) {
            entry.setAvgProteinPer100g(
                    runningAverage(entry.getAvgProteinPer100g(), n, newNutritionPer100g.getProteinPer100g())
            );
        }

        // Update fat
        if (entry.getAvgFatPer100g() != null && newNutritionPer100g.getFatPer100g() != null) {
            entry.setAvgFatPer100g(
                    runningAverage(entry.getAvgFatPer100g(), n, newNutritionPer100g.getFatPer100g())
            );
        }

        // Update carbs
        if (entry.getAvgCarbsPer100g() != null && newNutritionPer100g.getCarbsPer100g() != null) {
            entry.setAvgCarbsPer100g(
                    runningAverage(entry.getAvgCarbsPer100g(), n, newNutritionPer100g.getCarbsPer100g())
            );
        }

        entry.setSampleSize(newN);
    }

    /**
     * Welford's online algorithm for running mean and standard deviation.
     *
     * Advantages:
     * - Single pass (don't need to store all values)
     * - Numerically stable
     * - O(1) space complexity
     *
     * Formula:
     * M_n = M_{n-1} + (x_n - M_{n-1}) / n
     * S_n = S_{n-1} + (x_n - M_{n-1}) * (x_n - M_n)
     * σ_n = sqrt(S_n / (n-1))
     */
    private WelfordResult welfordUpdate(
            double currentMean,
            double currentStdDev,
            int n,
            double newValue) {

        // Current sum of squared differences
        double currentM2 = (n > 1) ? currentStdDev * currentStdDev * (n - 1) : 0.0;

        // Update mean
        double delta = newValue - currentMean;
        double newMean = currentMean + delta / (n + 1);

        // Update M2 (sum of squared differences from mean)
        double delta2 = newValue - newMean;
        double newM2 = currentM2 + delta * delta2;

        // Calculate new standard deviation
        double newStdDev = (n > 0) ? Math.sqrt(newM2 / n) : 0.0;

        return new WelfordResult(newMean, newStdDev);
    }

    private record WelfordResult(double newMean, double newStdDev) {}

    /**
     * Simple running average formula.
     * new_avg = (old_avg * n + new_value) / (n + 1)
     */
    private double runningAverage(double currentAvg, int n, double newValue) {
        return (currentAvg * n + newValue) / (n + 1);
    }

    /**
     * Calculate confidence score (0-1) based on:
     * 1. Sample size (more corrections = higher confidence)
     * 2. Variability (lower std dev = higher confidence)
     *
     * Formula: confidence = (1 - e^(-n/5)) * (1 - min(σ/μ, 1))
     *
     * Where:
     * - n = sample size
     * - σ = standard deviation
     * - μ = mean
     *
     * Interpretation:
     * - 0.0-0.5: Low confidence (use with caution)
     * - 0.5-0.7: Medium confidence
     * - 0.7-0.9: High confidence (reliable)
     * - 0.9-1.0: Very high confidence (use for auto-fill)
     */
    private double calculateConfidenceScore(int sampleSize, double stdDev) {
        if (sampleSize < 1) return 0.0;

        // Component 1: Sample size factor
        // Asymptotically approaches 1 as n increases
        // e^(-n/5) decays quickly: n=1→0.82, n=5→0.37, n=10→0.13, n=20→0.02
        double sampleFactor = 1.0 - Math.exp(-sampleSize / 5.0);

        // Component 2: Consistency factor
        // Lower variability = higher confidence
        // For now, use simple threshold-based scoring
        double consistencyFactor;
        if (stdDev < 5.0) {
            consistencyFactor = 1.0;  // Very consistent
        } else if (stdDev < 10.0) {
            consistencyFactor = 0.9;  // Consistent
        } else if (stdDev < 20.0) {
            consistencyFactor = 0.7;  // Somewhat variable
        } else if (stdDev < 30.0) {
            consistencyFactor = 0.5;  // Variable
        } else {
            consistencyFactor = 0.3;  // Highly variable
        }

        // Combined confidence
        double confidence = sampleFactor * consistencyFactor;

        // Clamp to [0, 1]
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Update typical quantity with weighted average.
     * Recent corrections have more weight than old ones.
     */
    private void updateTypicalQuantity(UserIngredientLibrary entry, MealIngredient newData) {
        if (newData.getQuantity() == null) return;

        if (entry.getTypicalQuantity() == null) {
            // First time
            entry.setTypicalQuantity(newData.getQuantity());
            entry.setTypicalUnit(newData.getUnit());
        } else {
            // Weighted average: 70% old, 30% new
            // This gives more weight to recent usage patterns
            double newTypical = entry.getTypicalQuantity() * 0.7 + newData.getQuantity() * 0.3;
            entry.setTypicalQuantity(newTypical);

            // Update unit if changed (prefer most recent)
            if (!newData.getUnit().equals(entry.getTypicalUnit())) {
                entry.setTypicalUnit(newData.getUnit());
            }
        }
    }

    /**
     * Get user's learning statistics.
     */
    public IngredientLibraryStats getUserLibraryStats(UUID userId) {
        long totalIngredients = ingredientLibraryRepository.countByUserId(userId);
        Double avgConfidence = ingredientLibraryRepository.getAverageConfidence(userId);

        List<UserIngredientLibrary> highConfidenceIngredients =
                ingredientLibraryRepository.findHighConfidenceIngredients(userId, 0.7);

        return IngredientLibraryStats.builder()
                .totalIngredientsLearned(totalIngredients)
                .averageConfidence(avgConfidence != null ? avgConfidence : 0.0)
                .highConfidenceCount(highConfidenceIngredients.size())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class NutritionPer100g {
        private Double caloriesPer100g;
        private Double proteinPer100g;
        private Double fatPer100g;
        private Double carbsPer100g;
        private Double fiberPer100g;
        private Double sugarPer100g;
        private Double sodiumPer100mg;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientLibraryStats {
        private Long totalIngredientsLearned;
        private Double averageConfidence;
        private Integer highConfidenceCount;
    }
}
```

#### 2. IngredientNormalizationService

**Purpose**: Match ingredient name variants ("idly" = "idli", "chutny" = "chutney").

`IngredientNormalizationService.java`:
```java
@Service
@Slf4j
public class IngredientNormalizationService {

    // Common ingredient aliases
    private static final Map<String, String> ALIASES = Map.ofEntries(
            // South Indian
            Map.entry("idly", "idli"),
            Map.entry("dosai", "dosa"),
            Map.entry("dosay", "dosa"),
            Map.entry("sambaar", "sambar"),
            Map.entry("chutny", "chutney"),
            Map.entry("raita", "raitha"),

            // Common variations
            Map.entry("yogurt", "yoghurt"),
            Map.entry("curd", "yoghurt"),
            Map.entry("panir", "paneer"),
            Map.entry("dal", "daal"),
            Map.entry("dhal", "daal"),

            // Proteins
            Map.entry("chickpea", "chickpeas"),
            Map.entry("chana", "chickpeas"),
            Map.entry("rajma", "kidney beans"),

            // Grains
            Map.entry("roti", "chapati"),
            Map.entry("phulka", "chapati")
    );

    /**
     * Normalize ingredient name for matching.
     *
     * Steps:
     * 1. Lowercase
     * 2. Trim whitespace
     * 3. Remove special characters
     * 4. Handle plural forms
     * 5. Apply known aliases
     * 6. Fuzzy matching (Levenshtein distance)
     */
    public String normalize(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return "";
        }

        String normalized = ingredientName.toLowerCase().trim();

        // Remove special characters (keep only alphanumeric and spaces)
        normalized = normalized.replaceAll("[^a-z0-9\\s]", "");

        // Collapse multiple spaces
        normalized = normalized.replaceAll("\\s+", " ");

        // Apply known aliases
        if (ALIASES.containsKey(normalized)) {
            normalized = ALIASES.get(normalized);
            log.debug("Applied alias: {} → {}", ingredientName, normalized);
        }

        // Remove plural 's' if present (simple heuristic)
        if (normalized.endsWith("s") && normalized.length() > 3) {
            String singular = normalized.substring(0, normalized.length() - 1);
            if (!normalized.equals(singular)) {
                normalized = singular;
                log.debug("Removed plural: {}", normalized);
            }
        }

        return normalized;
    }

    /**
     * Find best match in user's library using fuzzy matching.
     *
     * Returns the library entry with the closest name match,
     * or null if no good match found (distance > threshold).
     */
    public UserIngredientLibrary findBestMatch(
            String ingredientName,
            List<UserIngredientLibrary> library,
            int maxDistance) {

        String normalizedQuery = normalize(ingredientName);
        int bestDistance = Integer.MAX_VALUE;
        UserIngredientLibrary bestMatch = null;

        for (UserIngredientLibrary entry : library) {
            String normalizedLibraryName = entry.getNormalizedName();
            int distance = levenshteinDistance(normalizedQuery, normalizedLibraryName);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = entry;
            }
        }

        // Only return match if distance is within threshold
        if (bestDistance <= maxDistance) {
            log.info("Found fuzzy match: '{}' → '{}' (distance: {})",
                    ingredientName, bestMatch.getIngredientName(), bestDistance);
            return bestMatch;
        }

        return null;
    }

    /**
     * Levenshtein distance (edit distance) between two strings.
     * Measures minimum number of single-character edits needed to transform one string to another.
     *
     * Example:
     * - levenshtein("idly", "idli") = 1 (remove 'y')
     * - levenshtein("chutny", "chutney") = 2 (add 'e', move 'y')
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(
                            dp[i - 1][j],      // deletion
                            Math.min(
                                    dp[i][j - 1],      // insertion
                                    dp[i - 1][j - 1]   // substitution
                            )
                    );
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }
}
```

#### 3. Integration: Trigger Learning on Correction

Modify `IngredientExtractionService.updateIngredient()` to trigger learning:

```java
// In IngredientExtractionService.java

@Autowired
private IngredientLearningService learningService;

@Transactional
public MealIngredient updateIngredient(
        UUID ingredientId,
        IngredientRequest request) {

    MealIngredient ingredient = mealIngredientRepository.findById(ingredientId)
            .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

    // Store original values for comparison
    boolean wasUserCorrected = Boolean.TRUE.equals(ingredient.getIsUserCorrected());

    // Update fields
    ingredient.setIngredientName(request.getIngredientName());
    ingredient.setIngredientCategory(request.getIngredientCategory());
    ingredient.setQuantity(request.getQuantity());
    ingredient.setUnit(request.getUnit());
    ingredient.setCalories(request.getCalories());
    ingredient.setProteinG(request.getProteinG());
    ingredient.setFatG(request.getFatG());
    ingredient.setSaturatedFatG(request.getSaturatedFatG());
    ingredient.setCarbohydratesG(request.getCarbohydratesG());
    ingredient.setFiberG(request.getFiberG());
    ingredient.setSugarG(request.getSugarG());
    ingredient.setSodiumMg(request.getSodiumMg());

    if (request.getDisplayOrder() != null) {
        ingredient.setDisplayOrder(request.getDisplayOrder());
    }

    // Mark as user corrected
    ingredient.setIsUserCorrected(true);

    MealIngredient saved = mealIngredientRepository.save(ingredient);

    // **NEW: Trigger learning if this is a correction**
    if (!wasUserCorrected) {
        // This is the first time user corrected this ingredient
        User user = ingredient.getMeal().getUser();
        learningService.learnFromCorrection(saved, user);
        log.info("Triggered learning for ingredient: {}", ingredient.getIngredientName());
    }

    return saved;
}
```

#### 4. REST API Endpoints

Add to `IngredientController.java`:

```java
@Autowired
private IngredientLearningService learningService;

/**
 * Get user's ingredient library statistics.
 */
@GetMapping("/ingredients/library/stats")
@Operation(summary = "Get library stats", description = "Statistics about user's learned ingredients")
public ResponseEntity<IngredientLibraryStatsResponse> getLibraryStats(
        Authentication authentication) {

    UUID userId = UUID.fromString(authentication.getName());

    IngredientLearningService.IngredientLibraryStats stats =
            learningService.getUserLibraryStats(userId);

    IngredientLibraryStatsResponse response = IngredientLibraryStatsResponse.builder()
            .totalIngredientsLearned(stats.getTotalIngredientsLearned())
            .averageConfidence(stats.getAverageConfidence())
            .highConfidenceCount(stats.getHighConfidenceCount())
            .learningProgress(calculateProgress(stats))
            .build();

    return ResponseEntity.ok(response);
}

private double calculateProgress(IngredientLearningService.IngredientLibraryStats stats) {
    // Progress: 0-100% based on learned ingredients
    // Assumes "mastery" at 50+ ingredients with avg confidence >0.7
    long count = stats.getTotalIngredientsLearned();
    double confidence = stats.getAverageConfidence();

    double countProgress = Math.min(count / 50.0, 1.0);  // Max out at 50 ingredients
    double confidenceProgress = confidence;  // Already 0-1

    return (countProgress * 0.6 + confidenceProgress * 0.4) * 100.0;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class IngredientLibraryStatsResponse {
    private Long totalIngredientsLearned;
    private Double averageConfidence;
    private Integer highConfidenceCount;
    private Double learningProgress;  // 0-100%
}
```

#### Testing Checklist for Phase 3B

- [ ] User corrects ingredient → learningService.learnFromCorrection() called
- [ ] Normalization: "idly" → "idli", "chutny" → "chutney"
- [ ] First correction creates new UserIngredientLibrary entry
- [ ] Second correction updates running average (Welford's algorithm)
- [ ] Confidence score increases with more corrections
- [ ] Confidence score decreases with high variability (std dev)
- [ ] Typical quantity updated with weighted average (70% old, 30% new)
- [ ] Levenshtein distance correctly matches fuzzy names
- [ ] GET /api/ingredients/library/stats returns correct counts
- [ ] Learning progress calculated correctly (0-100%)

---

## Phase 3C: Predictions & Auto-fill (Week 5-6)

### Goal
Use learned ingredient library to provide intelligent auto-fill suggestions and detect recipe patterns.

### Tasks

#### 1. IngredientPredictionService

**Purpose**: Suggest ingredients and quantities based on user's learned patterns.

`IngredientPredictionService.java`:
```java
@Service
@Slf4j
public class IngredientPredictionService {

    private final UserIngredientLibraryRepository ingredientLibraryRepository;
    private final IngredientNormalizationService normalizationService;

    public IngredientPredictionService(
            UserIngredientLibraryRepository ingredientLibraryRepository,
            IngredientNormalizationService normalizationService) {
        this.ingredientLibraryRepository = ingredientLibraryRepository;
        this.normalizationService = normalizationService;
    }

    /**
     * Get prediction for an ingredient based on user's library.
     *
     * Used when:
     * 1. AI extracts ingredient from image (suggest corrections)
     * 2. User is manually adding ingredient (auto-fill)
     * 3. Recipe pattern suggests missing ingredient
     */
    public IngredientPrediction predictIngredient(String ingredientName, UUID userId) {
        log.info("Predicting ingredient: {} for user: {}", ingredientName, userId);

        // Normalize name for matching
        String normalizedName = normalizationService.normalize(ingredientName);

        // Exact match
        Optional<UserIngredientLibrary> exactMatch =
                ingredientLibraryRepository.findByUserIdAndNormalizedName(userId, normalizedName);

        if (exactMatch.isPresent()) {
            return buildPrediction(exactMatch.get(), PredictionConfidence.HIGH);
        }

        // Fuzzy match
        List<UserIngredientLibrary> library =
                ingredientLibraryRepository.findByUserIdOrderByConfidenceScoreDesc(userId);

        UserIngredientLibrary fuzzyMatch =
                normalizationService.findBestMatch(ingredientName, library, 2);  // Max distance: 2

        if (fuzzyMatch != null) {
            return buildPrediction(fuzzyMatch, PredictionConfidence.MEDIUM);
        }

        // No match found
        return IngredientPrediction.builder()
                .ingredientName(ingredientName)
                .found(false)
                .confidence(PredictionConfidence.NONE)
                .message("No data available for this ingredient yet")
                .build();
    }

    private IngredientPrediction buildPrediction(
            UserIngredientLibrary libraryEntry,
            PredictionConfidence confidence) {

        // Calculate predicted nutrition for typical quantity
        double typicalQuantityG = libraryEntry.getTypicalQuantity();
        String unit = libraryEntry.getTypicalUnit();

        // Convert typical quantity to grams if needed
        double quantityInG = convertToGrams(typicalQuantityG, unit);

        return IngredientPrediction.builder()
                .ingredientName(libraryEntry.getIngredientName())
                .found(true)
                .confidence(confidence)
                .libraryConfidence(libraryEntry.getConfidenceScore())
                .sampleSize(libraryEntry.getSampleSize())

                // Predicted values based on typical quantity
                .predictedQuantity(typicalQuantityG)
                .predictedUnit(unit)
                .predictedCalories(scaleFromPer100g(
                        libraryEntry.getAvgCaloriesPer100g(), quantityInG))
                .predictedProteinG(scaleFromPer100g(
                        libraryEntry.getAvgProteinPer100g(), quantityInG))
                .predictedFatG(scaleFromPer100g(
                        libraryEntry.getAvgFatPer100g(), quantityInG))
                .predictedCarbsG(scaleFromPer100g(
                        libraryEntry.getAvgCarbsPer100g(), quantityInG))

                // Per 100g reference
                .avgCaloriesPer100g(libraryEntry.getAvgCaloriesPer100g())
                .avgProteinPer100g(libraryEntry.getAvgProteinPer100g())

                .message(String.format("Based on %d previous meals (confidence: %.0f%%)",
                        libraryEntry.getSampleSize(),
                        libraryEntry.getConfidenceScore() * 100))
                .build();
    }

    private double convertToGrams(double quantity, String unit) {
        // Same logic as in IngredientLearningService
        return switch (unit.toLowerCase()) {
            case "g" -> quantity;
            case "kg" -> quantity * 1000;
            case "ml" -> quantity;
            case "l" -> quantity * 1000;
            case "oz" -> quantity * 28.35;
            case "cup" -> quantity * 240;
            case "tbsp" -> quantity * 15;
            case "tsp" -> quantity * 5;
            default -> quantity;
        };
    }

    private Integer scaleFromPer100g(Double valuePer100g, double quantityInG) {
        if (valuePer100g == null) return null;
        return (int) Math.round((valuePer100g / 100.0) * quantityInG);
    }

    /**
     * Get multiple predictions for auto-complete suggestions.
     */
    public List<IngredientPrediction> searchPredictions(String query, UUID userId, int limit) {
        List<UserIngredientLibrary> results =
                ingredientLibraryRepository.searchByName(userId, query);

        return results.stream()
                .limit(limit)
                .map(entry -> buildPrediction(entry, PredictionConfidence.HIGH))
                .collect(Collectors.toList());
    }

    public enum PredictionConfidence {
        NONE,    // No data
        LOW,     // Poor match or low library confidence
        MEDIUM,  // Fuzzy match
        HIGH     // Exact match with good library confidence
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientPrediction {
        private String ingredientName;
        private boolean found;
        private PredictionConfidence confidence;
        private Double libraryConfidence;  // 0-1
        private Integer sampleSize;

        // Predicted values
        private Double predictedQuantity;
        private String predictedUnit;
        private Integer predictedCalories;
        private Double predictedProteinG;
        private Double predictedFatG;
        private Double predictedCarbsG;

        // Per 100g reference
        private Double avgCaloriesPer100g;
        private Double avgProteinPer100g;

        private String message;
    }
}
```

#### 2. RecipePatternService

**Purpose**: Learn and suggest common ingredient combinations.

`RecipePatternService.java`:
```java
@Service
@Slf4j
@Transactional
public class RecipePatternService {

    private final UserRecipePatternRepository recipePatternRepository;
    private final MealIngredientRepository mealIngredientRepository;
    private final IngredientNormalizationService normalizationService;

    public RecipePatternService(
            UserRecipePatternRepository recipePatternRepository,
            MealIngredientRepository mealIngredientRepository,
            IngredientNormalizationService normalizationService) {
        this.recipePatternRepository = recipePatternRepository;
        this.mealIngredientRepository = mealIngredientRepository;
        this.normalizationService = normalizationService;
    }

    /**
     * Detect and update recipe patterns from a meal.
     *
     * Example: User eats idli → detect it typically comes with sambar + chutney
     *
     * Called after user completes editing a meal's ingredients.
     */
    public void updateRecipePatterns(UUID mealId, User user) {
        List<MealIngredient> ingredients =
                mealIngredientRepository.findByMealIdOrderByDisplayOrderAsc(mealId);

        if (ingredients.isEmpty()) {
            return;
        }

        // Identify "primary" ingredient (largest quantity or first)
        MealIngredient primary = identifyPrimaryIngredient(ingredients);
        String primaryName = normalizationService.normalize(primary.getIngredientName());

        log.info("Updating recipe pattern for: {}", primaryName);

        // Find existing pattern or create new
        UserRecipePattern pattern = recipePatternRepository
                .findByUserIdAndRecipeName(user.getId(), primaryName)
                .orElse(null);

        if (pattern == null) {
            pattern = createNewPattern(user, primaryName, ingredients);
        } else {
            updateExistingPattern(pattern, ingredients);
        }

        recipePatternRepository.save(pattern);
    }

    private MealIngredient identifyPrimaryIngredient(List<MealIngredient> ingredients) {
        // Heuristic: Primary is the one with highest calories OR first item
        return ingredients.stream()
                .max(Comparator.comparing(i -> i.getCalories() != null ? i.getCalories() : 0.0))
                .orElse(ingredients.get(0));
    }

    private UserRecipePattern createNewPattern(
            User user,
            String primaryName,
            List<MealIngredient> ingredients) {

        List<Map<String, Object>> commonIngredients = new ArrayList<>();
        for (MealIngredient ingredient : ingredients) {
            Map<String, Object> ingredientMap = new HashMap<>();
            ingredientMap.put("name", ingredient.getIngredientName());
            ingredientMap.put("quantity", ingredient.getQuantity());
            ingredientMap.put("unit", ingredient.getUnit());
            commonIngredients.add(ingredientMap);
        }

        return UserRecipePattern.builder()
                .user(user)
                .recipeName(primaryName)
                .recipeKeywords(new String[]{primaryName})
                .commonIngredients(commonIngredients)
                .timesMade(1)
                .lastMade(LocalDateTime.now())
                .build();
    }

    private void updateExistingPattern(
            UserRecipePattern pattern,
            List<MealIngredient> newIngredients) {

        // Simple approach: Merge new ingredients with existing
        // More sophisticated: Track frequency, remove rare ingredients

        pattern.setTimesMade(pattern.getTimesMade() + 1);
        pattern.setLastMade(LocalDateTime.now());

        // For now, just update commonIngredients with latest meal
        // TODO: Implement frequency-based ingredient retention
        List<Map<String, Object>> updatedIngredients = new ArrayList<>();
        for (MealIngredient ingredient : newIngredients) {
            Map<String, Object> ingredientMap = new HashMap<>();
            ingredientMap.put("name", ingredient.getIngredientName());
            ingredientMap.put("quantity", ingredient.getQuantity());
            ingredientMap.put("unit", ingredient.getUnit());
            updatedIngredients.add(ingredientMap);
        }
        pattern.setCommonIngredients(updatedIngredients);
    }

    /**
     * Suggest missing ingredients based on recipe patterns.
     *
     * Example: User adds "idli" → suggest "sambar" and "chutney"
     */
    public List<IngredientSuggestion> suggestMissingIngredients(
            List<MealIngredient> currentIngredients,
            UUID userId) {

        if (currentIngredients.isEmpty()) {
            return Collections.emptyList();
        }

        // Identify primary ingredient
        MealIngredient primary = identifyPrimaryIngredient(currentIngredients);
        String primaryName = normalizationService.normalize(primary.getIngredientName());

        // Find pattern
        Optional<UserRecipePattern> pattern =
                recipePatternRepository.findByUserIdAndRecipeName(userId, primaryName);

        if (pattern.isEmpty()) {
            return Collections.emptyList();
        }

        // Extract current ingredient names
        Set<String> currentNames = currentIngredients.stream()
                .map(i -> normalizationService.normalize(i.getIngredientName()))
                .collect(Collectors.toSet());

        // Find missing ingredients
        List<IngredientSuggestion> suggestions = new ArrayList<>();
        List<Map<String, Object>> commonIngredients = pattern.get().getCommonIngredients();

        for (Map<String, Object> ingredient : commonIngredients) {
            String name = (String) ingredient.get("name");
            String normalizedName = normalizationService.normalize(name);

            if (!currentNames.contains(normalizedName)) {
                // This ingredient is missing
                suggestions.add(IngredientSuggestion.builder()
                        .ingredientName(name)
                        .quantity((Double) ingredient.get("quantity"))
                        .unit((String) ingredient.get("unit"))
                        .reason(String.format("You usually have this with %s",
                                primary.getIngredientName()))
                        .frequency(pattern.get().getTimesMade())
                        .build());
            }
        }

        return suggestions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientSuggestion {
        private String ingredientName;
        private Double quantity;
        private String unit;
        private String reason;
        private Integer frequency;  // How many times user had this combination
    }
}
```

#### 3. Enhanced AI Prompts with Learned Context

Modify `OpenAIVisionService` to include user's learned ingredients in the prompt:

```java
// Add method to AnalyzerService

public AnalysisResponse analyzeImageWithLearning(
        String imageUrl,
        String userDescription,
        LocationContext locationContext,
        LocalDateTime mealTime,
        UUID userId) {

    // Get user's high-confidence ingredients
    List<UserIngredientLibrary> learnedIngredients =
            ingredientLibraryRepository.findHighConfidenceIngredients(userId, 0.7);

    // Build context string
    String learningContext = buildLearningContext(learnedIngredients);

    // Enhance prompt with learning context
    String enhancedPrompt = getAnalysisPrompt(userDescription, locationContext, mealTime)
            + learningContext;

    // Call OpenAI with enhanced prompt
    return openAIVisionService.analyzeImageWithPrompt(imageUrl, enhancedPrompt);
}

private String buildLearningContext(List<UserIngredientLibrary> learnedIngredients) {
    if (learnedIngredients.isEmpty()) {
        return "";
    }

    StringBuilder context = new StringBuilder("\n\nUSER'S INGREDIENT PREFERENCES:\n");
    context.append("The user has previously logged these ingredients. " +
                   "If you see similar items in the image, use these as reference:\n\n");

    for (UserIngredientLibrary ingredient : learnedIngredients.subList(
            0, Math.min(10, learnedIngredients.size()))) {

        context.append(String.format("- %s: typically %.0f%s, ~%.0f cal per 100g\n",
                ingredient.getIngredientName(),
                ingredient.getTypicalQuantity(),
                ingredient.getTypicalUnit(),
                ingredient.getAvgCaloriesPer100g()));
    }

    context.append("\nUse these as guidance for portion sizes and nutrition estimates.\n");

    return context.toString();
}
```

#### 4. REST API Endpoints

Add to `IngredientController.java`:

```java
@Autowired
private IngredientPredictionService predictionService;

@Autowired
private RecipePatternService recipePatternService;

/**
 * Get prediction for an ingredient.
 */
@GetMapping("/ingredients/predict/{ingredientName}")
@Operation(summary = "Predict ingredient", description = "Get auto-fill suggestion based on learned data")
public ResponseEntity<IngredientPredictionResponse> predictIngredient(
        @PathVariable String ingredientName,
        Authentication authentication) {

    UUID userId = UUID.fromString(authentication.getName());

    IngredientPredictionService.IngredientPrediction prediction =
            predictionService.predictIngredient(ingredientName, userId);

    IngredientPredictionResponse response = IngredientPredictionResponse.builder()
            .ingredientName(prediction.getIngredientName())
            .found(prediction.isFound())
            .confidence(prediction.getConfidence().toString())
            .libraryConfidence(prediction.getLibraryConfidence())
            .sampleSize(prediction.getSampleSize())
            .predictedQuantity(prediction.getPredictedQuantity())
            .predictedUnit(prediction.getPredictedUnit())
            .predictedCalories(prediction.getPredictedCalories())
            .predictedProteinG(prediction.getPredictedProteinG())
            .predictedFatG(prediction.getPredictedFatG())
            .predictedCarbsG(prediction.getPredictedCarbsG())
            .avgCaloriesPer100g(prediction.getAvgCaloriesPer100g())
            .message(prediction.getMessage())
            .build();

    return ResponseEntity.ok(response);
}

/**
 * Search for ingredient predictions (auto-complete).
 */
@GetMapping("/ingredients/search")
@Operation(summary = "Search predictions", description = "Auto-complete ingredient search")
public ResponseEntity<List<IngredientPredictionResponse>> searchPredictions(
        @RequestParam String q,
        @RequestParam(defaultValue = "5") int limit,
        Authentication authentication) {

    UUID userId = UUID.fromString(authentication.getName());

    List<IngredientPredictionService.IngredientPrediction> predictions =
            predictionService.searchPredictions(q, userId, limit);

    List<IngredientPredictionResponse> responses = predictions.stream()
            .map(p -> IngredientPredictionResponse.builder()
                    .ingredientName(p.getIngredientName())
                    .found(p.isFound())
                    .confidence(p.getConfidence().toString())
                    .predictedQuantity(p.getPredictedQuantity())
                    .predictedUnit(p.getPredictedUnit())
                    .predictedCalories(p.getPredictedCalories())
                    .message(p.getMessage())
                    .build())
            .collect(Collectors.toList());

    return ResponseEntity.ok(responses);
}

/**
 * Get missing ingredient suggestions for a meal.
 */
@GetMapping("/meals/{mealId}/ingredient-suggestions")
@Operation(summary = "Get ingredient suggestions",
           description = "Suggest missing ingredients based on recipe patterns")
public ResponseEntity<List<IngredientSuggestionResponse>> getMealIngredientSuggestions(
        @PathVariable UUID mealId,
        Authentication authentication) {

    UUID userId = UUID.fromString(authentication.getName());

    // Verify meal belongs to user
    Meal meal = mealRepository.findById(mealId)
            .orElseThrow(() -> new RuntimeException("Meal not found: " + mealId));

    if (!meal.getUser().getId().equals(userId)) {
        throw new RuntimeException("Unauthorized");
    }

    List<MealIngredient> currentIngredients =
            mealIngredientRepository.findByMealIdOrderByDisplayOrderAsc(mealId);

    List<RecipePatternService.IngredientSuggestion> suggestions =
            recipePatternService.suggestMissingIngredients(currentIngredients, userId);

    List<IngredientSuggestionResponse> responses = suggestions.stream()
            .map(s -> IngredientSuggestionResponse.builder()
                    .ingredientName(s.getIngredientName())
                    .quantity(s.getQuantity())
                    .unit(s.getUnit())
                    .reason(s.getReason())
                    .frequency(s.getFrequency())
                    .build())
            .collect(Collectors.toList());

    return ResponseEntity.ok(responses);
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class IngredientPredictionResponse {
    private String ingredientName;
    private boolean found;
    private String confidence;
    private Double libraryConfidence;
    private Integer sampleSize;
    private Double predictedQuantity;
    private String predictedUnit;
    private Integer predictedCalories;
    private Double predictedProteinG;
    private Double predictedFatG;
    private Double predictedCarbsG;
    private Double avgCaloriesPer100g;
    private String message;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class IngredientSuggestionResponse {
    private String ingredientName;
    private Double quantity;
    private String unit;
    private String reason;
    private Integer frequency;
}
```

#### 5. Flutter UI Examples

**Auto-fill Widget:**
```dart
class IngredientAutoFillField extends StatefulWidget {
  final Function(IngredientPrediction) onSelected;

  @override
  _IngredientAutoFillFieldState createState() => _IngredientAutoFillFieldState();
}

class _IngredientAutoFillFieldState extends State<IngredientAutoFillField> {
  List<IngredientPrediction> _suggestions = [];
  String _query = '';

  Future<void> _searchPredictions(String query) async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/ingredients/search?q=$query&limit=5'),
      headers: {'Authorization': 'Bearer $token'},
    );

    if (response.statusCode == 200) {
      setState(() {
        _suggestions = (jsonDecode(response.body) as List)
            .map((json) => IngredientPrediction.fromJson(json))
            .toList();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        TextField(
          decoration: InputDecoration(
            labelText: 'Ingredient name',
            suffixIcon: Icon(Icons.search),
          ),
          onChanged: (value) {
            _query = value;
            if (value.length >= 2) {
              _searchPredictions(value);
            }
          },
        ),
        if (_suggestions.isNotEmpty)
          Container(
            height: 200,
            child: ListView.builder(
              itemCount: _suggestions.length,
              itemBuilder: (context, index) {
                final suggestion = _suggestions[index];
                return ListTile(
                  leading: Icon(
                    Icons.auto_awesome,
                    color: _getConfidenceColor(suggestion.confidence),
                  ),
                  title: Text(suggestion.ingredientName),
                  subtitle: Text(
                    '${suggestion.predictedQuantity}${suggestion.predictedUnit} • '
                    '${suggestion.predictedCalories} cal\n'
                    '${suggestion.message}',
                  ),
                  trailing: Icon(Icons.arrow_forward),
                  onTap: () {
                    widget.onSelected(suggestion);
                    setState(() => _suggestions = []);
                  },
                );
              },
            ),
          ),
      ],
    );
  }

  Color _getConfidenceColor(String confidence) {
    switch (confidence) {
      case 'HIGH': return Colors.green;
      case 'MEDIUM': return Colors.orange;
      default: return Colors.grey;
    }
  }
}
```

**Missing Ingredient Suggestions:**
```dart
class MissingIngredientsSuggestions extends StatelessWidget {
  final String mealId;

  Future<List<IngredientSuggestion>> _fetchSuggestions() async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/meals/$mealId/ingredient-suggestions'),
      headers: {'Authorization': 'Bearer $token'},
    );

    if (response.statusCode == 200) {
      return (jsonDecode(response.body) as List)
          .map((json) => IngredientSuggestion.fromJson(json))
          .toList();
    }
    return [];
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<IngredientSuggestion>>(
      future: _fetchSuggestions(),
      builder: (context, snapshot) {
        if (!snapshot.hasData || snapshot.data!.isEmpty) {
          return SizedBox.shrink();
        }

        return Card(
          color: Colors.blue[50],
          child: Padding(
            padding: EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.lightbulb_outline, color: Colors.blue),
                    SizedBox(width: 8),
                    Text('Missing ingredients?',
                         style: TextStyle(fontWeight: FontWeight.bold)),
                  ],
                ),
                SizedBox(height: 8),
                ...snapshot.data!.map((suggestion) => ListTile(
                  dense: true,
                  leading: Icon(Icons.add_circle_outline, color: Colors.blue),
                  title: Text(suggestion.ingredientName),
                  subtitle: Text(
                    '${suggestion.quantity}${suggestion.unit} • ${suggestion.reason}',
                  ),
                  trailing: TextButton(
                    onPressed: () => _addIngredient(suggestion),
                    child: Text('Add'),
                  ),
                )),
              ],
            ),
          ),
        );
      },
    );
  }

  void _addIngredient(IngredientSuggestion suggestion) {
    // Add ingredient to meal
  }
}
```

**Learning Progress Dashboard:**
```dart
class LearningProgressScreen extends StatelessWidget {
  Future<LibraryStats> _fetchStats() async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/ingredients/library/stats'),
      headers: {'Authorization': 'Bearer $token'},
    );

    return LibraryStats.fromJson(jsonDecode(response.body));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Learning Progress')),
      body: FutureBuilder<LibraryStats>(
        future: _fetchStats(),
        builder: (context, snapshot) {
          if (!snapshot.hasData) {
            return Center(child: CircularProgressIndicator());
          }

          final stats = snapshot.data!;

          return SingleChildScrollView(
            padding: EdgeInsets.all(16),
            child: Column(
              children: [
                // Progress circle
                CircularProgressIndicator(
                  value: stats.learningProgress / 100,
                  backgroundColor: Colors.grey[300],
                  valueColor: AlwaysStoppedAnimation(Colors.green),
                  strokeWidth: 10,
                ),
                SizedBox(height: 16),
                Text('${stats.learningProgress.toInt()}%',
                     style: TextStyle(fontSize: 48, fontWeight: FontWeight.bold)),
                Text('Learning Progress'),

                SizedBox(height: 32),

                // Stats cards
                _buildStatCard(
                  icon: Icons.restaurant,
                  title: 'Ingredients Learned',
                  value: '${stats.totalIngredientsLearned}',
                  subtitle: 'Building your personal library',
                ),
                _buildStatCard(
                  icon: Icons.analytics,
                  title: 'Average Confidence',
                  value: '${(stats.averageConfidence * 100).toInt()}%',
                  subtitle: 'How reliable your data is',
                ),
                _buildStatCard(
                  icon: Icons.star,
                  title: 'High Confidence',
                  value: '${stats.highConfidenceCount}',
                  subtitle: 'Ready for auto-fill',
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildStatCard({
    required IconData icon,
    required String title,
    required String value,
    required String subtitle,
  }) {
    return Card(
      margin: EdgeInsets.only(bottom: 16),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: Colors.green[100],
          child: Icon(icon, color: Colors.green),
        ),
        title: Text(value, style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: TextStyle(fontWeight: FontWeight.bold)),
            Text(subtitle, style: TextStyle(fontSize: 12)),
          ],
        ),
      ),
    );
  }
}
```

#### Testing Checklist for Phase 3C

- [ ] GET /api/ingredients/predict/{name} returns prediction with confidence
- [ ] Prediction uses typical quantity from library
- [ ] Prediction scales per-100g nutrition to serving size
- [ ] GET /api/ingredients/search?q=idli returns auto-complete results
- [ ] Fuzzy matching works for typos ("idly" finds "idli")
- [ ] Recipe pattern created after user edits meal ingredients
- [ ] GET /api/meals/{id}/ingredient-suggestions returns missing ingredients
- [ ] Missing ingredient suggestions based on recipe patterns
- [ ] Flutter auto-fill widget displays predictions
- [ ] Flutter displays confidence levels with color coding
- [ ] Learning progress dashboard shows correct stats

---

## Mathematical Foundations

### Welford's Online Algorithm

**Purpose**: Calculate running mean and variance without storing all values.

**Formula**:
```
n = sample size
x_n = new value
M_n-1 = previous mean
S_n-1 = previous sum of squared differences

# Update mean:
δ = x_n - M_n-1
M_n = M_n-1 + δ/n

# Update sum of squared differences:
S_n = S_n-1 + δ * (x_n - M_n)

# Standard deviation:
σ_n = sqrt(S_n / (n-1))
```

**Properties**:
- O(1) space complexity (no need to store historical values)
- Numerically stable (avoids catastrophic cancellation)
- Single pass (can update in real-time)

**Example**:
```
User corrects "chutney" calories 5 times: 65, 70, 68, 72, 66

n=1: mean=65, stddev=undefined
n=2: mean=67.5, stddev=3.54
n=3: mean=67.67, stddev=2.52
n=4: mean=68.75, stddev=2.99
n=5: mean=68.2, stddev=2.86

Result: chutney = 68.2 ± 2.86 cal per serving
```

### Confidence Score Formula

**Purpose**: Quantify trustworthiness of learned ingredient data.

**Formula**:
```
confidence = (1 - e^(-n/5)) * consistency_factor

where:
- n = sample size
- consistency_factor = f(σ) where σ = standard deviation

consistency_factor:
- σ < 5:   1.0 (very consistent)
- σ < 10:  0.9 (consistent)
- σ < 20:  0.7 (somewhat variable)
- σ < 30:  0.5 (variable)
- σ >= 30: 0.3 (highly variable)
```

**Interpretation**:
- 0.0-0.5: Low confidence (use with caution, show warning)
- 0.5-0.7: Medium confidence (acceptable for suggestions)
- 0.7-0.9: High confidence (reliable for auto-fill)
- 0.9-1.0: Very high confidence (default choice)

**Rationale**:
1. **Sample size component**: More corrections = higher confidence
   - Asymptotic: approaches 1.0 as n → ∞
   - e^(-n/5): decays exponentially (n=5 → 0.37, n=10 → 0.13)
2. **Consistency component**: Lower variability = higher confidence
   - Users who consistently use same portions → trustworthy
   - High variability → unpredictable usage patterns

### Levenshtein Distance

**Purpose**: Fuzzy match ingredient names to handle typos and variants.

**Formula**:
```
lev(a, b) = minimum edits to transform string a into string b

Edits:
- Insertion (cost = 1)
- Deletion (cost = 1)
- Substitution (cost = 1)
```

**Examples**:
```
lev("idly", "idli") = 1  (delete 'y')
lev("chutny", "chutney") = 2  (insert 'e', move letters)
lev("dosa", "dosay") = 1  (delete 'y')
```

**Threshold**: Accept matches with distance ≤ 2 for auto-fill.

---

## Database Optimizations

### Indexes (already created in V15)

```sql
-- Fast lookup by user + normalized name (most common query)
CREATE INDEX idx_user_ingredient_library_user_name
  ON user_ingredient_library(user_id, normalized_name);

-- Confidence-based queries
CREATE INDEX idx_user_ingredient_library_confidence
  ON user_ingredient_library(user_id, confidence_score DESC);

-- Search by ingredient name
CREATE INDEX idx_user_ingredient_library_search
  ON user_ingredient_library(user_id, ingredient_name);

-- Recipe pattern lookups
CREATE INDEX idx_user_recipe_patterns_user_recipe
  ON user_recipe_patterns(user_id, recipe_name);
```

### Query Optimization Tips

1. **Batch updates**: Update multiple ingredients in single transaction
2. **Cache frequently used patterns**: Store top 10 recipe patterns in Redis
3. **Lazy loading**: Don't load full ingredient library unless needed
4. **Denormalize counts**: Store `total_uses` instead of counting each time

---

## Success Metrics

### Phase 3B: Learning

**Learning Rate**:
- Target: 80% of corrected ingredients trigger learning
- Metric: `learned_corrections / total_corrections`

**Library Growth**:
- Target: Average user has 20+ ingredients after 2 weeks
- Target: Average user has 50+ ingredients after 4 weeks

**Confidence Distribution**:
- Target: 60% of ingredients have confidence >0.7 after 1 month
- Target: 40% of ingredients have confidence >0.9 after 2 months

**Data Quality**:
- Target: Average std dev <15 cal per 100g
- Target: Sample size >3 for high-confidence ingredients

### Phase 3C: Predictions

**Prediction Accuracy**:
- Target: 80% of predictions within ±10% of user's actual usage
- Metric: Compare predicted quantity vs actual corrected quantity

**Auto-fill Adoption**:
- Target: 40% of users use auto-fill at least once per week
- Target: 60% acceptance rate when auto-fill is offered

**Recipe Pattern Detection**:
- Target: 70% of meals with primary ingredient trigger pattern detection
- Target: Average 2-3 common ingredients per recipe pattern

**User Satisfaction**:
- Target: Users save 30 seconds per ingredient with auto-fill
- Target: 80% of auto-fill suggestions rated "helpful" by users

### Overall System Health

**AI Accuracy Improvement**:
- Baseline: 70% accuracy for new ingredients (no learning)
- Target: 85% accuracy for learned ingredients (Phase 3B complete)
- Target: 95% accuracy with auto-fill (Phase 3C complete)

**User Retention**:
- Target: Users who reach 20+ learned ingredients have 25% higher retention
- Target: Users who use auto-fill have 30% higher engagement

---

## Testing Strategy

### Unit Tests

```java
@Test
void testWelfordAlgorithm() {
    // Test: Mean and std dev calculation
    double[] values = {65, 70, 68, 72, 66};

    double mean = 0;
    double m2 = 0;

    for (int i = 0; i < values.length; i++) {
        double delta = values[i] - mean;
        mean += delta / (i + 1);
        double delta2 = values[i] - mean;
        m2 += delta * delta2;
    }

    double stdDev = Math.sqrt(m2 / (values.length - 1));

    assertEquals(68.2, mean, 0.1);
    assertEquals(2.86, stdDev, 0.1);
}

@Test
void testConfidenceScore() {
    // High sample size, low variability → high confidence
    assertEquals(0.9, calculateConfidence(10, 5.0), 0.1);

    // Low sample size → low confidence
    assertEquals(0.3, calculateConfidence(2, 10.0), 0.1);

    // High variability → low confidence
    assertEquals(0.4, calculateConfidence(5, 35.0), 0.1);
}

@Test
void testNormalization() {
    assertEquals("idli", normalize("idly"));
    assertEquals("idli", normalize("Idli"));
    assertEquals("idli", normalize("IDLI "));
    assertEquals("chutney", normalize("chutny"));
    assertEquals("dosa", normalize("dosay"));
}

@Test
void testLevenshteinDistance() {
    assertEquals(1, levenshteinDistance("idly", "idli"));
    assertEquals(2, levenshteinDistance("chutny", "chutney"));
    assertEquals(0, levenshteinDistance("dosa", "dosa"));
}
```

### Integration Tests

```java
@Test
@Transactional
void testLearningFlow() {
    // 1. User corrects ingredient
    MealIngredient ingredient = createTestIngredient("chutney", 50, "g", 68);
    ingredient.setIsUserCorrected(true);
    mealIngredientRepository.save(ingredient);

    // 2. Trigger learning
    learningService.learnFromCorrection(ingredient, testUser);

    // 3. Verify library entry created
    Optional<UserIngredientLibrary> library =
            ingredientLibraryRepository.findByUserIdAndNormalizedName(
                    testUser.getId(), "chutney");

    assertTrue(library.isPresent());
    assertEquals(136.0, library.get().getAvgCaloriesPer100g(), 1.0);
    assertEquals(1, library.get().getSampleSize());
}

@Test
void testPredictionAccuracy() {
    // Given: User has learned "chutney" from 5 corrections
    createLearnedIngredient("chutney", 50, 68, 5);

    // When: Get prediction
    IngredientPrediction prediction =
            predictionService.predictIngredient("chutney", testUser.getId());

    // Then: Prediction matches learned pattern
    assertTrue(prediction.isFound());
    assertEquals(50.0, prediction.getPredictedQuantity(), 1.0);
    assertEquals(68, prediction.getPredictedCalories(), 5);
    assertEquals(HIGH, prediction.getConfidence());
}

@Test
void testRecipePatternDetection() {
    // Given: User eats idli meal with sambar and chutney
    Meal meal = createTestMeal();
    addIngredient(meal, "idli", 2, "piece");
    addIngredient(meal, "sambar", 150, "g");
    addIngredient(meal, "chutney", 50, "g");

    // When: Update recipe patterns
    recipePatternService.updateRecipePatterns(meal.getId(), testUser);

    // Then: Pattern created for "idli"
    Optional<UserRecipePattern> pattern =
            recipePatternRepository.findByUserIdAndRecipeName(testUser.getId(), "idli");

    assertTrue(pattern.isPresent());
    assertEquals(3, pattern.get().getCommonIngredients().size());
}
```

### Manual Testing Checklist

**Phase 3B**:
- [ ] Upload meal, correct ingredient quantity
- [ ] Verify UserIngredientLibrary entry created
- [ ] Correct same ingredient again with different quantity
- [ ] Verify running average updated correctly
- [ ] Correct same ingredient 5+ times
- [ ] Verify confidence score increases
- [ ] Test normalization: "idly" → "idli"
- [ ] Test fuzzy matching: "chutny" finds "chutney"
- [ ] GET /api/ingredients/library/stats shows correct counts

**Phase 3C**:
- [ ] Add ingredient "idli" → check auto-fill suggestion
- [ ] Verify prediction shows typical quantity (e.g., 2 pieces)
- [ ] Verify prediction shows confidence level
- [ ] Accept auto-fill suggestion
- [ ] Verify nutrition auto-populated
- [ ] Create meal with "idli" → check missing ingredient suggestions
- [ ] Verify "sambar" and "chutney" suggested
- [ ] Test auto-complete: type "chu" → shows "chutney"
- [ ] View learning progress dashboard
- [ ] Verify progress percentage increases with corrections

---

## Timeline Summary

| Phase | Duration | Key Deliverables | Testing |
|-------|----------|------------------|---------|
| **3B: Learning** | Week 3-4 | IngredientLearningService, Welford's algorithm, normalization, confidence scoring, learning trigger | Unit tests, integration tests, manual correction flow |
| **3C: Predictions** | Week 5-6 | IngredientPredictionService, RecipePatternService, auto-fill APIs, Flutter UI | Prediction accuracy tests, pattern detection tests, UI/UX testing |

**Total Duration**: 4 weeks

**After Phase 3C**: Complete ingredient learning system enables:
- ✅ Progressive accuracy improvement
- ✅ Time-saving auto-fill
- ✅ Personalized nutrition tracking
- ✅ Foundation for Phase 4 (packaged foods)

---

## Next Steps After Phase 3C

### Ready for Phase 4: Packaged Food Scanner
With ingredient learning complete, Phase 4 can:
- Compare packaged food nutrition with user's typical portions
- Detect discrepancies between label and user's learned data
- Hybrid meals: Combine packaged food + home-cooked ingredients

### Optional Enhancements (Future)
- **Smart portion detection**: Use computer vision to estimate quantities
- **Voice input**: "Add 2 idlis" → auto-fill from library
- **Meal templates**: Save common meals for 1-tap logging
- **Nutrition goals integration**: Auto-suggest ingredients to meet protein/calorie targets
- **Community sharing**: Anonymized ingredient database for similar users

---

## Conclusion

Phase 3B & 3C transform NutriLens from a simple tracking app to an **intelligent learning system**:

### Key Innovations

1. **Statistical Learning**: Welford's algorithm for numerically stable, memory-efficient learning
2. **Confidence Scoring**: Transparent trust metrics based on sample size and variability
3. **Fuzzy Matching**: Levenshtein distance for typo tolerance and variant handling
4. **Recipe Patterns**: Learn common ingredient combinations for smart suggestions
5. **Progressive Improvement**: System gets smarter with every correction

### User Benefits

- **Time Savings**: 30 seconds per ingredient with auto-fill
- **Accuracy**: 95% vs 70% for unlearned ingredients
- **Transparency**: Confidence scores show data reliability
- **Personalization**: Learns YOUR cooking, not generic averages
- **Education**: Users understand their eating patterns

### Technical Excellence

- **O(1) space**: No need to store all historical corrections
- **Real-time updates**: Instant learning after each correction
- **Scalable**: Efficient queries with proper indexing
- **Robust**: Handles typos, variants, and edge cases

This is the foundation that makes NutriLens **progressively smarter** - the more you use it, the better it gets. No other nutrition app does this! 🚀
