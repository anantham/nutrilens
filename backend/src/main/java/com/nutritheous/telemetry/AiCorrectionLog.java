package com.nutritheous.telemetry;

import com.nutritheous.auth.User;
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
 * Entity for tracking user corrections to AI-generated nutrition values.
 * Used for measuring AI accuracy and identifying systematic biases.
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
     * Name of the field that was corrected (e.g., "calories", "protein_g", "fat_g")
     */
    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    /**
     * Original value from AI analysis
     */
    @Column(name = "ai_value")
    private Double aiValue;

    /**
     * User-corrected value
     */
    @Column(name = "user_value")
    private Double userValue;

    /**
     * Percentage error: (user_value - ai_value) / user_value * 100
     * Positive = AI underestimated, Negative = AI overestimated
     */
    @Column(name = "percent_error")
    private Double percentError;

    // Context at time of correction

    /**
     * AI's confidence score (0.0-1.0) when analysis was performed
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /**
     * Type of location where meal was eaten (restaurant, home, cafe, etc.)
     */
    @Column(name = "location_type", length = 50)
    private String locationType;

    /**
     * Whether meal was at a restaurant
     */
    @Column(name = "location_is_restaurant")
    private Boolean locationIsRestaurant;

    /**
     * Whether meal was at home
     */
    @Column(name = "location_is_home")
    private Boolean locationIsHome;

    /**
     * User's description of the meal (for pattern analysis)
     */
    @Column(name = "meal_description", columnDefinition = "TEXT")
    private String mealDescription;

    /**
     * When the meal was eaten
     */
    @Column(name = "meal_time")
    private LocalDateTime mealTime;

    // Timestamps

    /**
     * When AI analysis was performed
     */
    @Column(name = "ai_analyzed_at")
    private LocalDateTime aiAnalyzedAt;

    /**
     * When user made the correction
     */
    @CreationTimestamp
    @Column(name = "corrected_at", nullable = false, updatable = false)
    private LocalDateTime correctedAt;

    /**
     * Calculate percent error from AI and user values
     * Positive = AI underestimated, Negative = AI overestimated
     */
    public static Double calculatePercentError(Double aiValue, Double userValue) {
        if (aiValue == null || userValue == null || userValue == 0.0) {
            return null;
        }
        return ((userValue - aiValue) / userValue) * 100.0;
    }

    /**
     * Get absolute error magnitude (for averaging)
     */
    public Double getAbsolutePercentError() {
        return percentError != null ? Math.abs(percentError) : null;
    }
}
