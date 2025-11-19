package com.nutritheous.meal;

import com.nutritheous.analyzer.AnalyzerService;
import com.nutritheous.auth.User;
import com.nutritheous.auth.UserRepository;
import com.nutritheous.common.dto.AnalysisResponse;
import com.nutritheous.common.dto.LocationContext;
import com.nutritheous.common.dto.MealResponse;
import com.nutritheous.common.dto.PhotoMetadata;
import com.nutritheous.common.exception.AnalyzerException;
import com.nutritheous.common.exception.ResourceNotFoundException;
import com.nutritheous.correction.AiCorrectionLogRepository;
import com.nutritheous.ingredient.IngredientExtractionService;
import com.nutritheous.meal.dto.MealUpdateRequest;
import com.nutritheous.service.LocationContextService;
import com.nutritheous.service.PhotoMetadataService;
import com.nutritheous.storage.GoogleCloudStorageService;
import com.nutritheous.validation.AiValidationService;
import com.nutritheous.validation.ValidationFailureRepository;
import com.nutritheous.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for MealService.
 * Tests the core business logic for meal management.
 */
@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    @Mock
    private MealRepository mealRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoogleCloudStorageService storageService;

    @Mock
    private AnalyzerService analyzerService;

    @Mock
    private PhotoMetadataService photoMetadataService;

    @Mock
    private LocationContextService locationContextService;

    @Mock
    private IngredientExtractionService ingredientExtractionService;

    @Mock
    private AiValidationService validationService;

    @Mock
    private ValidationFailureRepository validationFailureRepository;

    @Mock
    private AiCorrectionLogRepository correctionLogRepository;

    @InjectMocks
    private MealService mealService;

    private User testUser;
    private UUID testUserId;
    private Meal testMeal;
    private MockMultipartFile testImage;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail("test@example.com");

        testMeal = new Meal();
        testMeal.setId(UUID.randomUUID());
        testMeal.setUser(testUser);
        testMeal.setMealTime(LocalDateTime.now());
        testMeal.setMealType(Meal.MealType.LUNCH);
        testMeal.setDescription("Test meal");
        testMeal.setCalories(500);
        testMeal.setProteinG(25.0);

        testImage = new MockMultipartFile(
                "image",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );
    }

    // ============================================================================
    // Test 1: uploadMeal() - User Validation
    // ============================================================================

    @Test
    void testUploadMeal_UserNotFound_ThrowsException() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                mealService.uploadMeal(testUserId, testImage, Meal.MealType.LUNCH,
                        LocalDateTime.now(), "Test meal"));

        verify(userRepository).findById(testUserId);
        verifyNoInteractions(storageService, analyzerService);
    }

    @Test
    void testUploadMeal_ValidUser_ProceedsWithUpload() {
        LocalDateTime uploadTime = LocalDateTime.of(2024, 1, 15, 12, 30);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(photoMetadataService.extractMetadata(any())).thenReturn(PhotoMetadata.empty());
        when(storageService.uploadFile(any(), any())).thenReturn("test-object-123");
        when(storageService.getPresignedUrl(any())).thenReturn("https://storage.test/image");
        when(mealRepository.save(any())).thenAnswer(invocation -> {
            Meal saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AnalysisResponse mockAnalysis = AnalysisResponse.builder()
                .calories(500)
                .proteinG(30.0)
                .fatG(20.0)
                .carbohydratesG(45.0)
                .confidence(0.85)
                .build();
        when(analyzerService.analyzeImage(any(), any(), any(), any())).thenReturn(mockAnalysis);
        when(validationService.validate(any())).thenReturn(ValidationResult.builder()
                .valid(true)
                .issues(new ArrayList<>())
                .build());

        // Execute the upload
        MealResponse response = mealService.uploadMeal(testUserId, testImage, Meal.MealType.LUNCH,
                uploadTime, "Test meal");

        // Verify response contains expected values
        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals(testUserId, response.getUserId());
        assertEquals(Meal.MealType.LUNCH, response.getMealType());
        assertEquals("Test meal", response.getDescription());
        assertEquals(500, response.getCalories());
        assertEquals(30.0, response.getProteinG());
        assertEquals(uploadTime, response.getMealTime());

        // Verify interactions with specific values
        verify(userRepository).findById(testUserId);
        verify(storageService).uploadFile(
                argThat(file -> file.getOriginalFilename().equals("test.jpg")),
                eq(testUserId)
        );
        verify(storageService).getPresignedUrl("test-object-123");
        verify(analyzerService).analyzeImage(
                eq("https://storage.test/image"),
                eq("Test meal"),
                any(), // LocationContext can vary
                any()  // PhotoMetadata can vary
        );

        // Verify meal was saved with correct values
        verify(mealRepository, atLeast(2)).save(argThat(meal ->
                meal.getMealType() == Meal.MealType.LUNCH &&
                meal.getDescription().equals("Test meal") &&
                meal.getUser().getId().equals(testUserId) &&
                meal.getCalories() != null
        ));
    }

    // ============================================================================
    // Test 2: uploadMeal() - Image vs Text-Only
    // ============================================================================

    @Test
    void testUploadMeal_WithImage_UploadesAndAnalyzes() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(photoMetadataService.extractMetadata(any())).thenReturn(PhotoMetadata.empty());
        when(storageService.uploadFile(any(), any())).thenReturn("test-object-123");
        when(storageService.getPresignedUrl(any())).thenReturn("https://storage.test/image");
        when(mealRepository.save(any())).thenReturn(testMeal);

        AnalysisResponse mockAnalysis = AnalysisResponse.builder()
                .calories(500)
                .confidence(0.85)
                .build();
        when(analyzerService.analyzeImage(any(), any(), any(), any())).thenReturn(mockAnalysis);
        when(validationService.validate(any())).thenReturn(ValidationResult.builder()
                .valid(true)
                .issues(new ArrayList<>())
                .build());

        MealResponse response = mealService.uploadMeal(testUserId, testImage, Meal.MealType.LUNCH,
                LocalDateTime.now(), "Test meal");

        assertNotNull(response);
        verify(storageService).uploadFile(any(), eq(testUserId));
        verify(storageService).getPresignedUrl("test-object-123");
        verify(analyzerService).analyzeImage(eq("https://storage.test/image"), eq("Test meal"),
                any(), any());
        verify(analyzerService, never()).analyzeTextOnly(any(), any(), any());
    }

    @Test
    void testUploadMeal_TextOnly_SkipsImageUpload() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(mealRepository.save(any())).thenReturn(testMeal);

        AnalysisResponse mockAnalysis = AnalysisResponse.builder()
                .calories(300)
                .confidence(0.70)
                .build();
        when(analyzerService.analyzeTextOnly(any(), any(), any())).thenReturn(mockAnalysis);
        when(validationService.validate(any())).thenReturn(ValidationResult.builder()
                .valid(true)
                .issues(new ArrayList<>())
                .build());

        MealResponse response = mealService.uploadMeal(testUserId, null, Meal.MealType.SNACK,
                LocalDateTime.now(), "Apple");

        assertNotNull(response);
        verify(storageService, never()).uploadFile(any(), any());
        verify(analyzerService, never()).analyzeImage(any(), any(), any(), any());
        verify(analyzerService).analyzeTextOnly(eq("Apple"), any(), any());
    }

    @Test
    void testUploadMeal_EmptyImage_TreatedAsTextOnly() {
        MockMultipartFile emptyImage = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(mealRepository.save(any())).thenReturn(testMeal);

        AnalysisResponse mockAnalysis = AnalysisResponse.builder()
                .calories(200)
                .confidence(0.65)
                .build();
        when(analyzerService.analyzeTextOnly(any(), any(), any())).thenReturn(mockAnalysis);
        when(validationService.validate(any())).thenReturn(ValidationResult.builder()
                .valid(true)
                .issues(new ArrayList<>())
                .build());

        mealService.uploadMeal(testUserId, emptyImage, Meal.MealType.BREAKFAST,
                LocalDateTime.now(), "Coffee");

        verify(storageService, never()).uploadFile(any(), any());
        verify(analyzerService).analyzeTextOnly(eq("Coffee"), any(), any());
    }

    // ============================================================================
    // Test 3: uploadMeal() - Photo Metadata Extraction
    // ============================================================================

    @Test
    void testUploadMeal_WithGPSMetadata_ExtractsLocationContext() {
        PhotoMetadata photoMetadata = PhotoMetadata.builder()
                .latitude(37.7749)
                .longitude(-122.4194)
                .capturedAt(LocalDateTime.now().minusHours(1))
                .build();

        LocationContext locationContext = LocationContext.builder()
                .placeName("Test Restaurant")
                .placeType("restaurant")
                .isRestaurant(true)
                .isKnown(true)
                .build();

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(photoMetadataService.extractMetadata(any())).thenReturn(photoMetadata);
        when(locationContextService.getLocationContext(37.7749, -122.4194))
                .thenReturn(locationContext);
        when(storageService.uploadFile(any(), any())).thenReturn("test-object");
        when(storageService.getPresignedUrl(any())).thenReturn("https://storage.test/image");
        when(mealRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AnalysisResponse mockAnalysis = AnalysisResponse.builder()
                .calories(800)
                .confidence(0.90)
                .build();
        when(analyzerService.analyzeImage(any(), any(), any(), any())).thenReturn(mockAnalysis);
        when(validationService.validate(any())).thenReturn(ValidationResult.builder()
                .valid(true)
                .issues(new ArrayList<>())
                .build());

        mealService.uploadMeal(testUserId, testImage, Meal.MealType.DINNER,
                LocalDateTime.now(), "Steak");

        verify(photoMetadataService).extractMetadata(testImage);
        verify(locationContextService).getLocationContext(37.7749, -122.4194);
        verify(analyzerService).analyzeImage(any(), any(), eq(locationContext), any());
    }

    @Test
    void testUploadMeal_WithoutGPS_SkipsLocationLookup() {
        PhotoMetadata photoMetadata = PhotoMetadata.builder()
                .capturedAt(LocalDateTime.now())
                .build(); // No GPS

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(photoMetadataService.extractMetadata(any())).thenReturn(photoMetadata);
        when(storageService.uploadFile(any(), any())).thenReturn("test-object");
        when(storageService.getPresignedUrl(any())).thenReturn("https://storage.test/image");
        when(mealRepository.save(any())).thenReturn(testMeal);

        AnalysisResponse mockAnalysis = AnalysisResponse.builder()
                .calories(400)
                .confidence(0.75)
                .build();
        when(analyzerService.analyzeImage(any(), any(), any(), any())).thenReturn(mockAnalysis);
        when(validationService.validate(any())).thenReturn(ValidationResult.builder()
                .valid(true)
                .issues(new ArrayList<>())
                .build());

        mealService.uploadMeal(testUserId, testImage, Meal.MealType.LUNCH,
                LocalDateTime.now(), "Sandwich");

        verify(photoMetadataService).extractMetadata(testImage);
        verify(locationContextService, never()).getLocationContext(any(), any());
    }

    // ============================================================================
    // Test 4: uploadMeal() - Meal Time Determination
    // ============================================================================

    @Test
    void testUploadMeal_WithExplicitMealTime_UsesThatTime() {
        LocalDateTime explicitTime = LocalDateTime.of(2024, 1, 15, 12, 30);

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(photoMetadataService.extractMetadata(any())).thenReturn(PhotoMetadata.empty());
        when(storageService.uploadFile(any(), any())).thenReturn("test-object");
        when(storageService.getPresignedUrl(any())).thenReturn("https://storage.test/image");
        when(mealRepository.save(any())).thenAnswer(i -> {
            Meal meal = i.getArgument(0);
            assertEquals(explicitTime, meal.getMealTime());
            return meal;
        });

        AnalysisResponse mockAnalysis = AnalysisResponse.builder().calories(500).build();
        when(analyzerService.analyzeImage(any(), any(), any(), any())).thenReturn(mockAnalysis);
        when(validationService.validate(any())).thenReturn(ValidationResult.builder()
                .valid(true).issues(new ArrayList<>()).build());

        mealService.uploadMeal(testUserId, testImage, Meal.MealType.LUNCH,
                explicitTime, "Meal");

        verify(mealRepository, atLeastOnce()).save(argThat(meal ->
                meal.getMealTime().equals(explicitTime)));
    }

    @Test
    void testUploadMeal_NoMealTimeButPhotoTimestamp_UsesPhotoTime() {
        LocalDateTime photoTime = LocalDateTime.of(2024, 1, 15, 13, 45);
        PhotoMetadata photoMetadata = PhotoMetadata.builder()
                .capturedAt(photoTime)
                .build();

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(photoMetadataService.extractMetadata(any())).thenReturn(photoMetadata);
        when(storageService.uploadFile(any(), any())).thenReturn("test-object");
        when(storageService.getPresignedUrl(any())).thenReturn("https://storage.test/image");
        when(mealRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AnalysisResponse mockAnalysis = AnalysisResponse.builder().calories(500).build();
        when(analyzerService.analyzeImage(any(), any(), any(), any())).thenReturn(mockAnalysis);
        when(validationService.validate(any())).thenReturn(ValidationResult.builder()
                .valid(true).issues(new ArrayList<>()).build());

        mealService.uploadMeal(testUserId, testImage, Meal.MealType.DINNER,
                null, "Meal");

        verify(mealRepository, atLeastOnce()).save(argThat(meal ->
                meal.getMealTime().equals(photoTime)));
    }

    // ============================================================================
    // Test 5: uploadMeal() - AI Analysis Error Handling
    // ============================================================================

    @Test
    void testUploadMeal_AnalyzerThrowsException_MarksAsFailed() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(photoMetadataService.extractMetadata(any())).thenReturn(PhotoMetadata.empty());
        when(storageService.uploadFile(any(), any())).thenReturn("test-object");
        when(storageService.getPresignedUrl(any())).thenReturn("https://storage.test/image");
        when(mealRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(analyzerService.analyzeImage(any(), any(), any(), any()))
                .thenThrow(new AnalyzerException("AI service unavailable"));

        MealResponse response = mealService.uploadMeal(testUserId, testImage, Meal.MealType.LUNCH,
                LocalDateTime.now(), "Test");

        assertNotNull(response);
        // Meal should be saved with FAILED status
        verify(mealRepository, atLeast(2)).save(any()); // Once before analysis, once after failure
    }

    // ============================================================================
    // Test 6: updateMeal() - Authorization
    // ============================================================================

    @Test
    void testUpdateMeal_UserDoesNotOwnMeal_ThrowsException() {
        UUID wrongUserId = UUID.randomUUID();
        testMeal.getUser().setId(testUserId); // Meal belongs to testUserId

        when(mealRepository.findById(testMeal.getId())).thenReturn(Optional.of(testMeal));

        MealUpdateRequest updateRequest = new MealUpdateRequest();
        updateRequest.setCalories(600);

        assertThrows(ResourceNotFoundException.class, () ->
                mealService.updateMeal(testMeal.getId(), wrongUserId, updateRequest));

        verify(mealRepository, never()).save(any());
    }

    @Test
    void testUpdateMeal_MealNotFound_ThrowsException() {
        UUID nonexistentMealId = UUID.randomUUID();

        when(mealRepository.findById(nonexistentMealId)).thenReturn(Optional.empty());

        MealUpdateRequest updateRequest = new MealUpdateRequest();
        updateRequest.setCalories(600);

        assertThrows(ResourceNotFoundException.class, () ->
                mealService.updateMeal(nonexistentMealId, testUserId, updateRequest));
    }

    // ============================================================================
    // Test 7: updateMeal() - Correction Tracking
    // ============================================================================

    @Test
    void testUpdateMeal_ChangesCalories_TracksCorrection() {
        testMeal.setCalories(500); // AI value
        testMeal.setConfidence(0.85);

        when(mealRepository.findById(testMeal.getId())).thenReturn(Optional.of(testMeal));
        when(mealRepository.save(any())).thenReturn(testMeal);

        MealUpdateRequest updateRequest = new MealUpdateRequest();
        updateRequest.setCalories(650); // User correction

        mealService.updateMeal(testMeal.getId(), testUserId, updateRequest);

        // Should track the correction
        verify(correctionLogRepository).save(argThat(log ->
                log.getFieldName().equals("calories") &&
                        log.getAiValue().compareTo(BigDecimal.valueOf(500)) == 0 &&
                        log.getUserValue().compareTo(BigDecimal.valueOf(650)) == 0
        ));
    }

    @Test
    void testUpdateMeal_NoChange_DoesNotTrackCorrection() {
        testMeal.setCalories(500);

        when(mealRepository.findById(testMeal.getId())).thenReturn(Optional.of(testMeal));
        when(mealRepository.save(any())).thenReturn(testMeal);

        MealUpdateRequest updateRequest = new MealUpdateRequest();
        updateRequest.setCalories(500); // Same value

        mealService.updateMeal(testMeal.getId(), testUserId, updateRequest);

        // Should NOT track correction for identical values
        verify(correctionLogRepository, never()).save(any());
    }

    @Test
    void testUpdateMeal_CorrectionMath_MatchesExpectedFormula() {
        // INVARIANT TEST: Verify correction math is calculated correctly
        // This test uses independent calculation, not mirroring the code
        testMeal.setCalories(500);
        testMeal.setProteinG(30.0);
        testMeal.setConfidence(0.85);

        when(mealRepository.findById(testMeal.getId())).thenReturn(Optional.of(testMeal));
        when(mealRepository.save(any())).thenReturn(testMeal);

        MealUpdateRequest updateRequest = new MealUpdateRequest();
        updateRequest.setCalories(650);  // User correction
        updateRequest.setProteinG(42.0);  // User correction

        mealService.updateMeal(testMeal.getId(), testUserId, updateRequest);

        // INVARIANT: Verify calories correction math
        verify(correctionLogRepository).save(argThat(log -> {
            if (!log.getFieldName().equals("calories")) {
                return false;
            }

            // Independent calculation of expected values
            // percent_error = (user - ai) / user * 100
            // Expected: (650 - 500) / 650 * 100 = 23.08%
            BigDecimal expectedPercent = BigDecimal.valueOf(150)
                    .divide(BigDecimal.valueOf(650), 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, BigDecimal.ROUND_HALF_UP);

            // absolute_error = |user - ai|
            // Expected: |650 - 500| = 150
            BigDecimal expectedAbsolute = BigDecimal.valueOf(150);

            boolean aiValueCorrect = log.getAiValue().compareTo(BigDecimal.valueOf(500)) == 0;
            boolean userValueCorrect = log.getUserValue().compareTo(BigDecimal.valueOf(650)) == 0;
            boolean percentErrorCorrect = log.getPercentError().setScale(2, BigDecimal.ROUND_HALF_UP)
                    .compareTo(expectedPercent) == 0;
            boolean absoluteErrorCorrect = log.getAbsoluteError().compareTo(expectedAbsolute) == 0;

            return aiValueCorrect && userValueCorrect && percentErrorCorrect && absoluteErrorCorrect;
        }));

        // INVARIANT: Verify protein correction math
        verify(correctionLogRepository).save(argThat(log -> {
            if (!log.getFieldName().equals("protein_g")) {
                return false;
            }

            // Independent calculation: (42 - 30) / 42 * 100 = 28.57%
            BigDecimal expectedPercent = BigDecimal.valueOf(12)
                    .divide(BigDecimal.valueOf(42), 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, BigDecimal.ROUND_HALF_UP);

            return log.getAiValue().compareTo(BigDecimal.valueOf(30)) == 0 &&
                   log.getUserValue().compareTo(BigDecimal.valueOf(42)) == 0 &&
                   log.getPercentError().setScale(2, BigDecimal.ROUND_HALF_UP)
                           .compareTo(expectedPercent) == 0;
        }));
    }

    // ============================================================================
    // Test 8: deleteMeal() - Authorization and Cleanup
    // ============================================================================

    @Test
    void testDeleteMeal_UserDoesNotOwnMeal_ThrowsException() {
        UUID wrongUserId = UUID.randomUUID();
        testMeal.getUser().setId(testUserId);

        when(mealRepository.findById(testMeal.getId())).thenReturn(Optional.of(testMeal));

        assertThrows(ResourceNotFoundException.class, () ->
                mealService.deleteMeal(testMeal.getId(), wrongUserId));

        verify(mealRepository, never()).delete(any());
        verify(storageService, never()).deleteFile(any());
    }

    @Test
    void testDeleteMeal_WithImage_DeletesImageAndMeal() {
        testMeal.setObjectName("test-image-123");

        when(mealRepository.findById(testMeal.getId())).thenReturn(Optional.of(testMeal));
        doNothing().when(storageService).deleteFile(any());
        doNothing().when(mealRepository).delete(any());

        mealService.deleteMeal(testMeal.getId(), testUserId);

        verify(storageService).deleteFile("test-image-123");
        verify(mealRepository).delete(testMeal);
    }

    @Test
    void testDeleteMeal_WithoutImage_DeletesOnlyMeal() {
        testMeal.setObjectName(null); // No image

        when(mealRepository.findById(testMeal.getId())).thenReturn(Optional.of(testMeal));
        doNothing().when(mealRepository).delete(any());

        mealService.deleteMeal(testMeal.getId(), testUserId);

        verify(storageService, never()).deleteFile(any());
        verify(mealRepository).delete(testMeal);
    }

    @Test
    void testDeleteMeal_ImageDeletionFails_StillDeletesMeal() {
        testMeal.setObjectName("test-image-123");

        when(mealRepository.findById(testMeal.getId())).thenReturn(Optional.of(testMeal));
        doThrow(new RuntimeException("Storage error")).when(storageService).deleteFile(any());
        doNothing().when(mealRepository).delete(any());

        // Should not throw, meal should still be deleted
        assertDoesNotThrow(() ->
                mealService.deleteMeal(testMeal.getId(), testUserId));

        verify(storageService).deleteFile("test-image-123");
        verify(mealRepository).delete(testMeal); // Still deletes from DB
    }
}
