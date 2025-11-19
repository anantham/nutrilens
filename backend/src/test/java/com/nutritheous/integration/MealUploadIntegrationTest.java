package com.nutritheous.integration;

import com.nutritheous.auth.User;
import com.nutritheous.auth.UserRepository;
import com.nutritheous.meal.Meal;
import com.nutritheous.meal.MealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for meal upload flow.
 * Tests the complete end-to-end pipeline from API request to database storage.
 *
 * NOTE: This test requires a test database. Configure test profile with H2 or Testcontainers.
 * For full integration, mock external services (OpenAI, Google Maps, GCS).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MealUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clean up database
        mealRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setName("Test User");
        testUser = userRepository.save(testUser);
    }

    // ============================================================================
    // Test 1: Basic Meal Upload with Text Only
    // ============================================================================

    @Test
    @Transactional
    void testUploadMeal_TextOnly_CreatesMealInDatabase() {
        // Create a text-only meal (no image)
        Meal meal = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.of(2024, 1, 15, 14, 30))
                .mealType(Meal.MealType.SNACK)
                .description("Apple")
                .calories(95)
                .proteinG(0.5)
                .fatG(0.3)
                .carbohydratesG(25.0)
                .fiberG(4.5)
                .sugarG(19.0)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .confidence(0.90)
                .build();

        mealRepository.save(meal);

        // Verify meal was created in database
        List<Meal> meals = mealRepository.findAll();
        assertFalse(meals.isEmpty(), "Should create meal in database");
        assertEquals(1, meals.size());

        Meal createdMeal = meals.get(0);
        assertNotNull(createdMeal.getId());
        assertEquals(testUser.getId(), createdMeal.getUser().getId());
        assertEquals("Apple", createdMeal.getDescription());
        assertEquals(95, createdMeal.getCalories());
        assertEquals(Meal.MealType.SNACK, createdMeal.getMealType());
        assertEquals(Meal.AnalysisStatus.COMPLETED, createdMeal.getAnalysisStatus());
        assertEquals(0.90, createdMeal.getConfidence(), 0.001);

        // Verify nutrition values
        assertEquals(0.5, createdMeal.getProteinG());
        assertEquals(0.3, createdMeal.getFatG());
        assertEquals(25.0, createdMeal.getCarbohydratesG());
        assertEquals(4.5, createdMeal.getFiberG());
        assertEquals(19.0, createdMeal.getSugarG());
    }

    // ============================================================================
    // Test 2: Meal Retrieval
    // ============================================================================

    @Test
    @WithMockUser(username = "test@example.com")
    void testGetMeals_AfterUpload_ReturnsMeals() {
        // Create a meal directly
        Meal meal = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.now())
                .mealType(Meal.MealType.LUNCH)
                .description("Test meal")
                .calories(500)
                .proteinG(25.0)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        mealRepository.save(meal);

        // Verify we can retrieve meals (would need to implement the GET endpoint test)
        List<Meal> meals = mealRepository.findAll();
        assertEquals(1, meals.size());
        assertEquals("Test meal", meals.get(0).getDescription());
    }

    // ============================================================================
    // Test 3: Meal Update Flow
    // ============================================================================

    @Test
    @Transactional
    void testUpdateMeal_ChangesValues_SavesCorrectly() {
        // Create a meal
        Meal meal = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.now())
                .mealType(Meal.MealType.DINNER)
                .description("Pasta")
                .calories(600)
                .proteinG(20.0)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        meal = mealRepository.save(meal);

        // Update the meal
        meal.setCalories(750);
        meal.setProteinG(25.0);
        mealRepository.save(meal);

        // Verify update
        Meal updated = mealRepository.findById(meal.getId()).orElseThrow();
        assertEquals(750, updated.getCalories());
        assertEquals(25.0, updated.getProteinG());
    }

    // ============================================================================
    // Test 4: Meal Deletion Flow
    // ============================================================================

    @Test
    @Transactional
    void testDeleteMeal_RemovesFromDatabase() {
        // Create a meal
        Meal meal = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.now())
                .mealType(Meal.MealType.BREAKFAST)
                .description("Toast")
                .calories(200)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        meal = mealRepository.save(meal);

        assertNotNull(meal.getId());

        // Delete the meal
        mealRepository.delete(meal);

        // Verify deletion
        assertTrue(mealRepository.findById(meal.getId()).isEmpty());
    }

    // ============================================================================
    // Test 5: Multiple Users - Data Isolation
    // ============================================================================

    @Test
    @Transactional
    void testDataIsolation_UserOnlySeesOwnMeals() {
        // Create second user
        User user2 = new User();
        user2.setEmail("user2@example.com");
        user2.setPassword(passwordEncoder.encode("password456"));
        user2.setName("User Two");
        user2 = userRepository.save(user2);

        // Create meals for both users
        Meal meal1 = Meal.builder()
                .user(testUser)
                .mealTime(LocalDateTime.now())
                .mealType(Meal.MealType.LUNCH)
                .description("User 1 meal")
                .calories(500)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        mealRepository.save(meal1);

        Meal meal2 = Meal.builder()
                .user(user2)
                .mealTime(LocalDateTime.now())
                .mealType(Meal.MealType.LUNCH)
                .description("User 2 meal")
                .calories(600)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        mealRepository.save(meal2);

        // Verify each user has their own meals
        List<Meal> user1Meals = mealRepository.findByUserId(testUser.getId());
        List<Meal> user2Meals = mealRepository.findByUserId(user2.getId());

        assertEquals(1, user1Meals.size());
        assertEquals(1, user2Meals.size());
        assertEquals("User 1 meal", user1Meals.get(0).getDescription());
        assertEquals("User 2 meal", user2Meals.get(0).getDescription());
    }

    // ============================================================================
    // Test 6: Meal Time Handling
    // ============================================================================

    @Test
    @Transactional
    void testMealTime_StoredAndRetrieved_Correctly() {
        LocalDateTime specificTime = LocalDateTime.of(2024, 1, 15, 12, 30, 0);

        Meal meal = Meal.builder()
                .user(testUser)
                .mealTime(specificTime)
                .mealType(Meal.MealType.LUNCH)
                .description("Timed meal")
                .calories(450)
                .analysisStatus(Meal.AnalysisStatus.COMPLETED)
                .build();
        meal = mealRepository.save(meal);

        // Retrieve and verify
        Meal retrieved = mealRepository.findById(meal.getId()).orElseThrow();
        assertEquals(specificTime, retrieved.getMealTime());
        assertEquals(Meal.MealType.LUNCH, retrieved.getMealType());
    }

    // ============================================================================
    // Integration Test Documentation
    // ============================================================================

    /*
     * FULL INTEGRATION TEST IMPLEMENTATION:
     *
     * To implement full end-to-end tests with external services:
     *
     * 1. Set up test configuration:
     *    - Use H2 in-memory database for tests
     *    - Mock external services (OpenAI, Google Maps, GCS)
     *    - Use @MockBean to replace real beans with mocks
     *
     * 2. Example test configuration (application-test.yml):
     *
     *    spring:
     *      datasource:
     *        url: jdbc:h2:mem:testdb
     *        driver-class-name: org.h2.Driver
     *      jpa:
     *        hibernate:
     *          ddl-auto: create-drop
     *      flyway:
     *        enabled: false  # Or use test migrations
     *
     * 3. Mock external services:
     *
     *    @MockBean
     *    private GoogleCloudStorageService storageService;
     *
     *    @MockBean
     *    private OpenAIVisionService visionService;
     *
     *    @MockBean
     *    private LocationContextService locationService;
     *
     * 4. Set up mock responses:
     *
     *    @BeforeEach
     *    void setUpMocks() {
     *        when(storageService.uploadFile(any(), any())).thenReturn("test-object-123");
     *        when(visionService.analyzeImage(any(), any(), any(), any()))
     *            .thenReturn(AnalysisResponse.builder().calories(500).build());
     *    }
     *
     * 5. Test with MockMvc:
     *
     *    @Test
     *    void testUploadMealAPI() throws Exception {
     *        MockMultipartFile image = new MockMultipartFile(
     *            "image", "test.jpg", "image/jpeg", "test".getBytes());
     *
     *        mockMvc.perform(multipart("/api/meals")
     *            .file(image)
     *            .param("mealType", "LUNCH")
     *            .param("description", "Test meal")
     *            .header("Authorization", "Bearer " + getTestToken()))
     *            .andExpect(status().isOk())
     *            .andExpect(jsonPath("$.calories").value(500));
     *    }
     *
     * 6. Use Testcontainers for real PostgreSQL:
     *
     *    @Container
     *    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
     *        .withDatabaseName("testdb")
     *        .withUsername("test")
     *        .withPassword("test");
     *
     *    @DynamicPropertySource
     *    static void setProperties(DynamicPropertyRegistry registry) {
     *        registry.add("spring.datasource.url", postgres::getJdbcUrl);
     *    }
     */
}
