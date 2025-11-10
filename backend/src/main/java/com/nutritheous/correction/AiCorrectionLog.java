package com.nutritheous.correction;

import com.nutritheous.auth.User;
import com.nutritheous.meal.Meal;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for tracking user corrections to AI-generated nutrition data.
 *
 * <p>This table serves as telemetry to measure AI accuracy and detect systematic biases.
 * Each record represents a single field correction (e.g., user changed calories from 500 to 650).
 *
 * <p>Use cases:
 * - Calculate AI accuracy by field (e.g., "protein estimates are 85% accurate")
 * - Detect location-based biases (e.g., "AI underestimates restaurant meals by 18%")
 * - Measure confidence calibration (e.g., "high confidence meals have 12% avg error")
 * - Track improvement over time
 *
 * @see com.nutritheous.meal.MealService#updateMeal(UUID, UUID, com.nutritheous.meal.dto.MealUpdateRequest)
 */
@Entity
@Table(name = "ai_correction_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCorrectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Name of the field that was corrected.
     * Examples: "calories", "protein_g", "fat_g", "carbohydrates_g", "fiber_g", "sodium_mg"
     */
    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    /**
     * AI's original prediction for this field
     */
    @Column(name = "ai_value", precision = 10, scale = 2)
    private BigDecimal aiValue;

    /**
     * User's corrected value
     */
    @Column(name = "user_value", precision = 10, scale = 2)
    private BigDecimal userValue;

    /**
     * Percentage error: ((userValue - aiValue) / userValue) * 100
     * - Positive = AI underestimated
     * - Negative = AI overestimated
     * - Example: AI said 500 cal, user corrected to 650 cal = -23.08% (AI was 23% low)
     */
    @Column(name = "percent_error", precision = 10, scale = 2)
    private BigDecimal percentError;

    /**
     * Absolute error: ABS(userValue - aiValue)
     * Useful for calculating mean absolute error (MAE)
     */
    @Column(name = "absolute_error", precision = 10, scale = 2)
    private BigDecimal absoluteError;

    // Context fields for analysis

    /**
     * AI's confidence score when it made the prediction (0.0 - 1.0)
     * Used to measure confidence calibration (high confidence should = low error)
     */
    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    /**
     * Location type where meal was consumed (from meals.location_place_type)
     * Examples: "restaurant", "home", "cafe", null
     * Used to detect location-based biases
     */
    @Column(name = "location_type", length = 50)
    private String locationType;

    /**
     * Place name where meal was consumed (from meals.location_place_name)
     * Examples: "Chipotle Mexican Grill", "Home", null
     */
    @Column(name = "location_place_name", length = 255)
    private String locationPlaceName;

    /**
     * Meal type (from meals.meal_type)
     * Examples: "breakfast", "lunch", "dinner", "snack"
     * Used to detect meal-type-specific biases
     */
    @Column(name = "meal_type", length = 20)
    private String mealType;

    /**
     * Meal description (from meals.description)
     * Stored for pattern analysis (e.g., "AI always underestimates pizza")
     */
    @Column(name = "meal_description", columnDefinition = "TEXT")
    private String mealDescription;

    // Timestamps

    /**
     * When AI made the original prediction (from meals.created_at)
     */
    @Column(name = "ai_analyzed_at")
    private LocalDateTime aiAnalyzedAt;

    /**
     * When user made the correction (defaults to now)
     */
    @Column(name = "corrected_at", nullable = false)
    private LocalDateTime correctedAt;

    /**
     * Calculate and set percent_error and absolute_error from ai_value and user_value
     */
    public void calculateErrors() {
        if (aiValue != null && userValue != null && userValue.compareTo(BigDecimal.ZERO) != 0) {
            // Percent error: ((user - ai) / user) * 100
            BigDecimal difference = userValue.subtract(aiValue);
            this.percentError = difference.divide(userValue, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Absolute error: |user - ai|
            this.absoluteError = difference.abs();
        }
    }

    /**
     * Builder method to automatically calculate errors
     */
    public static class AiCorrectionLogBuilder {
        public AiCorrectionLog build() {
            AiCorrectionLog log = new AiCorrectionLog(
                    id, meal, user, fieldName, aiValue, userValue,
                    percentError, absoluteError, confidenceScore,
                    locationType, locationPlaceName, mealType, mealDescription,
                    aiAnalyzedAt, correctedAt
            );
            // Auto-calculate errors if not manually set
            if (log.percentError == null || log.absoluteError == null) {
                log.calculateErrors();
            }
            // Default correctedAt to now if not set
            if (log.correctedAt == null) {
                log.correctedAt = LocalDateTime.now();
            }
            return log;
        }
    }
}
