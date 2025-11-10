package com.nutritheous.meal;

import com.nutritheous.auth.User;
import com.nutritheous.common.dto.MealResponse;
import com.nutritheous.common.dto.PageResponse;
import com.nutritheous.meal.dto.MealUpdateRequest;
import com.nutritheous.security.FileValidationService;
import com.nutritheous.security.InputSanitizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/meals")
@Tag(name = "Meals", description = "Meal management and nutritional analysis endpoints")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class MealController {

    @Autowired
    private MealService mealService;

    @Autowired
    private FileValidationService fileValidationService;

    @Autowired
    private InputSanitizationService inputSanitizationService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload meal", description = "Upload a food image and/or description for nutritional analysis. Either image or description (or both) is required.")
    public ResponseEntity<MealResponse> uploadMeal(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "mealType", required = false) Meal.MealType mealType,
            @RequestParam(value = "mealTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime mealTime,
            @RequestParam(value = "description", required = false) String description
    ) {
        // Validate that at least one of image or description is provided
        if ((image == null || image.isEmpty()) && (description == null || description.isBlank())) {
            log.warn("❌ Meal upload rejected - neither image nor description provided");
            return ResponseEntity.badRequest().build();
        }

        log.info("🍽️  Received meal upload request - User: {}, Type: {}, Has Image: {}, Has Description: {}",
                user.getEmail(), mealType, image != null && !image.isEmpty(), description != null && !description.isBlank());

        // Validate image file if provided
        if (image != null && !image.isEmpty()) {
            fileValidationService.validateImageFile(image);
            log.debug("✅ Image file validation passed");
        }

        // Sanitize description if provided
        String sanitizedDescription = description;
        if (description != null && !description.isBlank()) {
            sanitizedDescription = inputSanitizationService.sanitizeMealDescription(description);
            log.debug("✅ Description sanitized");
        }

        MealResponse response = mealService.uploadMeal(
                user.getId(),
                image,
                mealType,
                mealTime,
                sanitizedDescription
        );

        log.info("✅ Meal upload complete - ID: {}, Image URL available: {}",
                response.getId(), response.getImageUrl() != null);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{mealId}")
    @Operation(summary = "Get meal by ID", description = "Retrieve a specific meal by its ID")
    public ResponseEntity<MealResponse> getMealById(
            @AuthenticationPrincipal User user,
            @PathVariable UUID mealId
    ) {
        MealResponse response = mealService.getMealById(mealId, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all user meals", description = "Retrieve all meals for the authenticated user")
    public ResponseEntity<List<MealResponse>> getUserMeals(
            @AuthenticationPrincipal User user
    ) {
        log.info("📋 Fetching all meals for user: {}", user.getEmail());

        List<MealResponse> meals = mealService.getUserMeals(user.getId());

        log.info("✅ Retrieved {} meals for user: {}", meals.size(), user.getEmail());
        log.info("📊 Each meal response contains fresh signed URLs (valid for 24 hours)");

        return ResponseEntity.ok(meals);
    }

    @GetMapping("/range")
    @Operation(summary = "Get meals by date range", description = "Retrieve meals within a specific date range")
    public ResponseEntity<List<MealResponse>> getMealsByDateRange(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        List<MealResponse> meals = mealService.getUserMealsByDateRange(user.getId(), startDate, endDate);
        return ResponseEntity.ok(meals);
    }

    @GetMapping("/type/{mealType}")
    @Operation(summary = "Get meals by type", description = "Retrieve meals filtered by meal type")
    public ResponseEntity<List<MealResponse>> getMealsByType(
            @AuthenticationPrincipal User user,
            @PathVariable Meal.MealType mealType
    ) {
        List<MealResponse> meals = mealService.getUserMealsByType(user.getId(), mealType);
        return ResponseEntity.ok(meals);
    }

    // ==================== Paginated Endpoints (Recommended for Production) ====================

    @GetMapping("/paginated")
    @Operation(summary = "Get user meals (paginated)",
               description = "Retrieve meals with pagination. Recommended for production use to avoid loading all meals at once.")
    public ResponseEntity<PageResponse<MealResponse>> getUserMealsPaginated(
            @AuthenticationPrincipal User user,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        // Enforce maximum page size to prevent resource exhaustion
        int effectiveSize = Math.min(size, 100);

        PageResponse<MealResponse> meals = mealService.getUserMealsPaginated(user.getId(), page, effectiveSize);

        log.info("📋 Retrieved page {} (size {}) of meals for user: {}",
                page, effectiveSize, user.getEmail());

        return ResponseEntity.ok(meals);
    }

    @GetMapping("/paginated/range")
    @Operation(summary = "Get meals by date range (paginated)",
               description = "Retrieve meals within a date range with pagination")
    public ResponseEntity<PageResponse<MealResponse>> getMealsByDateRangePaginated(
            @AuthenticationPrincipal User user,
            @Parameter(description = "Start date (ISO format)", example = "2025-01-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)", example = "2025-12-31T23:59:59")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        int effectiveSize = Math.min(size, 100);

        PageResponse<MealResponse> meals = mealService.getUserMealsByDateRangePaginated(
                user.getId(), startDate, endDate, page, effectiveSize);

        return ResponseEntity.ok(meals);
    }

    @GetMapping("/paginated/type/{mealType}")
    @Operation(summary = "Get meals by type (paginated)",
               description = "Retrieve meals filtered by type with pagination")
    public ResponseEntity<PageResponse<MealResponse>> getMealsByTypePaginated(
            @AuthenticationPrincipal User user,
            @PathVariable Meal.MealType mealType,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        int effectiveSize = Math.min(size, 100);

        PageResponse<MealResponse> meals = mealService.getUserMealsByTypePaginated(
                user.getId(), mealType, page, effectiveSize);

        return ResponseEntity.ok(meals);
    }

    @GetMapping("/count")
    @Operation(summary = "Get total meal count",
               description = "Get the total number of meals for the authenticated user")
    public ResponseEntity<Long> getUserMealsCount(
            @AuthenticationPrincipal User user
    ) {
        long count = mealService.getUserMealsCount(user.getId());
        return ResponseEntity.ok(count);
    }

    @PutMapping("/{mealId}")
    @Operation(summary = "Update meal metadata", description = "Update meal details and nutritional information")
    public ResponseEntity<MealResponse> updateMeal(
            @AuthenticationPrincipal User user,
            @PathVariable UUID mealId,
            @Valid @RequestBody MealUpdateRequest request
    ) {
        MealResponse response = mealService.updateMeal(mealId, user.getId(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{mealId}")
    @Operation(summary = "Delete meal", description = "Delete a specific meal")
    public ResponseEntity<Void> deleteMeal(
            @AuthenticationPrincipal User user,
            @PathVariable UUID mealId
    ) {
        mealService.deleteMeal(mealId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
