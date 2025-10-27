package com.nutritheous.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AI correction logs - enables analytics queries on AI accuracy
 */
@Repository
public interface AiCorrectionLogRepository extends JpaRepository<AiCorrectionLog, UUID> {

    /**
     * Find all corrections for a specific user
     */
    List<AiCorrectionLog> findByUserId(UUID userId);

    /**
     * Find all corrections for a specific meal
     */
    List<AiCorrectionLog> findByMealId(UUID mealId);

    /**
     * Find all corrections for a specific field (e.g., "calories", "protein_g")
     */
    List<AiCorrectionLog> findByFieldName(String fieldName);

    /**
     * Count total corrections by field name
     */
    @Query("SELECT c.fieldName, COUNT(c) as count FROM AiCorrectionLog c GROUP BY c.fieldName")
    List<Object[]> countCorrectionsByField();

    /**
     * Get average absolute percent error by field name
     */
    @Query("SELECT c.fieldName, AVG(ABS(c.percentError)) as avgError, COUNT(c) as count " +
           "FROM AiCorrectionLog c " +
           "WHERE c.percentError IS NOT NULL " +
           "GROUP BY c.fieldName " +
           "ORDER BY AVG(ABS(c.percentError)) DESC")
    List<Object[]> getAverageErrorByField();

    /**
     * Get average absolute percent error by location type
     */
    @Query("SELECT c.locationType, c.fieldName, AVG(ABS(c.percentError)) as avgError, COUNT(c) as count " +
           "FROM AiCorrectionLog c " +
           "WHERE c.percentError IS NOT NULL AND c.locationType IS NOT NULL " +
           "GROUP BY c.locationType, c.fieldName " +
           "ORDER BY AVG(ABS(c.percentError)) DESC")
    List<Object[]> getAverageErrorByLocationAndField();

    /**
     * Get average absolute percent error by confidence score bucket
     */
    @Query("SELECT FLOOR(c.confidenceScore * 10) / 10 as confidenceBucket, " +
           "AVG(ABS(c.percentError)) as avgError, COUNT(c) as count " +
           "FROM AiCorrectionLog c " +
           "WHERE c.percentError IS NOT NULL AND c.confidenceScore IS NOT NULL " +
           "GROUP BY FLOOR(c.confidenceScore * 10) / 10 " +
           "ORDER BY FLOOR(c.confidenceScore * 10) / 10")
    List<Object[]> getAverageErrorByConfidence();

    /**
     * Find corrections within a date range
     */
    @Query("SELECT c FROM AiCorrectionLog c WHERE c.correctedAt BETWEEN :startDate AND :endDate")
    List<AiCorrectionLog> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Get overall AI accuracy statistics
     */
    @Query("SELECT " +
           "COUNT(c) as totalCorrections, " +
           "AVG(ABS(c.percentError)) as avgError, " +
           "STDDEV(c.percentError) as errorStdDev " +
           "FROM AiCorrectionLog c " +
           "WHERE c.percentError IS NOT NULL")
    Object[] getOverallAccuracyStats();

    /**
     * Find corrections where AI significantly underestimated (error > threshold)
     */
    @Query("SELECT c FROM AiCorrectionLog c WHERE c.percentError > :threshold ORDER BY c.percentError DESC")
    List<AiCorrectionLog> findSignificantUnderestimates(@Param("threshold") Double threshold);

    /**
     * Find corrections where AI significantly overestimated (error < -threshold)
     */
    @Query("SELECT c FROM AiCorrectionLog c WHERE c.percentError < :threshold ORDER BY c.percentError ASC")
    List<AiCorrectionLog> findSignificantOverestimates(@Param("threshold") Double threshold);
}
