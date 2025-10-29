package com.nutritheous.ingredient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for meal ingredients
 */
@Repository
public interface MealIngredientRepository extends JpaRepository<MealIngredient, UUID> {

    /**
     * Find all ingredients for a specific meal
     */
    List<MealIngredient> findByMealIdOrderByDisplayOrderAsc(UUID mealId);

    /**
     * Find all ingredients for a meal
     */
    List<MealIngredient> findByMealId(UUID mealId);

    /**
     * Delete all ingredients for a meal
     */
    void deleteByMealId(UUID mealId);

    /**
     * Count ingredients for a meal
     */
    Long countByMealId(UUID mealId);

    /**
     * Find meals with a specific ingredient name (for learning patterns)
     */
    @Query("SELECT mi FROM MealIngredient mi WHERE mi.meal.user.id = :userId " +
           "AND LOWER(mi.ingredientName) LIKE LOWER(CONCAT('%', :ingredientName, '%')) " +
           "ORDER BY mi.createdAt DESC")
    List<MealIngredient> findByUserIdAndIngredientNameContaining(
            @Param("userId") UUID userId,
            @Param("ingredientName") String ingredientName
    );

    /**
     * Find user-corrected ingredients (for learning)
     */
    @Query("SELECT mi FROM MealIngredient mi WHERE mi.meal.user.id = :userId " +
           "AND mi.isUserCorrected = true ORDER BY mi.createdAt DESC")
    List<MealIngredient> findUserCorrectedIngredients(@Param("userId") UUID userId);
}
