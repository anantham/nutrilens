package com.nutritheous.telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Accuracy statistics by location type and field
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationFieldAccuracy {
    /**
     * Location type (e.g., "restaurant", "home", "cafe")
     */
    private String locationType;

    /**
     * Field name (e.g., "calories", "protein_g")
     */
    private String fieldName;

    /**
     * Average absolute percent error for this location+field combo
     */
    private Double averagePercentError;

    /**
     * Number of corrections for this location+field combo
     */
    private Long correctionCount;
}
