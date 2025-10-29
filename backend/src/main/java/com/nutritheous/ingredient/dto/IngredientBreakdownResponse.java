package com.nutritheous.ingredient.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for complete ingredient breakdown of a meal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete ingredient breakdown for a meal")
public class IngredientBreakdownResponse {

    @Schema(description = "Meal ID")
    private UUID mealId;

    @Schema(description = "Meal name/description")
    private String mealName;

    @Schema(description = "List of ingredients in the meal")
    private List<IngredientResponse> ingredients;

    @Schema(description = "Total number of ingredients")
    private Integer totalIngredients;

    @Schema(description = "Number of AI-extracted ingredients")
    private Integer aiExtractedCount;

    @Schema(description = "Number of user-corrected ingredients")
    private Integer userCorrectedCount;

    // Aggregated nutrition totals (sum of all ingredients)
    @Schema(description = "Total calories from all ingredients")
    private Double totalCalories;

    @Schema(description = "Total protein in grams")
    private Double totalProteinG;

    @Schema(description = "Total fat in grams")
    private Double totalFatG;

    @Schema(description = "Total saturated fat in grams")
    private Double totalSaturatedFatG;

    @Schema(description = "Total carbohydrates in grams")
    private Double totalCarbohydratesG;

    @Schema(description = "Total fiber in grams")
    private Double totalFiberG;

    @Schema(description = "Total sugar in grams")
    private Double totalSugarG;

    @Schema(description = "Total sodium in milligrams")
    private Double totalSodiumMg;

    /**
     * Calculate totals from ingredient list
     */
    public void calculateTotals() {
        if (ingredients == null || ingredients.isEmpty()) {
            return;
        }

        this.totalCalories = ingredients.stream()
                .map(IngredientResponse::getCalories)
                .filter(c -> c != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        this.totalProteinG = ingredients.stream()
                .map(IngredientResponse::getProteinG)
                .filter(p -> p != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        this.totalFatG = ingredients.stream()
                .map(IngredientResponse::getFatG)
                .filter(f -> f != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        this.totalSaturatedFatG = ingredients.stream()
                .map(IngredientResponse::getSaturatedFatG)
                .filter(sf -> sf != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        this.totalCarbohydratesG = ingredients.stream()
                .map(IngredientResponse::getCarbohydratesG)
                .filter(c -> c != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        this.totalFiberG = ingredients.stream()
                .map(IngredientResponse::getFiberG)
                .filter(f -> f != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        this.totalSugarG = ingredients.stream()
                .map(IngredientResponse::getSugarG)
                .filter(s -> s != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        this.totalSodiumMg = ingredients.stream()
                .map(IngredientResponse::getSodiumMg)
                .filter(s -> s != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        this.totalIngredients = ingredients.size();
        this.aiExtractedCount = (int) ingredients.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsAiExtracted()))
                .count();
        this.userCorrectedCount = (int) ingredients.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsUserCorrected()))
                .count();
    }
}
