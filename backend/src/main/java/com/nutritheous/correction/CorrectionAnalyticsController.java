package com.nutritheous.correction;

import com.nutritheous.auth.User;
import com.nutritheous.correction.dto.FieldAccuracyStats;
import com.nutritheous.correction.dto.LocationAccuracyStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST controller for AI correction analytics and accuracy metrics.
 *
 * <p>Provides endpoints to:
 * - View AI accuracy statistics
 * - Analyze corrections by location, field, confidence
 * - Detect systematic biases
 * - Generate accuracy reports
 */
@RestController
@RequestMapping("/api/analytics/corrections")
@RequiredArgsConstructor
@Tag(name = "Correction Analytics", description = "AI accuracy and correction analytics endpoints")
@SecurityRequirement(name = "bearerAuth")
public class CorrectionAnalyticsController {

    private final CorrectionAnalyticsService analyticsService;

    @GetMapping("/overall-accuracy")
    @Operation(
            summary = "Get overall AI accuracy by field",
            description = "Returns accuracy statistics for each nutrition field (calories, protein, fat, etc.), ordered by worst to best accuracy"
    )
    public ResponseEntity<List<FieldAccuracyStats>> getOverallAccuracy() {
        List<FieldAccuracyStats> stats = analyticsService.getOverallAccuracy();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/accuracy-by-location")
    @Operation(
            summary = "Get AI accuracy by location type",
            description = "Analyzes how AI accuracy differs between restaurants, home-cooked meals, cafes, etc."
    )
    public ResponseEntity<List<LocationAccuracyStats>> getAccuracyByLocation() {
        List<LocationAccuracyStats> stats = analyticsService.getAccuracyByLocation();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/user-accuracy")
    @Operation(
            summary = "Get user-specific AI accuracy",
            description = "Returns AI accuracy statistics for the authenticated user's corrections"
    )
    public ResponseEntity<List<FieldAccuracyStats>> getUserAccuracy(
            @AuthenticationPrincipal User user
    ) {
        List<FieldAccuracyStats> stats = analyticsService.getUserAccuracy(user.getId());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/systematic-bias")
    @Operation(
            summary = "Detect systematic bias in AI predictions",
            description = "Identifies if AI consistently under/overestimates specific fields. " +
                    "Positive values = AI underestimates, Negative values = AI overestimates"
    )
    public ResponseEntity<List<FieldAccuracyStats>> getSystematicBias() {
        List<FieldAccuracyStats> biases = analyticsService.detectSystematicBias();
        return ResponseEntity.ok(biases);
    }

    @GetMapping("/field/{fieldName}")
    @Operation(
            summary = "Get accuracy for a specific field",
            description = "Returns detailed accuracy statistics for a single nutrition field"
    )
    public ResponseEntity<FieldAccuracyStats> getFieldAccuracy(
            @Parameter(description = "Field name (e.g., 'calories', 'protein_g')", example = "calories")
            @PathVariable String fieldName
    ) {
        FieldAccuracyStats stats = analyticsService.getFieldSummary(fieldName);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/location/{locationType}/field/{fieldName}")
    @Operation(
            summary = "Get accuracy for specific location and field",
            description = "Returns accuracy for a specific combination of location type and nutrition field"
    )
    public ResponseEntity<Map<String, Object>> getAccuracyForLocationAndField(
            @Parameter(description = "Location type (e.g., 'restaurant', 'home')", example = "restaurant")
            @PathVariable String locationType,
            @Parameter(description = "Field name (e.g., 'calories', 'protein_g')", example = "calories")
            @PathVariable String fieldName
    ) {
        BigDecimal error = analyticsService.getAccuracyForLocationAndField(locationType, fieldName);
        return ResponseEntity.ok(Map.of(
                "locationType", locationType,
                "fieldName", fieldName,
                "avgAbsPercentError", error != null ? error : 0.0
        ));
    }

    @GetMapping("/confidence-calibration")
    @Operation(
            summary = "Check confidence calibration",
            description = "Analyzes whether high AI confidence correlates with low error (good calibration)"
    )
    public ResponseEntity<Map<String, Object>> getConfidenceCalibration(
            @Parameter(description = "Minimum confidence threshold (0.0-1.0)", example = "0.8")
            @RequestParam(defaultValue = "0.8") BigDecimal minConfidence
    ) {
        BigDecimal avgError = analyticsService.getHighConfidenceAccuracy(minConfidence);
        return ResponseEntity.ok(Map.of(
                "minConfidence", minConfidence,
                "avgAbsPercentError", avgError != null ? avgError : 0.0,
                "wellCalibrated", avgError != null && avgError.compareTo(BigDecimal.valueOf(15.0)) < 0
        ));
    }

    @GetMapping("/recent")
    @Operation(
            summary = "Get recent corrections",
            description = "Returns recent AI corrections made by users for manual review"
    )
    public ResponseEntity<List<AiCorrectionLog>> getRecentCorrections(
            @Parameter(description = "Number of days to look back", example = "7")
            @RequestParam(defaultValue = "7") int daysSince
    ) {
        List<AiCorrectionLog> corrections = analyticsService.getRecentCorrections(daysSince);
        return ResponseEntity.ok(corrections);
    }

    @GetMapping("/report")
    @Operation(
            summary = "Generate comprehensive accuracy report",
            description = "Returns a human-readable text report summarizing AI accuracy across all dimensions"
    )
    public ResponseEntity<String> generateReport() {
        String report = analyticsService.generateAccuracyReport();
        return ResponseEntity.ok(report);
    }

    @GetMapping("/correction-rate")
    @Operation(
            summary = "Get user correction rate",
            description = "Returns what percentage of meals this user corrects (indicates trust in AI)"
    )
    public ResponseEntity<Map<String, Object>> getCorrectionRate(
            @AuthenticationPrincipal User user,
            @Parameter(description = "Number of days to look back", example = "30")
            @RequestParam(defaultValue = "30") int daysSince
    ) {
        BigDecimal correctedMeals = analyticsService.getCorrectionRate(user.getId(), daysSince);
        return ResponseEntity.ok(Map.of(
                "userId", user.getId(),
                "daysSince", daysSince,
                "correctedMealsCount", correctedMeals
        ));
    }
}
