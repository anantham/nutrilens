package com.nutritheous.correction;

import com.nutritheous.correction.dto.FieldAccuracyStats;
import com.nutritheous.correction.dto.LocationAccuracyStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for analyzing AI correction data and generating accuracy metrics.
 *
 * <p>Provides high-level analytics methods for:
 * - Overall AI accuracy by field
 * - Location-based accuracy (restaurant vs home)
 * - User-specific accuracy
 * - Confidence calibration
 * - Temporal trends
 */
@Service
@Slf4j
public class CorrectionAnalyticsService {

    private final AiCorrectionLogRepository correctionLogRepository;

    public CorrectionAnalyticsService(AiCorrectionLogRepository correctionLogRepository) {
        this.correctionLogRepository = correctionLogRepository;
    }

    /**
     * Get overall AI accuracy statistics by field
     *
     * @return List of field accuracy stats, ordered by worst to best
     */
    public List<FieldAccuracyStats> getOverallAccuracy() {
        List<Object[]> results = correctionLogRepository.getAccuracyStatsByField();

        return results.stream()
                .map(row -> FieldAccuracyStats.builder()
                        .fieldName((String) row[0])
                        .avgAbsPercentError((BigDecimal) row[1])
                        .correctionCount(((Number) row[2]).longValue())
                        .meanAbsoluteError((BigDecimal) row[3])
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get AI accuracy by location type (restaurant vs home)
     *
     * @return List of location-based accuracy stats
     */
    public List<LocationAccuracyStats> getAccuracyByLocation() {
        List<Object[]> results = correctionLogRepository.getAccuracyByLocationType();

        return results.stream()
                .map(row -> LocationAccuracyStats.builder()
                        .locationType((String) row[0])
                        .fieldName((String) row[1])
                        .avgAbsPercentError((BigDecimal) row[2])
                        .correctionCount(((Number) row[3]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get user-specific AI accuracy
     *
     * @param userId User ID
     * @return List of field accuracy stats for this user
     */
    public List<FieldAccuracyStats> getUserAccuracy(UUID userId) {
        List<Object[]> results = correctionLogRepository.getUserAccuracyStats(userId);

        return results.stream()
                .map(row -> FieldAccuracyStats.builder()
                        .fieldName((String) row[0])
                        .avgAbsPercentError((BigDecimal) row[1])
                        .correctionCount(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Calculate correction rate (what % of meals are corrected)
     *
     * @param userId User ID
     * @param daysSince Number of days to look back
     * @return Correction rate as a percentage (e.g., 45.5 = 45.5%)
     */
    public BigDecimal getCorrectionRate(UUID userId, int daysSince) {
        LocalDateTime since = LocalDateTime.now().minusDays(daysSince);

        // Get total corrected meals
        Long correctedMeals = correctionLogRepository.countCorrectedMeals(userId, since);

        if (correctedMeals == null || correctedMeals == 0) {
            return BigDecimal.ZERO;
        }

        // TODO: Get total meals from MealRepository
        // For now, return just the count
        return BigDecimal.valueOf(correctedMeals);
    }

    /**
     * Detect systematic bias in AI predictions
     *
     * @return List of [field_name, bias] where bias is average percent error (not absolute)
     *         Positive = AI underestimates, Negative = AI overestimates
     */
    public List<FieldAccuracyStats> detectSystematicBias() {
        List<Object[]> results = correctionLogRepository.detectSystematicBias();

        return results.stream()
                .map(row -> FieldAccuracyStats.builder()
                        .fieldName((String) row[0])
                        .bias((BigDecimal) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get recent corrections (last N days)
     *
     * @param daysSince Number of days to look back
     * @return List of correction logs
     */
    public List<AiCorrectionLog> getRecentCorrections(int daysSince) {
        LocalDateTime since = LocalDateTime.now().minusDays(daysSince);
        return correctionLogRepository.findRecentCorrections(since);
    }

    /**
     * Get accuracy for a specific field and location type
     *
     * @param locationType Location type (e.g., "restaurant", "home")
     * @param fieldName Field name (e.g., "calories")
     * @return Average absolute percent error
     */
    public BigDecimal getAccuracyForLocationAndField(String locationType, String fieldName) {
        return correctionLogRepository.calculateErrorByLocationAndField(locationType, fieldName);
    }

    /**
     * Check confidence calibration (does high confidence = low error?)
     *
     * @param minConfidence Minimum confidence threshold (e.g., 0.8)
     * @return Average error for high-confidence predictions
     */
    public BigDecimal getHighConfidenceAccuracy(BigDecimal minConfidence) {
        return correctionLogRepository.calculateErrorForHighConfidence(minConfidence);
    }

    /**
     * Get summary statistics for a field
     *
     * @param fieldName Field name (e.g., "calories")
     * @return Accuracy stats for this field
     */
    public FieldAccuracyStats getFieldSummary(String fieldName) {
        BigDecimal avgError = correctionLogRepository.calculateAverageErrorByField(fieldName);
        BigDecimal mae = correctionLogRepository.calculateMAEByField(fieldName);

        // Get correction count from the list of fields
        Long correctionCount = correctionLogRepository.findByFieldName(fieldName).stream().count();

        return FieldAccuracyStats.builder()
                .fieldName(fieldName)
                .avgAbsPercentError(avgError)
                .meanAbsoluteError(mae)
                .correctionCount(correctionCount)
                .build();
    }

    /**
     * Generate a comprehensive accuracy report
     *
     * @return Human-readable report of AI accuracy
     */
    public String generateAccuracyReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== AI Accuracy Report ===\n\n");

        // Overall accuracy by field
        List<FieldAccuracyStats> overallStats = getOverallAccuracy();
        report.append("Overall Accuracy by Field:\n");
        for (FieldAccuracyStats stat : overallStats) {
            report.append(String.format("  %s: %.1f%% error (n=%d, MAE=%.1f)\n",
                    stat.getFieldName(),
                    stat.getAvgAbsPercentError(),
                    stat.getCorrectionCount(),
                    stat.getMeanAbsoluteError()));
        }

        // Location-based accuracy
        report.append("\nAccuracy by Location:\n");
        List<LocationAccuracyStats> locationStats = getAccuracyByLocation();
        for (LocationAccuracyStats stat : locationStats) {
            report.append(String.format("  %s - %s: %.1f%% error (n=%d)\n",
                    stat.getLocationType(),
                    stat.getFieldName(),
                    stat.getAvgAbsPercentError(),
                    stat.getCorrectionCount()));
        }

        // Systematic bias
        report.append("\nSystematic Bias Detection:\n");
        List<FieldAccuracyStats> biases = detectSystematicBias();
        for (FieldAccuracyStats bias : biases) {
            String direction = bias.getBias().compareTo(BigDecimal.ZERO) > 0 ? "underestimates" : "overestimates";
            report.append(String.format("  %s: AI %s by %.1f%%\n",
                    bias.getFieldName(),
                    direction,
                    bias.getBias().abs()));
        }

        return report.toString();
    }
}
