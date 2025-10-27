package com.nutritheous.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Overall AI accuracy statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAccuracyStats {
    /**
     * Total number of user corrections tracked
     */
    private Long totalCorrections;

    /**
     * Average absolute percent error across all corrections
     */
    private Double averagePercentError;

    /**
     * Standard deviation of percent errors
     */
    private Double errorStdDev;

    /**
     * Number of unique meals that have been corrected
     */
    private Long uniqueMealsEdited;

    /**
     * Percentage of all meals that have user edits
     */
    private Double editRate;
}
