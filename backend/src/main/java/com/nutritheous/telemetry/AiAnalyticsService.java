package com.nutritheous.telemetry;

import com.nutritheous.meal.MealRepository;
import com.nutritheous.telemetry.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for analyzing AI accuracy and correction patterns
 */
@Service
public class AiAnalyticsService {

    @Autowired
    private AiCorrectionLogRepository correctionLogRepository;

    @Autowired
    private MealRepository mealRepository;

    /**
     * Get overall AI accuracy statistics
     */
    public AiAccuracyStats getOverallAccuracyStats(UUID userId) {
        // Get corrections for this user
        List<AiCorrectionLog> corrections = correctionLogRepository.findByUserId(userId);

        if (corrections.isEmpty()) {
            return AiAccuracyStats.builder()
                    .totalCorrections(0L)
                    .averagePercentError(0.0)
                    .errorStdDev(0.0)
                    .uniqueMealsEdited(0L)
                    .editRate(0.0)
                    .build();
        }

        // Calculate average absolute percent error
        double avgError = corrections.stream()
                .filter(c -> c.getPercentError() != null)
                .mapToDouble(c -> Math.abs(c.getPercentError()))
                .average()
                .orElse(0.0);

        // Calculate standard deviation
        double mean = corrections.stream()
                .filter(c -> c.getPercentError() != null)
                .mapToDouble(AiCorrectionLog::getPercentError)
                .average()
                .orElse(0.0);

        double variance = corrections.stream()
                .filter(c -> c.getPercentError() != null)
                .mapToDouble(c -> Math.pow(c.getPercentError() - mean, 2))
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);

        // Count unique meals that have been edited
        long uniqueMeals = corrections.stream()
                .map(c -> c.getMeal().getId())
                .distinct()
                .count();

        // Calculate edit rate (% of all user meals that have corrections)
        long totalMeals = mealRepository.countByUserId(userId);
        double editRate = totalMeals > 0 ? (uniqueMeals * 100.0 / totalMeals) : 0.0;

        return AiAccuracyStats.builder()
                .totalCorrections((long) corrections.size())
                .averagePercentError(avgError)
                .errorStdDev(stdDev)
                .uniqueMealsEdited(uniqueMeals)
                .editRate(editRate)
                .build();
    }

    /**
     * Get accuracy statistics by field (e.g., calories, protein, etc.)
     */
    public List<FieldAccuracy> getAccuracyByField(UUID userId) {
        List<AiCorrectionLog> corrections = correctionLogRepository.findByUserId(userId);

        // Group by field name
        return corrections.stream()
                .filter(c -> c.getPercentError() != null)
                .collect(Collectors.groupingBy(AiCorrectionLog::getFieldName))
                .entrySet().stream()
                .map(entry -> {
                    String fieldName = entry.getKey();
                    List<AiCorrectionLog> fieldCorrections = entry.getValue();

                    double avgError = fieldCorrections.stream()
                            .mapToDouble(c -> Math.abs(c.getPercentError()))
                            .average()
                            .orElse(0.0);

                    double minError = fieldCorrections.stream()
                            .mapToDouble(AiCorrectionLog::getPercentError)
                            .min()
                            .orElse(0.0);

                    double maxError = fieldCorrections.stream()
                            .mapToDouble(AiCorrectionLog::getPercentError)
                            .max()
                            .orElse(0.0);

                    return FieldAccuracy.builder()
                            .fieldName(fieldName)
                            .averagePercentError(avgError)
                            .correctionCount((long) fieldCorrections.size())
                            .minError(minError)
                            .maxError(maxError)
                            .build();
                })
                .sorted((a, b) -> Double.compare(b.getAveragePercentError(), a.getAveragePercentError()))
                .collect(Collectors.toList());
    }

    /**
     * Get accuracy statistics by location type and field
     */
    public List<LocationFieldAccuracy> getAccuracyByLocation(UUID userId) {
        List<AiCorrectionLog> corrections = correctionLogRepository.findByUserId(userId);

        List<LocationFieldAccuracy> results = new ArrayList<>();

        // Group by location type and field name
        corrections.stream()
                .filter(c -> c.getPercentError() != null && c.getLocationType() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getLocationType() + "||" + c.getFieldName()
                ))
                .forEach((key, logs) -> {
                    String[] parts = key.split("\\|\\|");
                    String locationType = parts[0];
                    String fieldName = parts[1];

                    double avgError = logs.stream()
                            .mapToDouble(c -> Math.abs(c.getPercentError()))
                            .average()
                            .orElse(0.0);

                    results.add(LocationFieldAccuracy.builder()
                            .locationType(locationType)
                            .fieldName(fieldName)
                            .averagePercentError(avgError)
                            .correctionCount((long) logs.size())
                            .build());
                });

        return results.stream()
                .sorted((a, b) -> Double.compare(b.getAveragePercentError(), a.getAveragePercentError()))
                .collect(Collectors.toList());
    }

    /**
     * Get confidence calibration data (does confidence correlate with accuracy?)
     */
    public List<ConfidenceCalibration> getConfidenceCalibration(UUID userId) {
        List<AiCorrectionLog> corrections = correctionLogRepository.findByUserId(userId);

        // Group by confidence bucket (0.1 increments)
        return corrections.stream()
                .filter(c -> c.getPercentError() != null && c.getConfidenceScore() != null)
                .collect(Collectors.groupingBy(
                        c -> Math.floor(c.getConfidenceScore() * 10) / 10.0
                ))
                .entrySet().stream()
                .map(entry -> {
                    double bucket = entry.getKey();
                    List<AiCorrectionLog> bucketCorrections = entry.getValue();

                    double avgError = bucketCorrections.stream()
                            .mapToDouble(c -> Math.abs(c.getPercentError()))
                            .average()
                            .orElse(0.0);

                    return ConfidenceCalibration.builder()
                            .confidenceBucket(bucket)
                            .averagePercentError(avgError)
                            .correctionCount((long) bucketCorrections.size())
                            .build();
                })
                .sorted((a, b) -> Double.compare(a.getConfidenceBucket(), b.getConfidenceBucket()))
                .collect(Collectors.toList());
    }

    /**
     * Get significant errors (outliers that require attention)
     */
    public List<SignificantError> getSignificantErrors(UUID userId, Double threshold) {
        List<AiCorrectionLog> corrections = correctionLogRepository.findByUserId(userId);

        // Find corrections where absolute error exceeds threshold
        return corrections.stream()
                .filter(c -> c.getPercentError() != null)
                .filter(c -> Math.abs(c.getPercentError()) >= threshold)
                .map(c -> SignificantError.builder()
                        .id(c.getId())
                        .mealId(c.getMeal().getId())
                        .fieldName(c.getFieldName())
                        .aiValue(c.getAiValue())
                        .userValue(c.getUserValue())
                        .percentError(c.getPercentError())
                        .confidence(c.getConfidenceScore())
                        .locationType(c.getLocationType())
                        .mealDescription(c.getMealDescription())
                        .correctedAt(c.getCorrectedAt())
                        .build())
                .sorted((a, b) -> Double.compare(
                        Math.abs(b.getPercentError()),
                        Math.abs(a.getPercentError())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get corrections within a date range
     */
    public List<AiCorrectionLog> getCorrectionsByDateRange(
            UUID userId,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        // First get all corrections for user, then filter by date
        return correctionLogRepository.findByUserId(userId).stream()
                .filter(c -> c.getCorrectedAt().isAfter(startDate) && c.getCorrectedAt().isBefore(endDate))
                .collect(Collectors.toList());
    }
}
