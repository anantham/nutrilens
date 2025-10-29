package com.nutritheous.ingredient.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating or updating an ingredient
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create or update a meal ingredient")
public class IngredientRequest {

    @Schema(description = "Ingredient ID (null for new ingredients)")
    private UUID id;

    @NotBlank(message = "Ingredient name is required")
    @Schema(description = "Name of the ingredient (e.g., 'idli', 'sambar')", example = "idli")
    private String ingredientName;

    @Schema(description = "Category for grouping (grain, protein, vegetable, fat, dairy, spice, condiment, beverage)",
            example = "grain")
    private String ingredientCategory;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    @Schema(description = "Quantity of ingredient", example = "100.0")
    private Double quantity;

    @NotBlank(message = "Unit is required")
    @Schema(description = "Unit of measurement (g, ml, piece, tsp, tbsp, cup)", example = "g")
    private String unit;

    // Nutrition per this serving
    @Schema(description = "Calories for this serving", example = "120.0")
    private Double calories;

    @Schema(description = "Protein in grams", example = "4.5")
    private Double proteinG;

    @Schema(description = "Fat in grams", example = "1.2")
    private Double fatG;

    @Schema(description = "Saturated fat in grams", example = "0.3")
    private Double saturatedFatG;

    @Schema(description = "Carbohydrates in grams", example = "22.0")
    private Double carbohydratesG;

    @Schema(description = "Fiber in grams", example = "2.5")
    private Double fiberG;

    @Schema(description = "Sugar in grams", example = "0.5")
    private Double sugarG;

    @Schema(description = "Sodium in milligrams", example = "150.0")
    private Double sodiumMg;

    @Schema(description = "Display order in ingredient list (0 = first)", example = "0")
    private Integer displayOrder;
}
