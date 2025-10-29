package com.nutritheous.ingredient.dto;

import com.nutritheous.ingredient.MealIngredient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for individual ingredient in a meal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientResponse {

    private UUID id;
    private String ingredientName;
    private String ingredientCategory;
    private Double quantity;
    private String unit;

    // Nutrition per this serving
    private Double calories;
    private Double proteinG;
    private Double fatG;
    private Double saturatedFatG;
    private Double carbohydratesG;
    private Double fiberG;
    private Double sugarG;
    private Double sodiumMg;

    // Metadata
    private Boolean isAiExtracted;
    private Boolean isUserCorrected;
    private Double aiConfidence;
    private Integer displayOrder;

    /**
     * Convert entity to DTO
     */
    public static IngredientResponse fromEntity(MealIngredient ingredient) {
        return IngredientResponse.builder()
                .id(ingredient.getId())
                .ingredientName(ingredient.getIngredientName())
                .ingredientCategory(ingredient.getIngredientCategory())
                .quantity(ingredient.getQuantity())
                .unit(ingredient.getUnit())
                .calories(ingredient.getCalories())
                .proteinG(ingredient.getProteinG())
                .fatG(ingredient.getFatG())
                .saturatedFatG(ingredient.getSaturatedFatG())
                .carbohydratesG(ingredient.getCarbohydratesG())
                .fiberG(ingredient.getFiberG())
                .sugarG(ingredient.getSugarG())
                .sodiumMg(ingredient.getSodiumMg())
                .isAiExtracted(ingredient.getIsAiExtracted())
                .isUserCorrected(ingredient.getIsUserCorrected())
                .aiConfidence(ingredient.getAiConfidence())
                .displayOrder(ingredient.getDisplayOrder())
                .build();
    }
}
