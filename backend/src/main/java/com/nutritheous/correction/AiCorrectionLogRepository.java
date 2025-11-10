package com.nutritheous.correction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AI correction logs with analytics queries.
 *
 * <p>Provides methods to:
 * - Query corrections by user, field, location, etc.
 * - Calculate accuracy metrics (average error, MAE, etc.)
 * - Analyze AI performance by various dimensions
 */
@Repository
public interface AiCorrectionLogRepository extends JpaRepository<AiCorrectionLog, UUID> {

    // ==================== Basic Queries ====================

    /**
     * Find all corrections for a specific meal
     */
    List<AiCorrectionLog> findByMealId(UUID mealId);

    /**
     * Find all corrections by a specific user
     */
    List<AiCorrectionLog> findByUserIdOrderByCorrectedAtDesc(UUID userId);

    /**
     * Find corrections for a specific field (e.g., "calories")
     */
    List<AiCorrectionLog> findByFieldName(String fieldName);

    /**
     * Find recent corrections (last N days)
     */
    @Query("SELECT c FROM AiCorrectionLog c WHERE c.correctedAt >= :since ORDER BY c.correctedAt DESC")
    List<AiCorrectionLog> findRecentCorrections(@Param("since") LocalDateTime since);

    // ==================== Accuracy Metrics ====================

    /**
     * Calculate overall AI accuracy (average absolute percent error) for a field
     *
     * @param fieldName The nutrition field (e.g., "calories", "protein_g")
     * @return Average absolute percent error (e.g., 15.5 means 15.5% average error)
     */
    @Query("SELECT AVG(ABS(c.percentError)) FROM AiCorrectionLog c WHERE c.fieldName = :fieldName")
    BigDecimal calculateAverageErrorByField(@Param("fieldName") String fieldName);

    /**
     * Calculate mean absolute error (MAE) for a field
     *
     * @param fieldName The nutrition field
     * @return Mean absolute error in the field's units
     */
    @Query("SELECT AVG(c.absoluteError) FROM AiCorrectionLog c WHERE c.fieldName = :fieldName")
    BigDecimal calculateMAEByField(@Param("fieldName") String fieldName);

    /**
     * Get correction count per field (how often each field is corrected)
     */
    @Query("SELECT c.fieldName, COUNT(c) FROM AiCorrectionLog c GROUP BY c.fieldName ORDER BY COUNT(c) DESC")
    List<Object[]> getCorrectionsCountByField();

    /**
     * Calculate AI accuracy by field with detailed stats
     *
     * Returns: [field_name, avg_abs_percent_error, correction_count, avg_absolute_error]
     */
    @Query("""
        SELECT c.fieldName,
               AVG(ABS(c.percentError)),
               COUNT(c),
               AVG(c.absoluteError)
        FROM AiCorrectionLog c
        GROUP BY c.fieldName
        ORDER BY AVG(ABS(c.percentError)) DESC
    """)
    List<Object[]> getAccuracyStatsByField();

    // ==================== Location-Based Analysis ====================

    /**
     * Calculate AI accuracy by location type (restaurant vs home)
     *
     * Returns: [location_type, field_name, avg_abs_percent_error, correction_count]
     */
    @Query("""
        SELECT c.locationType,
               c.fieldName,
               AVG(ABS(c.percentError)),
               COUNT(c)
        FROM AiCorrectionLog c
        WHERE c.locationType IS NOT NULL
        GROUP BY c.locationType, c.fieldName
        ORDER BY c.locationType, AVG(ABS(c.percentError)) DESC
    """)
    List<Object[]> getAccuracyByLocationType();

    /**
     * Calculate AI accuracy for a specific location type and field
     */
    @Query("""
        SELECT AVG(ABS(c.percentError))
        FROM AiCorrectionLog c
        WHERE c.locationType = :locationType AND c.fieldName = :fieldName
    """)
    BigDecimal calculateErrorByLocationAndField(
            @Param("locationType") String locationType,
            @Param("fieldName") String fieldName
    );

    // ==================== Confidence Calibration ====================

    /**
     * Analyze AI accuracy by confidence score buckets
     *
     * Returns: [confidence_bucket, avg_abs_percent_error, correction_count]
     * Confidence buckets: 0.0-0.1, 0.1-0.2, ..., 0.9-1.0
     */
    @Query("""
        SELECT FLOOR(c.confidenceScore * 10) / 10,
               AVG(ABS(c.percentError)),
               COUNT(c)
        FROM AiCorrectionLog c
        WHERE c.confidenceScore IS NOT NULL
        GROUP BY FLOOR(c.confidenceScore * 10) / 10
        ORDER BY FLOOR(c.confidenceScore * 10) / 10
    """)
    List<Object[]> getAccuracyByConfidenceScore();

    /**
     * Check if high confidence correlates with low error (confidence calibration)
     */
    @Query("""
        SELECT AVG(ABS(c.percentError))
        FROM AiCorrectionLog c
        WHERE c.confidenceScore >= :minConfidence
    """)
    BigDecimal calculateErrorForHighConfidence(@Param("minConfidence") BigDecimal minConfidence);

    // ==================== User-Specific Analysis ====================

    /**
     * Calculate AI accuracy for a specific user
     */
    @Query("""
        SELECT c.fieldName,
               AVG(ABS(c.percentError)),
               COUNT(c)
        FROM AiCorrectionLog c
        WHERE c.user.id = :userId
        GROUP BY c.fieldName
        ORDER BY AVG(ABS(c.percentError)) DESC
    """)
    List<Object[]> getUserAccuracyStats(@Param("userId") UUID userId);

    /**
     * Get user's total correction count
     */
    @Query("SELECT COUNT(c) FROM AiCorrectionLog c WHERE c.user.id = :userId")
    Long countUserCorrections(@Param("userId") UUID userId);

    /**
     * Check how often a user corrects meals (correction rate)
     *
     * @param userId User ID
     * @param since Start date
     * @return Number of unique meals that were corrected
     */
    @Query("""
        SELECT COUNT(DISTINCT c.meal.id)
        FROM AiCorrectionLog c
        WHERE c.user.id = :userId AND c.correctedAt >= :since
    """)
    Long countCorrectedMeals(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    // ==================== Temporal Analysis ====================

    /**
     * Get correction trend over time (corrections per day)
     *
     * Returns: [date, correction_count]
     */
    @Query("""
        SELECT CAST(c.correctedAt AS date),
               COUNT(c)
        FROM AiCorrectionLog c
        WHERE c.correctedAt >= :since
        GROUP BY CAST(c.correctedAt AS date)
        ORDER BY CAST(c.correctedAt AS date)
    """)
    List<Object[]> getCorrectionTrend(@Param("since") LocalDateTime since);

    /**
     * Calculate AI accuracy improvement over time (is AI getting better?)
     *
     * Returns: [week_start, avg_abs_percent_error]
     */
    @Query("""
        SELECT DATE_TRUNC('week', c.correctedAt),
               AVG(ABS(c.percentError))
        FROM AiCorrectionLog c
        WHERE c.correctedAt >= :since
        GROUP BY DATE_TRUNC('week', c.correctedAt)
        ORDER BY DATE_TRUNC('week', c.correctedAt)
    """)
    List<Object[]> getAccuracyTrendByWeek(@Param("since") LocalDateTime since);

    // ==================== Systematic Bias Detection ====================

    /**
     * Detect systematic bias (does AI consistently under/overestimate?)
     *
     * Returns: [field_name, avg_percent_error (NOT absolute)]
     * - Positive = AI underestimates
     * - Negative = AI overestimates
     */
    @Query("""
        SELECT c.fieldName,
               AVG(c.percentError)
        FROM AiCorrectionLog c
        GROUP BY c.fieldName
        ORDER BY ABS(AVG(c.percentError)) DESC
    """)
    List<Object[]> detectSystematicBias();

    /**
     * Find most problematic meals (meals with highest total error across all fields)
     */
    @Query("""
        SELECT c.meal.id,
               c.meal.description,
               SUM(ABS(c.percentError)),
               COUNT(c)
        FROM AiCorrectionLog c
        GROUP BY c.meal.id, c.meal.description
        ORDER BY SUM(ABS(c.percentError)) DESC
    """)
    List<Object[]> findMostProblematicMeals();
}
