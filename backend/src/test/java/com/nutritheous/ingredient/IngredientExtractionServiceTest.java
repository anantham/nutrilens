package com.nutritheous.ingredient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritheous.auth.User;
import com.nutritheous.common.dto.AnalysisResponse;
import com.nutritheous.ingredient.dto.IngredientRequest;
import com.nutritheous.meal.Meal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IngredientExtractionService.
 * Tests AI parsing, ingredient creation, and user corrections.
 */
@ExtendWith(MockitoExtension.class)
class IngredientExtractionServiceTest {

    @Mock
    private MealIngredientRepository ingredientRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private IngredientExtractionService extractionService;

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
        testMeal.setDescription("Idli with sambar and chutney");
    }

    /**
     * Test: Successfully parse ingredient breakdown from AI response.
     */
    @Test
    void testExtractAndSaveIngredients_WithValidBreakdown_SavesIngredients() {
        // Given: AI response with detailed ingredient breakdown
        String rawAiResponse = """
            {
              "calories": 309,
              "protein_g": 11.0,
              "ingredient_breakdown": [
                {
                  "name": "idli",
                  "category": "grain",
                  "quantity": 2.0,
                  "unit": "piece",
                  "calories": 156,
                  "protein_g": 5.6,
                  "fat_g": 0.6,
                  "carbohydrates_g": 30.0
                },
                {
                  "name": "sambar",
                  "category": "vegetable",
                  "quantity": 150.0,
                  "unit": "g",
                  "calories": 85,
                  "protein_g": 4.2,
                  "fat_g": 2.1,
                  "carbohydrates_g": 12.5
                },
                {
                  "name": "coconut chutney",
                  "category": "condiment",
                  "quantity": 30.0,
                  "unit": "g",
                  "calories": 68,
                  "protein_g": 1.2,
                  "fat_g": 5.8,
                  "carbohydrates_g": 4.2
                }
              ]
            }
            """;

        AnalysisResponse analysisResponse = AnalysisResponse.builder()
                .calories(309)
                .proteinG(11.0)
                .rawAiResponse(rawAiResponse)
                .build();

        when(ingredientRepository.saveAll(anyList())).thenReturn(List.of());

        // When
        extractionService.extractAndSaveIngredients(testMeal, rawAiResponse, analysisResponse, 0.95);

        // Then: Should save 3 ingredients
        ArgumentCaptor<List<MealIngredient>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingredientRepository).saveAll(captor.capture());

        List<MealIngredient> savedIngredients = captor.getValue();
        assertEquals(3, savedIngredients.size());

        // Verify first ingredient (idli)
        MealIngredient idli = savedIngredients.get(0);
        assertEquals("idli", idli.getIngredientName());
        assertEquals("grain", idli.getIngredientCategory());
        assertEquals(2.0, idli.getQuantity());
        assertEquals("piece", idli.getUnit());
        assertEquals(156.0, idli.getCalories());
        assertEquals(5.6, idli.getProteinG());
        assertTrue(idli.getIsAiExtracted());
        assertFalse(idli.getIsUserCorrected());
        assertEquals(0.95, idli.getAiConfidence());
        assertEquals(0, idli.getDisplayOrder());

        // Verify second ingredient (sambar)
        MealIngredient sambar = savedIngredients.get(1);
        assertEquals("sambar", sambar.getIngredientName());
        assertEquals(150.0, sambar.getQuantity());
        assertEquals("g", sambar.getUnit());
        assertEquals(1, sambar.getDisplayOrder());

        // Verify third ingredient (chutney)
        MealIngredient chutney = savedIngredients.get(2);
        assertEquals("coconut chutney", chutney.getIngredientName());
        assertEquals(30.0, chutney.getQuantity());
        assertEquals(2, chutney.getDisplayOrder());
    }

    /**
     * Test: Fallback to basic ingredient creation when no breakdown provided.
     */
    @Test
    void testExtractAndSaveIngredients_NoBreakdown_FallsBackToBasicList() {
        // Given: AI response without ingredient_breakdown field
        String rawAiResponse = """
            {
              "calories": 250,
              "protein_g": 8.0,
              "ingredients": ["rice", "lentils", "vegetables"]
            }
            """;

        AnalysisResponse analysisResponse = AnalysisResponse.builder()
                .calories(250)
                .proteinG(8.0)
                .ingredients(List.of("rice", "lentils", "vegetables"))
                .rawAiResponse(rawAiResponse)
                .build();

        doNothing().when(ingredientRepository).deleteByMealId(testMeal.getId());
        when(ingredientRepository.saveAll(anyList())).thenReturn(List.of());

        // When
        extractionService.extractAndSaveIngredients(testMeal, rawAiResponse, analysisResponse, 0.85);

        // Then: Should create basic ingredients from simple list
        ArgumentCaptor<List<MealIngredient>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingredientRepository).saveAll(captor.capture());

        List<MealIngredient> savedIngredients = captor.getValue();
        assertEquals(3, savedIngredients.size());

        // Verify ingredients have names but no detailed nutrition
        assertEquals("rice", savedIngredients.get(0).getIngredientName());
        assertEquals("lentils", savedIngredients.get(1).getIngredientName());
        assertEquals("vegetables", savedIngredients.get(2).getIngredientName());

        // All should be AI extracted with lower confidence (fallback mode)
        savedIngredients.forEach(ingredient -> {
            assertTrue(ingredient.getIsAiExtracted());
            assertFalse(ingredient.getIsUserCorrected());
        });
    }

    /**
     * Test: Handle empty ingredient breakdown gracefully.
     */
    @Test
    void testExtractAndSaveIngredients_EmptyBreakdown_DoesNotSave() {
        // Given: AI response with empty ingredient_breakdown array
        String rawAiResponse = """
            {
              "calories": 200,
              "ingredient_breakdown": []
            }
            """;

        AnalysisResponse analysisResponse = AnalysisResponse.builder()
                .calories(200)
                .rawAiResponse(rawAiResponse)
                .build();

        // When
        extractionService.extractAndSaveIngredients(testMeal, rawAiResponse, analysisResponse, 0.90);

        // Then: Should not attempt to save empty list
        verify(ingredientRepository, never()).saveAll(any());
    }

    /**
     * Test: Handle malformed JSON gracefully.
     */
    @Test
    void testExtractAndSaveIngredients_MalformedJson_DoesNotCrash() {
        // Given: Invalid JSON
        String rawAiResponse = "{ invalid json }";

        AnalysisResponse analysisResponse = AnalysisResponse.builder()
                .calories(100)
                .rawAiResponse(rawAiResponse)
                .build();

        // When & Then: Should not throw exception
        assertDoesNotThrow(() ->
                extractionService.extractAndSaveIngredients(testMeal, rawAiResponse, analysisResponse, 0.80)
        );

        // Should not save anything
        verify(ingredientRepository, never()).saveAll(any());
    }

    /**
     * Test: Update existing ingredient with user corrections.
     */
    @Test
    void testUpdateIngredient_MarksAsUserCorrected() {
        // Given: Existing ingredient
        UUID ingredientId = UUID.randomUUID();
        MealIngredient existing = MealIngredient.builder()
                .id(ingredientId)
                .meal(testMeal)
                .ingredientName("chutney")
                .quantity(30.0)
                .unit("g")
                .calories(68.0)
                .isAiExtracted(true)
                .isUserCorrected(false)
                .build();

        when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(existing));
        when(ingredientRepository.save(any(MealIngredient.class))).thenAnswer(i -> i.getArgument(0));

        // Updated values from user
        IngredientRequest updateRequest = IngredientRequest.builder()
                .ingredientName("chutney")
                .quantity(50.0)  // User corrected from 30g to 50g
                .unit("g")
                .calories(113.3)  // Recalculated
                .build();

        // When
        MealIngredient result = extractionService.updateIngredient(ingredientId, updateRequest);

        // Then: Should mark as user corrected
        assertTrue(result.getIsUserCorrected());
        assertEquals(50.0, result.getQuantity());
        assertEquals(113.3, result.getCalories(), 0.1);

        verify(ingredientRepository).save(argThat(ingredient ->
                ingredient.getIsUserCorrected() &&
                ingredient.getQuantity() == 50.0
        ));
    }

    /**
     * Test: Add new ingredient to meal.
     */
    @Test
    void testAddIngredient_CreatesNewIngredient() {
        // Given: New ingredient to add
        IngredientRequest newRequest = IngredientRequest.builder()
                .ingredientName("ghee")
                .quantity(5.0)
                .unit("g")
                .calories(45.0)
                .fatG(5.0)
                .build();

        // Create 3 ingredients with display orders 0, 1, 2
        MealIngredient ing1 = createTestIngredient("idli", 156.0, 5.6, 0.6, 30.0);
        ing1.setDisplayOrder(0);
        MealIngredient ing2 = createTestIngredient("sambar", 85.0, 4.2, 2.1, 12.5);
        ing2.setDisplayOrder(1);
        MealIngredient ing3 = createTestIngredient("chutney", 68.0, 1.2, 5.8, 4.2);
        ing3.setDisplayOrder(2);

        when(ingredientRepository.findByMealIdOrderByDisplayOrderAsc(testMeal.getId()))
                .thenReturn(List.of(ing1, ing2, ing3));
        when(ingredientRepository.save(any(MealIngredient.class))).thenAnswer(i -> i.getArgument(0));

        // When
        MealIngredient result = extractionService.addIngredient(testMeal, newRequest);

        // Then: Should be marked as user-added (not AI extracted)
        assertFalse(result.getIsAiExtracted());
        assertFalse(result.getIsUserCorrected());
        assertEquals(3, result.getDisplayOrder());  // Should be last (after 3 existing with order 0,1,2)
        assertEquals(testMeal.getId(), result.getMeal().getId());

        verify(ingredientRepository).save(argThat(ingredient ->
                !ingredient.getIsAiExtracted() &&
                "ghee".equals(ingredient.getIngredientName())
        ));
    }

    /**
     * Test: Delete ingredient from meal.
     */
    @Test
    void testDeleteIngredient_RemovesIngredient() {
        // Given: Existing ingredient to delete
        UUID ingredientId = UUID.randomUUID();

        // When
        extractionService.deleteIngredient(ingredientId);

        // Then: Should delete the ingredient
        verify(ingredientRepository).deleteById(ingredientId);
    }

    /**
     * Test: Get ingredients breakdown with aggregated totals.
     */
    @Test
    void testGetIngredients_CalculatesTotals() {
        // Given: Meal with multiple ingredients
        List<MealIngredient> ingredients = List.of(
                createTestIngredient("idli", 156.0, 5.6, 0.6, 30.0),
                createTestIngredient("sambar", 85.0, 4.2, 2.1, 12.5),
                createTestIngredient("chutney", 68.0, 1.2, 5.8, 4.2)
        );

        when(ingredientRepository.findByMealIdOrderByDisplayOrderAsc(testMeal.getId()))
                .thenReturn(ingredients);

        // When
        List<MealIngredient> result = extractionService.getIngredientsForMeal(testMeal.getId());

        // Then: Should return all ingredients
        assertEquals(3, result.size());

        // Verify totals (could be calculated in a response DTO)
        double totalCalories = result.stream()
                .mapToDouble(MealIngredient::getCalories)
                .sum();
        assertEquals(309.0, totalCalories, 0.1);  // 156 + 85 + 68
    }

    /**
     * Helper method to create test ingredients.
     */
    private MealIngredient createTestIngredient(
            String name,
            Double calories,
            Double protein,
            Double fat,
            Double carbs
    ) {
        return MealIngredient.builder()
                .id(UUID.randomUUID())
                .meal(testMeal)
                .ingredientName(name)
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
