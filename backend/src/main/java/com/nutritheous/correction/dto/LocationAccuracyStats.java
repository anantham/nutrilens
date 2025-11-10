package com.nutritheous.correction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for location-based AI accuracy statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationAccuracyStats {
    /**
     * Location type (e.g., "restaurant", "home", "cafe")
     */
    private String locationType;

    /**
     * Field name (e.g., "calories", "protein_g")
     */
    private String fieldName;

    /**
     * Average absolute percent error for this location type
     */
    private BigDecimal avgAbsPercentError;

    /**
     * Number of corrections for this location + field combination
     */
    private Long correctionCount;
}
