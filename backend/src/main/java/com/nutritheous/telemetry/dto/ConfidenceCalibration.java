package com.nutritheous.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Calibration check: Does AI confidence correlate with actual accuracy?
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfidenceCalibration {
    /**
     * Confidence bucket (e.g., 0.7 means 0.7-0.79 range)
     */
    private Double confidenceBucket;

    /**
     * Average absolute percent error for this confidence level
     */
    private Double averagePercentError;

    /**
     * Number of corrections at this confidence level
     */
    private Long correctionCount;
}
