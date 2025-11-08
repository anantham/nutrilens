package com.nutritheous.ingredient;

import com.nutritheous.auth.User;
import com.nutritheous.meal.Meal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IngredientLearningService.
 * Tests Welford's algorithm, confidence scoring, and learning flow.
 */
@ExtendWith(MockitoExtension.class)
class IngredientLearningServiceTest {

    @Mock
    private UserIngredientLibraryRepository libraryRepository;

    @Mock
    private IngredientNormalizationService normalizationService;

    @InjectMocks
    private IngredientLearningService learningService;

    private User testUser;
    private Meal testMeal;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");

        testMeal = new Meal();
        testMeal.setId(UUID.randomUUID());
        testMeal.setUser(testUser);
    }

    /**
     * Test: First observation creates new ingredient with sample size 1.
     */
    @Test
    void testLearnFromCorrection_FirstObservation_CreatesNewIngredient() {
        // Given: First time seeing "idli"
        MealIngredient ingredient = createIngredient("idli", 2.0, "piece", 156.0, 5.6, 0.6, 30.0);

        when(normalizationService.normalize("idli")).thenReturn("idli");
        when(libraryRepository.findByUserIdOrderByConfidenceScoreDesc(testUser.getId())).thenReturn(new ArrayList<>());
        when(normalizationService.findBestMatch(eq("idli"), anyList(), anyInt())).thenReturn(Optional.empty());

        // When
        learningService.learnFromCorrection(ingredient, testUser);

        // Then: New library entry created
        ArgumentCaptor<UserIngredientLibrary> captor = ArgumentCaptor.forClass(UserIngredientLibrary.class);
        verify(libraryRepository).save(captor.capture());

        UserIngredientLibrary saved = captor.getValue();
        assertEquals("idli", saved.getIngredientName());
        assertEquals("idli", saved.getNormalizedName());
        assertEquals(1, saved.getSampleSize());
        assertEquals(0.0, saved.getStdDevCalories());  // First observation - no variability
        assertTrue(saved.getConfidenceScore() < 0.5);  // Low confidence with only 1 sample

        // Per-100g calculation: 2 pieces ≈ 100g (heuristic), so 156 cal / 100g = 156 cal/100g
        assertNotNull(saved.getAvgCaloriesPer100g());
        assertTrue(saved.getAvgCaloriesPer100g() > 0);
    }

    /**
     * Test: Second observation updates averages using Welford's algorithm.
     */
    @Test
    void testLearnFromCorrection_SecondObservation_UpdatesAverages() {
        // Given: Existing ingredient with 1 observation
        UserIngredientLibrary existing = UserIngredientLibrary.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .ingredientName("idli")
                .normalizedName("idli")
                .avgCaloriesPer100g(156.0)
                .avgProteinPer100g(5.6)
                .avgFatPer100g(0.6)
                .avgCarbsPer100g(30.0)
                .stdDevCalories(0.0)
                .sampleSize(1)
                .confidenceScore(0.18)  // Low initial confidence
                .typicalQuantity(2.0)
                .typicalUnit("piece")
                .lastUsed(LocalDateTime.now().minusDays(1))
                .build();

        // Second observation: slightly different values (160 cal instead of 156)
        MealIngredient ingredient = createIngredient("idli", 2.0, "piece", 160.0, 5.8, 0.7, 31.0);

        when(normalizationService.normalize("idli")).thenReturn("idli");
        when(libraryRepository.findByUserIdOrderByConfidenceScoreDesc(testUser.getId()))
                .thenReturn(List.of(existing));
        when(normalizationService.findBestMatch(eq("idli"), anyList(), anyInt()))
                .thenReturn(Optional.of(existing));

        // When
        learningService.learnFromCorrection(ingredient, testUser);

        // Then: Existing entry updated
        ArgumentCaptor<UserIngredientLibrary> captor = ArgumentCaptor.forClass(UserIngredientLibrary.class);
        verify(libraryRepository).save(captor.capture());

        UserIngredientLibrary updated = captor.getValue();
        assertEquals(2, updated.getSampleSize());

        // Mean should be between 156 and 160
        assertTrue(updated.getAvgCaloriesPer100g() >= 156.0 && updated.getAvgCaloriesPer100g() <= 160.0);

        // Standard deviation should now be > 0 (we have variability)
        assertTrue(updated.getStdDevCalories() > 0);

        // Confidence should increase with more samples
        assertNotNull(updated.getConfidenceScore(), "Confidence score should not be null");
        assertTrue(updated.getConfidenceScore() >= existing.getConfidenceScore(),
                String.format("Confidence should not decrease: old=%f, new=%f",
                        existing.getConfidenceScore(), updated.getConfidenceScore()));
    }

    /**
     * Test: Multiple observations converge to true mean.
     */
    @Test
    void testLearnFromCorrection_MultipleObservations_ConvergesToMean() {
        // Given: Existing ingredient with 5 observations (mean = 150 cal/100g)
        UserIngredientLibrary existing = UserIngredientLibrary.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .ingredientName("rice")
                .normalizedName("rice")
                .avgCaloriesPer100g(150.0)
                .avgProteinPer100g(3.0)
                .avgFatPer100g(0.3)
                .avgCarbsPer100g(32.0)
                .stdDevCalories(5.0)  // Low variability
                .sampleSize(5)
                .confidenceScore(0.7)
                .typicalQuantity(100.0)
                .typicalUnit("g")
                .lastUsed(LocalDateTime.now().minusDays(1))
                .build();

        // New observation: 152 cal/100g (close to mean)
        MealIngredient ingredient = createIngredient("rice", 100.0, "g", 152.0, 3.1, 0.3, 32.5);

        when(normalizationService.normalize("rice")).thenReturn("rice");
        when(libraryRepository.findByUserIdOrderByConfidenceScoreDesc(testUser.getId()))
                .thenReturn(List.of(existing));
        when(normalizationService.findBestMatch(eq("rice"), anyList(), anyInt()))
                .thenReturn(Optional.of(existing));

        // When
        learningService.learnFromCorrection(ingredient, testUser);

        // Then
        ArgumentCaptor<UserIngredientLibrary> captor = ArgumentCaptor.forClass(UserIngredientLibrary.class);
        verify(libraryRepository).save(captor.capture());

        UserIngredientLibrary updated = captor.getValue();
        assertEquals(6, updated.getSampleSize());

        // Mean should move slightly toward 152
        assertTrue(updated.getAvgCaloriesPer100g() > 150.0 && updated.getAvgCaloriesPer100g() < 152.0);

        // Standard deviation should remain low (consistent data)
        assertTrue(updated.getStdDevCalories() < 10.0);

        // High confidence (6 samples + low variability)
        // With formula: sampleFactor = 1 - e^(-6/5) ≈ 0.70, consistencyFactor = 1.0 (stdDev < 5)
        // Expected confidence ≈ 0.70, so > 0.65 is reasonable
        assertTrue(updated.getConfidenceScore() > 0.65);
    }

    /**
     * Test: High variability reduces confidence score.
     */
    @Test
    void testLearnFromCorrection_HighVariability_ReducesConfidence() {
        // Given: "curry" with high variability (depends on recipe)
        UserIngredientLibrary existing = UserIngredientLibrary.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .ingredientName("curry")
                .normalizedName("curry")
                .avgCaloriesPer100g(200.0)
                .avgProteinPer100g(10.0)
                .avgFatPer100g(12.0)
                .avgCarbsPer100g(15.0)
                .stdDevCalories(50.0)  // High variability!
                .sampleSize(8)
                .confidenceScore(0.35)  // Low confidence despite many samples
                .typicalQuantity(150.0)
                .typicalUnit("g")
                .lastUsed(LocalDateTime.now().minusDays(1))
                .build();

        // New observation with very different value
        MealIngredient ingredient = createIngredient("curry", 150.0, "g", 120.0, 8.0, 6.0, 12.0);

        when(normalizationService.normalize("curry")).thenReturn("curry");
        when(libraryRepository.findByUserIdOrderByConfidenceScoreDesc(testUser.getId()))
                .thenReturn(List.of(existing));
        when(normalizationService.findBestMatch(eq("curry"), anyList(), anyInt()))
                .thenReturn(Optional.of(existing));

        // When
        learningService.learnFromCorrection(ingredient, testUser);

        // Then
        ArgumentCaptor<UserIngredientLibrary> captor = ArgumentCaptor.forClass(UserIngredientLibrary.class);
        verify(libraryRepository).save(captor.capture());

        UserIngredientLibrary updated = captor.getValue();

        // Despite having 9 samples, confidence remains low due to high variability
        assertTrue(updated.getConfidenceScore() < 0.6);
    }

    /**
     * Test: Fuzzy matching finds similar ingredient names.
     */
    @Test
    void testLearnFromCorrection_FuzzyMatching_FindsSimilarIngredient() {
        // Given: Existing "idli" in library
        UserIngredientLibrary existing = UserIngredientLibrary.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .ingredientName("idli")
                .normalizedName("idli")
                .avgCaloriesPer100g(156.0)
                .sampleSize(3)
                .confidenceScore(0.5)
                .build();

        // User enters "idly" (typo)
        MealIngredient ingredient = createIngredient("idly", 2.0, "piece", 158.0, 5.7, 0.6, 30.5);

        when(normalizationService.normalize("idly")).thenReturn("idli");  // Alias normalization
        when(libraryRepository.findByUserIdOrderByConfidenceScoreDesc(testUser.getId()))
                .thenReturn(List.of(existing));
        when(normalizationService.findBestMatch(eq("idli"), anyList(), anyInt()))
                .thenReturn(Optional.of(existing));  // Fuzzy match found!

        // When
        learningService.learnFromCorrection(ingredient, testUser);

        // Then: Should update existing "idli", not create new "idly"
        ArgumentCaptor<UserIngredientLibrary> captor = ArgumentCaptor.forClass(UserIngredientLibrary.class);
        verify(libraryRepository).save(captor.capture());

        UserIngredientLibrary updated = captor.getValue();
        assertEquals(4, updated.getSampleSize());  // Incremented from 3
        assertEquals("idli", updated.getIngredientName());  // Still "idli", not "idly"
    }

    /**
     * Test: Typical quantity updates with weighted average (70% old, 30% new).
     */
    @Test
    void testLearnFromCorrection_TypicalQuantity_WeightedAverage() {
        // Given: User typically eats 2 idlis
        UserIngredientLibrary existing = UserIngredientLibrary.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .ingredientName("idli")
                .normalizedName("idli")
                .avgCaloriesPer100g(156.0)
                .sampleSize(5)
                .confidenceScore(0.7)
                .typicalQuantity(2.0)  // Typically 2 pieces
                .typicalUnit("piece")
                .build();

        // User eats 3 idlis this time
        MealIngredient ingredient = createIngredient("idli", 3.0, "piece", 234.0, 8.4, 0.9, 45.0);

        when(normalizationService.normalize("idli")).thenReturn("idli");
        when(libraryRepository.findByUserIdOrderByConfidenceScoreDesc(testUser.getId()))
                .thenReturn(List.of(existing));
        when(normalizationService.findBestMatch(eq("idli"), anyList(), anyInt()))
                .thenReturn(Optional.of(existing));

        // When
        learningService.learnFromCorrection(ingredient, testUser);

        // Then
        ArgumentCaptor<UserIngredientLibrary> captor = ArgumentCaptor.forClass(UserIngredientLibrary.class);
        verify(libraryRepository).save(captor.capture());

        UserIngredientLibrary updated = captor.getValue();

        // Weighted average: 0.7 * 2.0 + 0.3 * 3.0 = 1.4 + 0.9 = 2.3
        assertEquals(2.3, updated.getTypicalQuantity(), 0.01);
    }

    /**
     * Test: Null or invalid ingredient is handled gracefully.
     */
    @Test
    void testLearnFromCorrection_NullIngredient_HandlesGracefully() {
        // When & Then: Should not throw exception
        assertDoesNotThrow(() -> learningService.learnFromCorrection(null, testUser));
        verify(libraryRepository, never()).save(any());
    }

    /**
     * Test: Ingredient with no quantity cannot be learned.
     */
    @Test
    void testLearnFromCorrection_NoQuantity_DoesNotLearn() {
        // Given: Ingredient with null quantity
        MealIngredient ingredient = MealIngredient.builder()
                .id(UUID.randomUUID())
                .meal(testMeal)
                .ingredientName("unknown")
                .quantity(null)  // Missing!
                .unit("g")
                .calories(100.0)
                .build();

        when(normalizationService.normalize("unknown")).thenReturn("unknown");
        // No need to stub libraryRepository - method returns early before calling it

        // When
        learningService.learnFromCorrection(ingredient, testUser);

        // Then: Should not save (cannot calculate per-100g)
        verify(libraryRepository, never()).save(any());
    }

    /**
     * Test: CRITICAL BUG FIX - Unit mismatch does not corrupt typical quantity.
     *
     * Bug scenario: User logs "rice" as 100g, then logs "rice" as 2 pieces.
     * OLD BUGGY CODE would calculate: 0.7 * 100 + 0.3 * 2 = 70.6 pieces (NONSENSE!)
     * NEW FIXED CODE should skip update and keep original 100g.
     */
    @Test
    void testLearnFromCorrection_UnitMismatch_DoesNotCorruptData() {
        // Given: Existing "rice" with typical quantity 100g
        UserIngredientLibrary existing = UserIngredientLibrary.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .ingredientName("rice")
                .normalizedName("rice")
                .avgCaloriesPer100g(130.0)
                .avgProteinPer100g(2.7)
                .avgFatPer100g(0.3)
                .avgCarbsPer100g(28.0)
                .stdDevCalories(5.0)
                .sampleSize(3)
                .confidenceScore(0.6)
                .typicalQuantity(100.0)  // 100 grams
                .typicalUnit("g")         // Unit: grams
                .lastUsed(LocalDateTime.now().minusDays(1))
                .build();

        // User logs rice as "2 pieces" (incompatible unit!)
        MealIngredient ingredient = createIngredient("rice", 2.0, "piece", 156.0, 5.4, 0.6, 30.0);

        when(normalizationService.normalize("rice")).thenReturn("rice");
        when(libraryRepository.findByUserIdOrderByConfidenceScoreDesc(testUser.getId()))
                .thenReturn(List.of(existing));
        when(normalizationService.findBestMatch(eq("rice"), anyList(), anyInt()))
                .thenReturn(Optional.of(existing));

        // When
        learningService.learnFromCorrection(ingredient, testUser);

        // Then: Typical quantity should NOT be corrupted
        ArgumentCaptor<UserIngredientLibrary> captor = ArgumentCaptor.forClass(UserIngredientLibrary.class);
        verify(libraryRepository).save(captor.capture());

        UserIngredientLibrary updated = captor.getValue();

        // CRITICAL: Quantity should remain 100g, NOT become 70.6 pieces!
        assertEquals(100.0, updated.getTypicalQuantity(), 0.01,
                "Typical quantity should remain unchanged when units mismatch");
        assertEquals("g", updated.getTypicalUnit(),
                "Typical unit should remain 'g' when new unit is incompatible");

        // Nutrition per-100g should still be updated (that's independent of unit)
        assertEquals(4, updated.getSampleSize(), "Sample size should still increment");
        assertTrue(updated.getAvgCaloriesPer100g() > 130.0 && updated.getAvgCaloriesPer100g() < 156.0,
                "Nutrition averages should still update");
    }

    /**
     * Test: Unit change from null to non-null should work (first observation).
     */
    @Test
    void testLearnFromCorrection_UnitFromNullToValue_SetsUnit() {
        // Given: Existing ingredient with no unit set
        UserIngredientLibrary existing = UserIngredientLibrary.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .ingredientName("rice")
                .normalizedName("rice")
                .avgCaloriesPer100g(130.0)
                .sampleSize(1)
                .confidenceScore(0.3)
                .typicalQuantity(null)  // No quantity yet
                .typicalUnit(null)      // No unit yet
                .build();

        // User provides quantity with unit
        MealIngredient ingredient = createIngredient("rice", 100.0, "g", 130.0, 2.7, 0.3, 28.0);

        when(normalizationService.normalize("rice")).thenReturn("rice");
        when(libraryRepository.findByUserIdOrderByConfidenceScoreDesc(testUser.getId()))
                .thenReturn(List.of(existing));
        when(normalizationService.findBestMatch(eq("rice"), anyList(), anyInt()))
                .thenReturn(Optional.of(existing));

        // When
        learningService.learnFromCorrection(ingredient, testUser);

        // Then: Should adopt the new unit
        ArgumentCaptor<UserIngredientLibrary> captor = ArgumentCaptor.forClass(UserIngredientLibrary.class);
        verify(libraryRepository).save(captor.capture());

        UserIngredientLibrary updated = captor.getValue();

        assertEquals(100.0, updated.getTypicalQuantity(), "Should set initial quantity");
        assertEquals("g", updated.getTypicalUnit(), "Should set initial unit");
    }

    /**
     * Test: Same unit should allow weighted average (normal case).
     */
    @Test
    void testLearnFromCorrection_SameUnit_UpdatesWithWeightedAverage() {
        // Given: Existing "rice" with 100g
        UserIngredientLibrary existing = UserIngredientLibrary.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .ingredientName("rice")
                .normalizedName("rice")
                .avgCaloriesPer100g(130.0)
                .sampleSize(3)
                .confidenceScore(0.6)
                .typicalQuantity(100.0)  // 100 grams
                .typicalUnit("g")         // Unit: grams
                .build();

        // User logs rice as 150g (same unit!)
        MealIngredient ingredient = createIngredient("rice", 150.0, "g", 195.0, 4.0, 0.5, 42.0);

        when(normalizationService.normalize("rice")).thenReturn("rice");
        when(libraryRepository.findByUserIdOrderByConfidenceScoreDesc(testUser.getId()))
                .thenReturn(List.of(existing));
        when(normalizationService.findBestMatch(eq("rice"), anyList(), anyInt()))
                .thenReturn(Optional.of(existing));

        // When
        learningService.learnFromCorrection(ingredient, testUser);

        // Then: Should calculate weighted average
        ArgumentCaptor<UserIngredientLibrary> captor = ArgumentCaptor.forClass(UserIngredientLibrary.class);
        verify(libraryRepository).save(captor.capture());

        UserIngredientLibrary updated = captor.getValue();

        // Weighted average: 0.7 * 100 + 0.3 * 150 = 70 + 45 = 115
        assertEquals(115.0, updated.getTypicalQuantity(), 0.01,
                "Should calculate weighted average when units match");
        assertEquals("g", updated.getTypicalUnit(), "Unit should remain 'g'");
    }

    /**
     * Helper method to create test ingredients.
     */
    private MealIngredient createIngredient(
            String name,
            Double quantity,
            String unit,
            Double calories,
            Double protein,
            Double fat,
            Double carbs
    ) {
        return MealIngredient.builder()
                .id(UUID.randomUUID())
                .meal(testMeal)
                .ingredientName(name)
                .quantity(quantity)
                .unit(unit)
                .calories(calories)
                .proteinG(protein)
                .fatG(fat)
                .carbohydratesG(carbs)
                .isAiExtracted(true)
                .isUserCorrected(false)
                .displayOrder(0)
                .build();
    }
}
