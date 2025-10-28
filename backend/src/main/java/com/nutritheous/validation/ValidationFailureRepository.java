package com.nutritheous.validation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AI validation failures
 */
@Repository
public interface ValidationFailureRepository extends JpaRepository<ValidationFailure, UUID> {

    /**
     * Find all validation failures for a specific meal
     */
    List<ValidationFailure> findByMealId(UUID mealId);

    /**
     * Find recent validation failures
     */
    @Query("SELECT v FROM ValidationFailure v WHERE v.failedAt >= :since ORDER BY v.failedAt DESC")
    List<ValidationFailure> findRecentFailures(LocalDateTime since);

    /**
     * Count validation failures in the last N days
     */
    @Query("SELECT COUNT(v) FROM ValidationFailure v WHERE v.failedAt >= :since")
    Long countRecentFailures(LocalDateTime since);

    /**
     * Get validation failure rate (failures / total meals) in last N days
     */
    @Query("SELECT COUNT(DISTINCT v.meal.id) FROM ValidationFailure v WHERE v.failedAt >= :since")
    Long countMealsWithFailures(LocalDateTime since);
}
