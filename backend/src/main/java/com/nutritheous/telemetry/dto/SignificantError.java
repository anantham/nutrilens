package com.nutritheous.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a significant AI error that requires attention
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignificantError {
    /**
     * ID of the correction log entry
     */
    private UUID id;

    /**
     * Meal ID
     */
    private UUID mealId;

    /**
     * Field that was corrected
     */
    private String fieldName;

    /**
     * AI's original value
     */
    private Double aiValue;

    /**
     * User's corrected value
     */
    private Double userValue;

    /**
     * Percent error (positive = underestimate, negative = overestimate)
     */
    private Double percentError;

    /**
     * AI's confidence when it made this estimate
     */
    private Double confidence;

    /**
     * Location type where meal was eaten
     */
    private String locationType;

    /**
     * Meal description for context
     */
    private String mealDescription;

    /**
     * When the correction was made
     */
    private LocalDateTime correctedAt;
}
