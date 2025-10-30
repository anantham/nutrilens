package com.nutritheous.ingredient;

import com.nutritheous.ingredient.dto.IngredientBreakdownResponse;
import com.nutritheous.ingredient.dto.IngredientRequest;
import com.nutritheous.ingredient.dto.IngredientResponse;
import com.nutritheous.ingredient.dto.UserIngredientResponse;
import com.nutritheous.meal.Meal;
import com.nutritheous.meal.MealRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API endpoints for ingredient management.
 * Enables users to view, edit, and manage meal ingredients.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Ingredients", description = "Ingredient breakdown and learning APIs")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class IngredientController {

    private final IngredientExtractionService ingredientExtractionService;
    private final MealIngredientRepository mealIngredientRepository;
    private final UserIngredientLibraryRepository userIngredientLibraryRepository;
    private final MealRepository mealRepository;

    public IngredientController(
            IngredientExtractionService ingredientExtractionService,
            MealIngredientRepository mealIngredientRepository,
            UserIngredientLibraryRepository userIngredientLibraryRepository,
            MealRepository mealRepository) {
        this.ingredientExtractionService = ingredientExtractionService;
        this.mealIngredientRepository = mealIngredientRepository;
        this.userIngredientLibraryRepository = userIngredientLibraryRepository;
        this.mealRepository = mealRepository;
    }

    /**
     * Get complete ingredient breakdown for a meal.
     */
    @GetMapping("/meals/{mealId}/ingredients")
    @Operation(summary = "Get ingredient breakdown", description = "Get all ingredients for a meal with aggregated totals")
    public ResponseEntity<IngredientBreakdownResponse> getMealIngredients(
            @PathVariable UUID mealId,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        // Verify meal belongs to user
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new RuntimeException("Meal not found: " + mealId));

        if (!meal.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        // Get ingredients
        List<MealIngredient> ingredients = mealIngredientRepository.findByMealIdOrderByDisplayOrderAsc(mealId);

        List<IngredientResponse> ingredientResponses = ingredients.stream()
                .map(IngredientResponse::fromEntity)
                .collect(Collectors.toList());

        IngredientBreakdownResponse response = IngredientBreakdownResponse.builder()
                .mealId(mealId)
                .mealName(meal.getDescription())
                .ingredients(ingredientResponses)
                .build();

        // Calculate totals
        response.calculateTotals();

        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing ingredient (user correction).
     */
    @PutMapping("/meals/{mealId}/ingredients/{ingredientId}")
    @Operation(summary = "Update ingredient", description = "Update ingredient details (marks as user-corrected)")
    public ResponseEntity<IngredientResponse> updateIngredient(
            @PathVariable UUID mealId,
            @PathVariable UUID ingredientId,
            @Valid @RequestBody IngredientRequest request,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        // Verify ingredient exists
        MealIngredient ingredient = mealIngredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

        // Verify ingredient belongs to the specified meal
        if (!ingredient.getMeal().getId().equals(mealId)) {
            throw new RuntimeException("Ingredient does not belong to this meal");
        }

        // Verify meal belongs to user
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new RuntimeException("Meal not found: " + mealId));

        if (!meal.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        MealIngredient updated = ingredientExtractionService.updateIngredient(ingredientId, request);
        log.info("User {} updated ingredient {} for meal {}", userId, ingredientId, mealId);

        return ResponseEntity.ok(IngredientResponse.fromEntity(updated));
    }

    /**
     * Add a new ingredient to a meal.
     */
    @PostMapping("/meals/{mealId}/ingredients")
    @Operation(summary = "Add ingredient", description = "Add a new ingredient to the meal")
    public ResponseEntity<IngredientResponse> addIngredient(
            @PathVariable UUID mealId,
            @Valid @RequestBody IngredientRequest request,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        // Verify meal belongs to user
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new RuntimeException("Meal not found: " + mealId));

        if (!meal.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        MealIngredient created = ingredientExtractionService.addIngredient(meal, request);
        log.info("User {} added ingredient to meal {}", userId, mealId);

        return ResponseEntity.ok(IngredientResponse.fromEntity(created));
    }

    /**
     * Delete an ingredient from a meal.
     */
    @DeleteMapping("/meals/{mealId}/ingredients/{ingredientId}")
    @Operation(summary = "Delete ingredient", description = "Remove an ingredient from the meal")
    public ResponseEntity<Void> deleteIngredient(
            @PathVariable UUID mealId,
            @PathVariable UUID ingredientId,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        // Verify ingredient exists
        MealIngredient ingredient = mealIngredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

        // Verify ingredient belongs to the specified meal
        if (!ingredient.getMeal().getId().equals(mealId)) {
            throw new RuntimeException("Ingredient does not belong to this meal");
        }

        // Verify meal belongs to user
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new RuntimeException("Meal not found: " + mealId));

        if (!meal.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        ingredientExtractionService.deleteIngredient(ingredientId);
        log.info("User {} deleted ingredient {} from meal {}", userId, ingredientId, mealId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Get user's learned ingredient library.
     */
    @GetMapping("/ingredients/library")
    @Operation(summary = "Get ingredient library", description = "Get user's personal learned ingredient database")
    public ResponseEntity<List<UserIngredientResponse>> getUserIngredientLibrary(
            Authentication authentication,
            @RequestParam(required = false) String search) {

        UUID userId = UUID.fromString(authentication.getName());

        List<UserIngredientLibrary> ingredients;
        if (search != null && !search.isBlank()) {
            ingredients = userIngredientLibraryRepository.searchByName(userId, search);
        } else {
            ingredients = userIngredientLibraryRepository.findByUserIdOrderByConfidenceScoreDesc(userId);
        }

        List<UserIngredientResponse> responses = ingredients.stream()
                .map(UserIngredientResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get high-confidence ingredients for auto-fill suggestions.
     */
    @GetMapping("/ingredients/suggestions")
    @Operation(summary = "Get ingredient suggestions", description = "Get high-confidence ingredients for auto-fill")
    public ResponseEntity<List<UserIngredientResponse>> getIngredientSuggestions(
            Authentication authentication,
            @RequestParam(defaultValue = "0.7") Double minConfidence) {

        UUID userId = UUID.fromString(authentication.getName());

        List<UserIngredientLibrary> ingredients = userIngredientLibraryRepository
                .findHighConfidenceIngredients(userId, minConfidence);

        List<UserIngredientResponse> responses = ingredients.stream()
                .map(UserIngredientResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get statistics about user's learned ingredient library.
     */
    @GetMapping("/ingredients/library/stats")
    @Operation(summary = "Get library statistics", description = "Get statistics about learned ingredients (total count, avg confidence, top ingredients)")
    public ResponseEntity<com.nutritheous.ingredient.dto.IngredientLibraryStatsResponse> getLibraryStats(
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());

        List<UserIngredientLibrary> allIngredients = userIngredientLibraryRepository
                .findByUserIdOrderByConfidenceScoreDesc(userId);

        if (allIngredients.isEmpty()) {
            // Return empty stats if no ingredients learned yet
            return ResponseEntity.ok(com.nutritheous.ingredient.dto.IngredientLibraryStatsResponse.builder()
                    .totalIngredients(0)
                    .highConfidenceCount(0)
                    .avgConfidenceScore(0.0)
                    .totalCorrections(0)
                    .build());
        }

        // Calculate statistics
        int totalIngredients = allIngredients.size();
        long highConfidenceCount = allIngredients.stream()
                .filter(i -> i.getConfidenceScore() != null && i.getConfidenceScore() >= 0.7)
                .count();
        double avgConfidence = allIngredients.stream()
                .filter(i -> i.getConfidenceScore() != null)
                .mapToDouble(UserIngredientLibrary::getConfidenceScore)
                .average()
                .orElse(0.0);
        int totalCorrections = allIngredients.stream()
                .mapToInt(i -> i.getSampleSize() != null ? i.getSampleSize() : 0)
                .sum();

        // Find most frequent ingredient (highest sample size)
        UserIngredientLibrary mostFrequent = allIngredients.stream()
                .max((a, b) -> Integer.compare(
                        a.getSampleSize() != null ? a.getSampleSize() : 0,
                        b.getSampleSize() != null ? b.getSampleSize() : 0))
                .orElse(null);

        // Find highest confidence ingredient (already sorted by confidence desc)
        UserIngredientLibrary highestConfidence = allIngredients.get(0);

        // Find most recent ingredient
        UserIngredientLibrary mostRecent = allIngredients.stream()
                .filter(i -> i.getLastUsed() != null)
                .max((a, b) -> a.getLastUsed().compareTo(b.getLastUsed()))
                .orElse(null);

        com.nutritheous.ingredient.dto.IngredientLibraryStatsResponse response =
                com.nutritheous.ingredient.dto.IngredientLibraryStatsResponse.builder()
                        .totalIngredients(totalIngredients)
                        .highConfidenceCount((int) highConfidenceCount)
                        .avgConfidenceScore(avgConfidence)
                        .totalCorrections(totalCorrections)
                        .mostFrequentIngredient(mostFrequent != null ? buildTopIngredient(mostFrequent) : null)
                        .highestConfidenceIngredient(buildTopIngredient(highestConfidence))
                        .mostRecentIngredient(mostRecent != null ? buildTopIngredient(mostRecent) : null)
                        .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to build TopIngredient DTO.
     */
    private com.nutritheous.ingredient.dto.IngredientLibraryStatsResponse.TopIngredient buildTopIngredient(
            UserIngredientLibrary ingredient) {
        return com.nutritheous.ingredient.dto.IngredientLibraryStatsResponse.TopIngredient.builder()
                .name(ingredient.getIngredientName())
                .category(ingredient.getIngredientCategory())
                .sampleSize(ingredient.getSampleSize())
                .confidenceScore(ingredient.getConfidenceScore())
                .lastUsed(ingredient.getLastUsed() != null ? ingredient.getLastUsed().toString() : null)
                .build();
    }
}
