package com.nutritheous.ingredient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing statistics about user's learned ingredient library.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientLibraryStatsResponse {

    /**
     * Total number of ingredients in user's library
     */
    private Integer totalIngredients;

    /**
     * Number of high-confidence ingredients (confidence >= 0.7)
     */
    private Integer highConfidenceCount;

    /**
     * Average confidence score across all ingredients
     */
    private Double avgConfidenceScore;

    /**
     * Total number of corrections (sum of all sample sizes)
     */
    private Integer totalCorrections;

    /**
     * Ingredient with most observations
     */
    private TopIngredient mostFrequentIngredient;

    /**
     * Ingredient with highest confidence
     */
    private TopIngredient highestConfidenceIngredient;

    /**
     * Ingredient with most recent usage
     */
    private TopIngredient mostRecentIngredient;

    /**
     * Nested class for top ingredient details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopIngredient {
        private String name;
        private String category;
        private Integer sampleSize;
        private Double confidenceScore;
        private String lastUsed;  // ISO 8601 timestamp
    }
}
