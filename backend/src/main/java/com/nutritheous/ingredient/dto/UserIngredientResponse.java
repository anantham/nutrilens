package com.nutritheous.ingredient.dto;

import com.nutritheous.ingredient.UserIngredientLibrary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for learned ingredients in user's personal library
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Learned ingredient from user's personal library")
public class UserIngredientResponse {

    @Schema(description = "Ingredient ID")
    private UUID id;

    @Schema(description = "Original ingredient name", example = "idli")
    private String ingredientName;

    @Schema(description = "Ingredient category", example = "grain")
    private String ingredientCategory;

    @Schema(description = "Normalized name for matching", example = "idli")
    private String normalizedName;

    // Average nutrition per 100g (learned from corrections)
    @Schema(description = "Average calories per 100g", example = "120.0")
    private Double avgCaloriesPer100g;

    @Schema(description = "Average protein per 100g", example = "4.5")
    private Double avgProteinPer100g;

    @Schema(description = "Average fat per 100g", example = "1.2")
    private Double avgFatPer100g;

    @Schema(description = "Average carbs per 100g", example = "22.0")
    private Double avgCarbsPer100g;

    @Schema(description = "Standard deviation of calories (variability measure)", example = "10.5")
    private Double stdDevCalories;

    @Schema(description = "Number of times user has corrected this ingredient", example = "5")
    private Integer sampleSize;

    @Schema(description = "Confidence score (0-1) based on sample size and variability", example = "0.85")
    private Double confidenceScore;

    @Schema(description = "User's typical quantity for this ingredient", example = "100.0")
    private Double typicalQuantity;

    @Schema(description = "User's typical unit", example = "g")
    private String typicalUnit;

    @Schema(description = "Last time this ingredient was used")
    private LocalDateTime lastUsed;

    @Schema(description = "When this ingredient was first learned")
    private LocalDateTime createdAt;

    /**
     * Convert entity to DTO
     */
    public static UserIngredientResponse fromEntity(UserIngredientLibrary ingredient) {
        return UserIngredientResponse.builder()
                .id(ingredient.getId())
                .ingredientName(ingredient.getIngredientName())
                .ingredientCategory(ingredient.getIngredientCategory())
                .normalizedName(ingredient.getNormalizedName())
                .avgCaloriesPer100g(ingredient.getAvgCaloriesPer100g())
                .avgProteinPer100g(ingredient.getAvgProteinPer100g())
                .avgFatPer100g(ingredient.getAvgFatPer100g())
                .avgCarbsPer100g(ingredient.getAvgCarbsPer100g())
                .stdDevCalories(ingredient.getStdDevCalories())
                .sampleSize(ingredient.getSampleSize())
                .confidenceScore(ingredient.getConfidenceScore())
                .typicalQuantity(ingredient.getTypicalQuantity())
                .typicalUnit(ingredient.getTypicalUnit())
                .lastUsed(ingredient.getLastUsed())
                .createdAt(ingredient.getCreatedAt())
                .build();
    }
}
