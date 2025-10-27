package com.nutritheous.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Accuracy statistics for a specific nutrition field
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldAccuracy {
    /**
     * Field name (e.g., "calories", "protein_g")
     */
    private String fieldName;

    /**
     * Average absolute percent error for this field
     */
    private Double averagePercentError;

    /**
     * Number of corrections for this field
     */
    private Long correctionCount;

    /**
     * Minimum percent error seen
     */
    private Double minError;

    /**
     * Maximum percent error seen
     */
    private Double maxError;
}
