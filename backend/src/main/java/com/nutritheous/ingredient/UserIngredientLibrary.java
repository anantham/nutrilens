package com.nutritheous.ingredient;

import com.nutritheous.auth.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Personal ingredient database learned from user corrections over time.
 * Stores average nutrition values per 100g for each ingredient.
 */
@Entity
@Table(name = "user_ingredient_library",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "normalized_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIngredientLibrary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Original ingredient name as entered by user or AI
     */
    @Column(name = "ingredient_name", nullable = false, length = 200)
    private String ingredientName;

    /**
     * Ingredient category
     */
    @Column(name = "ingredient_category", length = 50)
    private String ingredientCategory;

    /**
     * Normalized name for matching (lowercase, trimmed)
     */
    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    // Average nutrition per 100g (learned from user corrections)
    @Column(name = "avg_calories_per_100g")
    private Double avgCaloriesPer100g;

    @Column(name = "avg_protein_per_100g")
    private Double avgProteinPer100g;

    @Column(name = "avg_fat_per_100g")
    private Double avgFatPer100g;

    @Column(name = "avg_carbs_per_100g")
    private Double avgCarbsPer100g;

    /**
     * Standard deviation of calories (measures variability)
     */
    @Column(name = "std_dev_calories")
    private Double stdDevCalories;

    /**
     * Number of times user has corrected this ingredient
     */
    @Column(name = "sample_size")
    @Builder.Default
    private Integer sampleSize = 1;

    /**
     * Confidence score (0-1) based on sample size and variability
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /**
     * User's typical quantity for this ingredient
     */
    @Column(name = "typical_quantity")
    private Double typicalQuantity;

    /**
     * User's typical unit (g, piece, ml, etc.)
     */
    @Column(name = "typical_unit", length = 20)
    private String typicalUnit;

    @Column(name = "last_used")
    private LocalDateTime lastUsed;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
