package com.nutritheous.correction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for field-level AI accuracy statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldAccuracyStats {
    /**
     * Field name (e.g., "calories", "protein_g")
     */
    private String fieldName;

    /**
     * Average absolute percent error (e.g., 15.5 means 15.5% average error)
     */
    private BigDecimal avgAbsPercentError;

    /**
     * Number of corrections for this field
     */
    private Long correctionCount;

    /**
     * Mean absolute error in field's units
     */
    private BigDecimal meanAbsoluteError;

    /**
     * Systematic bias (positive = AI underestimates, negative = AI overestimates)
     */
    private BigDecimal bias;
}
