package com.nutritheous.meal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MealRepository extends JpaRepository<Meal, UUID> {

    // ==================== Non-Paginated Methods (Legacy) ====================

    List<Meal> findByUserIdOrderByMealTimeDesc(UUID userId);

    List<Meal> findByUserIdAndMealTimeBetweenOrderByMealTimeDesc(
            UUID userId, LocalDateTime startTime, LocalDateTime endTime);

    List<Meal> findByUserIdAndMealTypeOrderByMealTimeDesc(UUID userId, Meal.MealType mealType);

    List<Meal> findByAnalysisStatus(Meal.AnalysisStatus status);

    // ==================== Paginated Methods (Recommended) ====================

    /**
     * Get all meals for a user with pagination.
     * Results are ordered by meal_time DESC (newest first).
     *
     * @param userId User ID
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of meals
     */
    Page<Meal> findByUserId(UUID userId, Pageable pageable);

    /**
     * Get meals for a user within a date range with pagination.
     *
     * @param userId User ID
     * @param startTime Start of date range
     * @param endTime End of date range
     * @param pageable Pagination parameters
     * @return Page of meals within date range
     */
    Page<Meal> findByUserIdAndMealTimeBetween(
            UUID userId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * Get meals for a user filtered by meal type with pagination.
     *
     * @param userId User ID
     * @param mealType Meal type (BREAKFAST, LUNCH, DINNER, SNACK)
     * @param pageable Pagination parameters
     * @return Page of meals of the specified type
     */
    Page<Meal> findByUserIdAndMealType(UUID userId, Meal.MealType mealType, Pageable pageable);

    /**
     * Count total meals for a user (useful for statistics).
     *
     * @param userId User ID
     * @return Total number of meals
     */
    long countByUserId(UUID userId);
}
