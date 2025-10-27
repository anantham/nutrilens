package com.nutritheous.telemetry;

import com.nutritheous.auth.User;
import com.nutritheous.telemetry.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for AI accuracy analytics and telemetry
 */
@RestController
@RequestMapping("/api/analytics/ai")
@RequiredArgsConstructor
@Tag(name = "AI Analytics", description = "AI accuracy measurement and telemetry endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AiAnalyticsController {

    private final AiAnalyticsService analyticsService;

    @GetMapping("/accuracy")
    @Operation(
            summary = "Get overall AI accuracy statistics",
            description = "Returns overall accuracy metrics including total corrections, average error, edit rate, etc."
    )
    public ResponseEntity<AiAccuracyStats> getOverallAccuracy(@AuthenticationPrincipal User user) {
        AiAccuracyStats stats = analyticsService.getOverallAccuracyStats(user.getId());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/accuracy-by-field")
    @Operation(
            summary = "Get accuracy statistics by nutrition field",
            description = "Returns accuracy metrics broken down by field (calories, protein_g, fat_g, etc.)"
    )
    public ResponseEntity<List<FieldAccuracy>> getAccuracyByField(@AuthenticationPrincipal User user) {
        List<FieldAccuracy> stats = analyticsService.getAccuracyByField(user.getId());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/accuracy-by-location")
    @Operation(
            summary = "Get accuracy statistics by location type",
            description = "Returns accuracy metrics broken down by location type (restaurant, home, etc.) and field"
    )
    public ResponseEntity<List<LocationFieldAccuracy>> getAccuracyByLocation(@AuthenticationPrincipal User user) {
        List<LocationFieldAccuracy> stats = analyticsService.getAccuracyByLocation(user.getId());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/confidence-calibration")
    @Operation(
            summary = "Get confidence calibration data",
            description = "Returns accuracy metrics by AI confidence level to check if high confidence correlates with low error"
    )
    public ResponseEntity<List<ConfidenceCalibration>> getConfidenceCalibration(@AuthenticationPrincipal User user) {
        List<ConfidenceCalibration> stats = analyticsService.getConfidenceCalibration(user.getId());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/significant-errors")
    @Operation(
            summary = "Get significant AI errors",
            description = "Returns corrections where AI error exceeded the specified threshold (default 50%)"
    )
    public ResponseEntity<List<SignificantError>> getSignificantErrors(
            @AuthenticationPrincipal User user,
            @Parameter(description = "Error threshold percentage (default 50.0)", example = "50.0")
            @RequestParam(defaultValue = "50.0") Double threshold
    ) {
        List<SignificantError> errors = analyticsService.getSignificantErrors(user.getId(), threshold);
        return ResponseEntity.ok(errors);
    }
}
