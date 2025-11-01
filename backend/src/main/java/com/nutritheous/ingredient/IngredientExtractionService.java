package com.nutritheous.ingredient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritheous.common.dto.AnalysisResponse;
import com.nutritheous.meal.Meal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for extracting individual ingredients from meals using AI.
 * Enhances AI prompts to request detailed ingredient-level breakdown.
 */
@Service
@Slf4j
public class IngredientExtractionService {

    private final MealIngredientRepository mealIngredientRepository;
    private final ObjectMapper objectMapper;
    private final IngredientLearningService learningService;

    public IngredientExtractionService(
            MealIngredientRepository mealIngredientRepository,
            ObjectMapper objectMapper,
            IngredientLearningService learningService) {
        this.mealIngredientRepository = mealIngredientRepository;
        this.objectMapper = objectMapper;
        this.learningService = learningService;
        log.info("IngredientExtractionService initialized");
    }

    /**
     * Enhanced AI prompt fragment for ingredient extraction.
     * This should be added to the OpenAI Vision prompt to request detailed ingredient breakdown.
     */
    public static String getIngredientExtractionPromptFragment() {
        return """

                INGREDIENT BREAKDOWN (CRITICAL):
                In addition to overall nutrition, provide a detailed breakdown of individual ingredients.
                Add an "ingredient_breakdown" field with an array of ingredients:

                "ingredient_breakdown": [
                  {
                    "name": "idli",
                    "category": "grain",
                    "quantity": 2.0,
                    "unit": "piece",
                    "calories": 78,
                    "protein_g": 2.8,
                    "fat_g": 0.3,
                    "saturated_fat_g": 0.1,
                    "carbohydrates_g": 15.0,
                    "fiber_g": 1.2,
                    "sugar_g": 0.5,
                    "sodium_mg": 45.0
                  },
                  {
                    "name": "coconut chutney",
                    "category": "condiment",
                    "quantity": 50.0,
                    "unit": "g",
                    "calories": 68,
                    "protein_g": 1.2,
                    "fat_g": 6.5,
                    "saturated_fat_g": 5.8,
                    "carbohydrates_g": 2.5,
                    "fiber_g": 1.8,
                    "sugar_g": 0.8,
                    "sodium_mg": 120.0
                  }
                ]

                For each ingredient:
                - name: specific ingredient name (e.g., "idli", "sambar", "ghee")
                - category: grain/protein/vegetable/fat/dairy/spice/condiment/beverage
                - quantity: estimated amount
                - unit: g, ml, piece, tsp, tbsp, cup (use metric when possible)
                - nutrition values: per THIS serving (not per 100g)

                Important:
                - Break down complex meals into constituent ingredients
                - For South Indian meals, identify each component (idli, sambar, chutney, etc.)
                - For restaurant meals, estimate typical ingredient quantities
                - For home-cooked meals, consider typical recipe proportions
                - Sum of ingredient nutrition should approximately match overall meal nutrition
                - If unable to determine ingredient breakdown, return empty array []
                """;
    }

    /**
     * Extracts and saves ingredients from AI analysis response.
     * Parses the "ingredient_breakdown" field from AI response and creates MealIngredient records.
     *
     * @param meal The meal to associate ingredients with
     * @param aiResponse Raw AI response string (may contain ingredient_breakdown field)
     * @param analysisResponse Parsed analysis response with overall nutrition
     * @param aiConfidence AI confidence score for this meal
     */
    @Transactional
    public void extractAndSaveIngredients(
            Meal meal,
            String aiResponse,
            AnalysisResponse analysisResponse,
            Double aiConfidence
    ) {
        try {
            log.info("Extracting ingredients for meal {}", meal.getId());

            // Try to parse ingredient_breakdown from AI response
            List<MealIngredient> ingredients = parseIngredientsFromResponse(aiResponse, meal, aiConfidence);

            if (ingredients.isEmpty()) {
                log.info("No ingredient breakdown found in AI response for meal {}", meal.getId());
                // Fallback: create basic ingredients from the simple ingredients list
                ingredients = createBasicIngredientsFromList(meal, analysisResponse, aiConfidence);
            }

            if (ingredients.isEmpty()) {
                log.warn("No ingredients extracted for meal {}", meal.getId());
                return;
            }

            // Delete existing ingredients (in case this is a re-analysis)
            mealIngredientRepository.deleteByMealId(meal.getId());

            // Save all ingredients
            mealIngredientRepository.saveAll(ingredients);
            log.info("Saved {} ingredients for meal {}", ingredients.size(), meal.getId());

        } catch (Exception e) {
            log.error("Failed to extract ingredients for meal {}", meal.getId(), e);
            // Don't fail the entire meal save if ingredient extraction fails
        }
    }

    /**
     * Parse detailed ingredient breakdown from AI response.
     */
    private List<MealIngredient> parseIngredientsFromResponse(
            String aiResponse,
            Meal meal,
            Double aiConfidence
    ) {
        List<MealIngredient> ingredients = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(aiResponse);

            if (!rootNode.has("ingredient_breakdown")) {
                return ingredients;
            }

            JsonNode ingredientsNode = rootNode.get("ingredient_breakdown");
            if (!ingredientsNode.isArray()) {
                return ingredients;
            }

            int displayOrder = 0;
            for (JsonNode ingredientNode : ingredientsNode) {
                try {
                    MealIngredient ingredient = MealIngredient.builder()
                            .meal(meal)
                            .ingredientName(getStringValue(ingredientNode, "name"))
                            .ingredientCategory(getStringValue(ingredientNode, "category"))
                            .quantity(getDoubleValue(ingredientNode, "quantity"))
                            .unit(getStringValue(ingredientNode, "unit"))
                            .calories(getDoubleValue(ingredientNode, "calories"))
                            .proteinG(getDoubleValue(ingredientNode, "protein_g"))
                            .fatG(getDoubleValue(ingredientNode, "fat_g"))
                            .saturatedFatG(getDoubleValue(ingredientNode, "saturated_fat_g"))
                            .carbohydratesG(getDoubleValue(ingredientNode, "carbohydrates_g"))
                            .fiberG(getDoubleValue(ingredientNode, "fiber_g"))
                            .sugarG(getDoubleValue(ingredientNode, "sugar_g"))
                            .sodiumMg(getDoubleValue(ingredientNode, "sodium_mg"))
                            .isAiExtracted(true)
                            .isUserCorrected(false)
                            .aiConfidence(aiConfidence)
                            .displayOrder(displayOrder++)
                            .build();

                    // Validate required fields
                    if (ingredient.getIngredientName() != null &&
                        ingredient.getQuantity() != null &&
                        ingredient.getUnit() != null) {
                        ingredients.add(ingredient);
                    } else {
                        log.warn("Skipping ingredient with missing required fields: {}", ingredientNode);
                    }

                } catch (Exception e) {
                    log.error("Failed to parse ingredient: {}", ingredientNode, e);
                }
            }

            log.info("Parsed {} ingredients from detailed breakdown", ingredients.size());

        } catch (Exception e) {
            log.error("Failed to parse ingredient breakdown from AI response", e);
        }

        return ingredients;
    }

    /**
     * Fallback: Create basic ingredients from simple ingredients list.
     * Used when AI doesn't provide detailed breakdown.
     */
    private List<MealIngredient> createBasicIngredientsFromList(
            Meal meal,
            AnalysisResponse analysisResponse,
            Double aiConfidence
    ) {
        List<MealIngredient> ingredients = new ArrayList<>();

        if (analysisResponse.getIngredients() == null || analysisResponse.getIngredients().isEmpty()) {
            return ingredients;
        }

        log.info("Creating basic ingredients from simple list for meal {}", meal.getId());

        int displayOrder = 0;
        for (String ingredientName : analysisResponse.getIngredients()) {
            MealIngredient ingredient = MealIngredient.builder()
                    .meal(meal)
                    .ingredientName(ingredientName)
                    .ingredientCategory(null)  // Unknown
                    .quantity(1.0)             // Placeholder
                    .unit("serving")           // Placeholder
                    .calories(null)            // Unknown - user must fill
                    .proteinG(null)
                    .fatG(null)
                    .saturatedFatG(null)
                    .carbohydratesG(null)
                    .fiberG(null)
                    .sugarG(null)
                    .sodiumMg(null)
                    .isAiExtracted(true)
                    .isUserCorrected(false)
                    .aiConfidence(0.5)  // Lower confidence for basic extraction
                    .displayOrder(displayOrder++)
                    .build();

            ingredients.add(ingredient);
        }

        log.info("Created {} basic ingredients from simple list", ingredients.size());
        return ingredients;
    }

    /**
     * Helper method to safely extract string values from JSON.
     */
    private String getStringValue(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).isNull()
                ? node.get(fieldName).asText()
                : null;
    }

    /**
     * Helper method to safely extract double values from JSON.
     */
    private Double getDoubleValue(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asDouble();
    }

    /**
     * Get all ingredients for a meal.
     */
    public List<MealIngredient> getIngredientsForMeal(java.util.UUID mealId) {
        return mealIngredientRepository.findByMealIdOrderByDisplayOrderAsc(mealId);
    }

    /**
     * Update an ingredient (when user corrects it).
     * Triggers learning service to update user's ingredient library.
     */
    @Transactional
    public MealIngredient updateIngredient(
            java.util.UUID ingredientId,
            com.nutritheous.ingredient.dto.IngredientRequest request
    ) {
        MealIngredient ingredient = mealIngredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

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

        // Save the updated ingredient
        MealIngredient savedIngredient = mealIngredientRepository.save(ingredient);

        // Trigger learning from user correction
        // Only learn if this was a correction to AI-generated or previously corrected data
        if (savedIngredient.getMeal() != null && savedIngredient.getMeal().getUser() != null) {
            try {
                learningService.learnFromCorrection(savedIngredient, savedIngredient.getMeal().getUser());
                log.info("Learning triggered for ingredient '{}' (user: {})",
                        savedIngredient.getIngredientName(),
                        savedIngredient.getMeal().getUser().getId());
            } catch (Exception e) {
                // Don't fail the update if learning fails
                log.error("Failed to learn from correction for ingredient {}: {}",
                        savedIngredient.getIngredientName(), e.getMessage(), e);
            }
        } else {
            log.warn("Cannot trigger learning: ingredient {} missing meal or user reference",
                    savedIngredient.getId());
        }

        return savedIngredient;
    }

    /**
     * Add a new ingredient to a meal.
     */
    @Transactional
    public MealIngredient addIngredient(
            Meal meal,
            com.nutritheous.ingredient.dto.IngredientRequest request
    ) {
        // Get max display order
        List<MealIngredient> existing = mealIngredientRepository.findByMealIdOrderByDisplayOrderAsc(meal.getId());
        int maxOrder = existing.stream()
                .mapToInt(MealIngredient::getDisplayOrder)
                .max()
                .orElse(-1);

        MealIngredient ingredient = MealIngredient.builder()
                .meal(meal)
                .ingredientName(request.getIngredientName())
                .ingredientCategory(request.getIngredientCategory())
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .calories(request.getCalories())
                .proteinG(request.getProteinG())
                .fatG(request.getFatG())
                .saturatedFatG(request.getSaturatedFatG())
                .carbohydratesG(request.getCarbohydratesG())
                .fiberG(request.getFiberG())
                .sugarG(request.getSugarG())
                .sodiumMg(request.getSodiumMg())
                .isAiExtracted(false)  // User added
                .isUserCorrected(false)
                .aiConfidence(null)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : maxOrder + 1)
                .build();

        return mealIngredientRepository.save(ingredient);
    }

    /**
     * Delete an ingredient.
     */
    @Transactional
    public void deleteIngredient(java.util.UUID ingredientId) {
        mealIngredientRepository.deleteById(ingredientId);
    }
}
