package com.nutritheous.ingredient;

import com.nutritheous.meal.Meal;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an individual ingredient within a meal.
 * Enables decomposition of meals into constituent ingredients for better learning.
 */
@Entity
@Table(name = "meal_ingredients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    /**
     * Ingredient name (e.g., "idli", "coconut chutney", "sambar")
     */
    @Column(name = "ingredient_name", nullable = false, length = 200)
    private String ingredientName;

    /**
     * Ingredient category for grouping and display
     * Values: grain, protein, vegetable, fat, dairy, spice, condiment, beverage
     */
    @Column(name = "ingredient_category", length = 50)
    private String ingredientCategory;

    /**
     * Quantity of ingredient
     */
    @Column(nullable = false)
    private Double quantity;

    /**
     * Unit of measurement
     * Common values: g, ml, piece, tsp, tbsp, cup
     */
    @Column(nullable = false, length = 20)
    private String unit;

    // Nutrition per this serving (not per 100g)
    @Column
    private Double calories;

    @Column(name = "protein_g")
    private Double proteinG;

    @Column(name = "fat_g")
    private Double fatG;

    @Column(name = "saturated_fat_g")
    private Double saturatedFatG;

    @Column(name = "carbohydrates_g")
    private Double carbohydratesG;

    @Column(name = "fiber_g")
    private Double fiberG;

    @Column(name = "sugar_g")
    private Double sugarG;

    @Column(name = "sodium_mg")
    private Double sodiumMg;

    /**
     * True if AI extracted this ingredient
     */
    @Column(name = "is_ai_extracted")
    @Builder.Default
    private Boolean isAiExtracted = true;

    /**
     * True if user manually corrected this ingredient
     */
    @Column(name = "is_user_corrected")
    @Builder.Default
    private Boolean isUserCorrected = false;

    /**
     * AI confidence score when extracting this ingredient (0-1)
     */
    @Column(name = "ai_confidence")
    private Double aiConfidence;

    /**
     * Display order in ingredient list (0 = first)
     */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
