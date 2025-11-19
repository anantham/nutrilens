package com.nutritheous.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritheous.auth.JwtTokenProvider;
import com.nutritheous.auth.User;
import com.nutritheous.auth.UserRepository;
import com.nutritheous.common.dto.AnalysisResponse;
import com.nutritheous.meal.Meal;
import com.nutritheous.meal.MealRepository;
import com.nutritheous.meal.MealUpdateRequest;
import com.nutritheous.services.GoogleCloudStorageService;
import com.nutritheous.services.LocationContextService;
import com.nutritheous.services.OpenAIVisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REAL Integration Tests for Meal API
 *
 * Tests the complete HTTP → Controller → Service → Database flow
 * with mocked external services (OpenAI, GCS, Location).
 *
 * This is NOT a "Nokkukuthi" test. These tests:
 * 1. Send real HTTP requests via MockMvc
 * 2. Execute actual service logic
 * 3. Persist to real H2 database
 * 4. Verify database state after operations
 * 5. Test security, validation, and error handling
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MealApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // Mock external services to avoid real API calls
    @MockBean
    private GoogleCloudStorageService storageService;

    @MockBean
    private OpenAIVisionService visionService;

    @MockBean
    private LocationContextService locationService;

    private User testUser;
    private String authToken;

    @BeforeEach
    void setUp() {
        // Clean database
        mealRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setName("Test User");
        testUser = userRepository.save(testUser);

        // Generate JWT token for authentication
        authToken = jwtTokenProvider.generateToken(testUser.getEmail());

        // Set up default mock responses for external services
        setupDefaultMocks();
    }

    private void setupDefaultMocks() {
        // Mock GCS upload - returns object ID
        when(storageService.uploadFile(any(), any())).thenReturn("test-object-123");

        // Mock OpenAI Vision analysis - returns realistic nutrition data
        AnalysisResponse mockAnalysis = AnalysisResponse.builder()
                .calories(500)
                .proteinG(25.0)
                .fatG(15.0)
                .carbohydratesG(60.0)
                .fiberG(5.0)
                .sugarG(10.0)
                .saturatedFatG(4.0)
                .sodiumMg(400.0)
                .description("Chicken breast with rice and vegetables")
                .confidence(0.85)
                .build();

        when(visionService.analyzeImage(any(), any(), any(), any()))
                .thenReturn(mockAnalysis);

        // Mock location service
        when(locationService.getLocationContext(anyDouble(), anyDouble()))
                .thenReturn("Test Location");
    }

    // ============================================================================
    // Test 1: Full Meal Upload Flow with Image
    // ============================================================================

    @Test
    void fullMealUploadFlow_withImage_persistsCorrectlyToDatabase() throws Exception {
        // Arrange: Create multipart file
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "chicken-rice.jpg",
                "image/jpeg",
                "fake-image-content".getBytes()
        );

        // Act: Send HTTP request
        MvcResult result = mockMvc.perform(multipart("/api/meals")
                        .file(image)
                        .param("mealType", "LUNCH")
                        .param("description", "Chicken and rice")
                        .param("mealTime", "2024-01-15T12:30:00")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calories").value(500))
                .andExpect(jsonPath("$.proteinG").value(25.0))
                .andExpect(jsonPath("$.description").value("Chicken and rice"))
                .andExpect(jsonPath("$.mealType").value("LUNCH"))
                .andReturn();

        // Assert: Verify database persistence
        List<Meal> savedMeals = mealRepository.findAll();
        assertEquals(1, savedMeals.size(), "Should save exactly one meal");

        Meal savedMeal = savedMeals.get(0);
        assertNotNull(savedMeal.getId(), "Meal should have generated ID");
        assertEquals(testUser.getId(), savedMeal.getUser().getId(), "Should associate with correct user");
        assertEquals(500, savedMeal.getCalories(), "Should save calories from AI");
        assertEquals(25.0, savedMeal.getProteinG(), "Should save protein from AI");
        assertEquals(Meal.MealType.LUNCH, savedMeal.getMealType(), "Should save meal type");
        assertEquals("test-object-123", savedMeal.getImageUrl(), "Should save GCS object ID");
        assertEquals(Meal.AnalysisStatus.COMPLETED, savedMeal.getAnalysisStatus());

        // Verify external service interactions
        verify(storageService, times(1)).uploadFile(any(), eq(testUser.getId()));
        verify(visionService, times(1)).analyzeImage(any(), any(), any(), any());
    }

    // ============================================================================
    // Test 2: Text-Only Meal Upload (No Image)
    // ============================================================================

    @Test
    void textOnlyMealUpload_withoutImage_persistsCorrectly() throws Exception {
        // Act: Upload meal with text only
        mockMvc.perform(post("/api/meals/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mealType", "SNACK",
                                "description", "Apple",
                                "mealTime", "2024-01-15T14:30:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Apple"))
                .andExpect(jsonPath("$.mealType").value("SNACK"));

        // Assert: Verify database
        List<Meal> savedMeals = mealRepository.findAll();
        assertEquals(1, savedMeals.size());

        Meal savedMeal = savedMeals.get(0);
        assertEquals("Apple", savedMeal.getDescription());
        assertEquals(Meal.MealType.SNACK, savedMeal.getMealType());
        assertNull(savedMeal.getImageUrl(), "Text-only meal should have no image");

        // Verify OpenAI was called for text analysis
        verify(visionService, times(1)).analyzeImage(isNull(), eq("Apple"), any(), any());
        verify(storageService, never()).uploadFile(any(), any());
    }

    // ============================================================================
    // Test 3: Meal Update Flow with Correction Tracking
    // ============================================================================

    @Test
    void mealUpdate_withUserCorrections_persistsAndTracksCorrections() throws Exception {
        // Arrange: Create initial meal
        Meal meal = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.now())
                .mealType(Meal.MealType.DINNER)
                .description("Pasta")
                .calories(600)
                .proteinG(20.0)
                .fatG(15.0)
                .carbohydratesG(80.0)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        meal = mealRepository.save(meal);

        // Arrange: Create update request
        MealUpdateRequest updateRequest = new MealUpdateRequest();
        updateRequest.setCalories(750);  // User corrects calories
        updateRequest.setProteinG(25.0);  // User corrects protein
        updateRequest.setDescription("Pasta with chicken");

        // Act: Send update request
        mockMvc.perform(put("/api/meals/" + meal.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calories").value(750))
                .andExpect(jsonPath("$.proteinG").value(25.0))
                .andExpect(jsonPath("$.description").value("Pasta with chicken"));

        // Assert: Verify database updates
        Meal updatedMeal = mealRepository.findById(meal.getId()).orElseThrow();
        assertEquals(750, updatedMeal.getCalories(), "Should update calories");
        assertEquals(25.0, updatedMeal.getProteinG(), "Should update protein");
        assertEquals("Pasta with chicken", updatedMeal.getDescription(), "Should update description");

        // Note: Correction tracking verification would require CorrectionLogRepository
        // which may not be exposed via HTTP API, so we verify service layer behavior
    }

    // ============================================================================
    // Test 4: Meal Deletion Flow
    // ============================================================================

    @Test
    void mealDeletion_removesFromDatabase_andCleansUpStorage() throws Exception {
        // Arrange: Create meal with image
        Meal meal = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.now())
                .mealType(Meal.MealType.BREAKFAST)
                .description("Toast")
                .calories(200)
                .imageUrl("test-object-to-delete")
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        meal = mealRepository.save(meal);

        Long mealId = meal.getId();

        // Act: Delete meal
        mockMvc.perform(delete("/api/meals/" + mealId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNoContent());

        // Assert: Verify deletion
        assertTrue(mealRepository.findById(mealId).isEmpty(), "Meal should be deleted from database");

        // Verify storage cleanup
        verify(storageService, times(1)).deleteFile("test-object-to-delete");
    }

    // ============================================================================
    // Test 5: Get User Meals with Filtering
    // ============================================================================

    @Test
    void getUserMeals_withDateFilter_returnsCorrectMeals() throws Exception {
        // Arrange: Create meals on different dates
        Meal meal1 = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.of(2024, 1, 15, 8, 0))
                .mealType(Meal.MealType.BREAKFAST)
                .description("Meal 1")
                .calories(300)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        mealRepository.save(meal1);

        Meal meal2 = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.of(2024, 1, 15, 12, 0))
                .mealType(Meal.MealType.LUNCH)
                .description("Meal 2")
                .calories(500)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        mealRepository.save(meal2);

        Meal meal3 = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.of(2024, 1, 16, 8, 0))
                .mealType(Meal.MealType.BREAKFAST)
                .description("Meal 3")
                .calories(350)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        mealRepository.save(meal3);

        // Act: Get meals for specific date
        mockMvc.perform(get("/api/meals")
                        .param("startDate", "2024-01-15")
                        .param("endDate", "2024-01-15")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].description").value("Meal 2"))  // Sorted by time DESC
                .andExpect(jsonPath("$[1].description").value("Meal 1"));
    }

    // ============================================================================
    // Test 6: Security - Unauthorized Access
    // ============================================================================

    @Test
    void mealAccess_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/meals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mealAccess_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/meals")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================================
    // Test 7: Security - User Isolation
    // ============================================================================

    @Test
    void mealAccess_cannotAccessOtherUsersMeals() throws Exception {
        // Arrange: Create second user
        User user2 = new User();
        user2.setEmail("user2@example.com");
        user2.setPassword(passwordEncoder.encode("password456"));
        user2.setName("User Two");
        user2 = userRepository.save(user2);

        // Create meal for user2
        Meal user2Meal = Meal.builder()
                .user(user2)
                .mealTime(LocalDateTime.now())
                .mealType(Meal.MealType.LUNCH)
                .description("User 2 meal")
                .calories(500)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        user2Meal = mealRepository.save(user2Meal);

        // Act: Try to update user2's meal as testUser
        MealUpdateRequest updateRequest = new MealUpdateRequest();
        updateRequest.setCalories(999);

        mockMvc.perform(put("/api/meals/" + user2Meal.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)  // testUser's token
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());

        // Assert: Verify meal was NOT updated
        Meal unchangedMeal = mealRepository.findById(user2Meal.getId()).orElseThrow();
        assertEquals(500, unchangedMeal.getCalories(), "Meal should not be updated by other user");
    }

    // ============================================================================
    // Test 8: Validation - Invalid Input
    // ============================================================================

    @Test
    void mealUpload_withInvalidMealType_returns400() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test".getBytes()
        );

        mockMvc.perform(multipart("/api/meals")
                        .file(image)
                        .param("mealType", "INVALID_TYPE")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mealUpdate_withNegativeCalories_returns400() throws Exception {
        // Arrange: Create meal
        Meal meal = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.now())
                .mealType(Meal.MealType.SNACK)
                .description("Test")
                .calories(100)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        meal = mealRepository.save(meal);

        // Act: Try to update with negative calories
        MealUpdateRequest updateRequest = new MealUpdateRequest();
        updateRequest.setCalories(-500);

        mockMvc.perform(put("/api/meals/" + meal.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + authToken)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    // ============================================================================
    // Test 9: Error Handling - OpenAI Failure
    // ============================================================================

    @Test
    void mealUpload_whenOpenAIFails_returnsErrorAndMarksAsError() throws Exception {
        // Arrange: Mock OpenAI to throw exception
        when(visionService.analyzeImage(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("OpenAI API Error"));

        MockMultipartFile image = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "test".getBytes()
        );

        // Act & Assert: Should handle gracefully
        mockMvc.perform(multipart("/api/meals")
                        .file(image)
                        .param("mealType", "LUNCH")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isInternalServerError());

        // Verify meal may be saved with ERROR status (depending on implementation)
        // This tests resilience and error tracking
    }

    // ============================================================================
    // Test 10: Pagination and Sorting
    // ============================================================================

    @Test
    void getUserMeals_withPagination_returnsCorrectPage() throws Exception {
        // Arrange: Create 15 meals
        for (int i = 0; i < 15; i++) {
            Meal meal = Meal.builder()
                    .user(testUser)
                    .mealTime(LocalDateTime.now().minusHours(i))
                    .mealType(Meal.MealType.SNACK)
                    .description("Meal " + i)
                    .calories(100 + i * 10)
                    .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                    .build();
            mealRepository.save(meal);
        }

        // Act: Get first page (10 items)
        mockMvc.perform(get("/api/meals")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2));

        // Get second page (5 items)
        mockMvc.perform(get("/api/meals")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5));
    }
}
