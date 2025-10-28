package com.nutritheous.meal;

import com.nutritheous.analyzer.AnalyzerService;
import com.nutritheous.auth.User;
import com.nutritheous.auth.UserRepository;
import com.nutritheous.common.dto.AnalysisResponse;
import com.nutritheous.common.dto.MealResponse;
import com.nutritheous.common.exception.AnalyzerException;
import com.nutritheous.common.exception.ResourceNotFoundException;
import com.nutritheous.meal.dto.MealUpdateRequest;
import com.nutritheous.storage.GoogleCloudStorageService;
import com.nutritheous.validation.AiValidationService;
import com.nutritheous.validation.ValidationFailure;
import com.nutritheous.validation.ValidationFailureRepository;
import com.nutritheous.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MealService {

    private static final Logger logger = LoggerFactory.getLogger(MealService.class);

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoogleCloudStorageService storageService;

    @Autowired
    private AnalyzerService analyzerService;

    @Autowired
    private com.nutritheous.service.PhotoMetadataService photoMetadataService;

    @Autowired
    private com.nutritheous.service.LocationContextService locationContextService;

    @Autowired
    private AiValidationService validationService;

    @Autowired
    private ValidationFailureRepository validationFailureRepository;

    @Transactional
    public MealResponse uploadMeal(
            UUID userId,
            MultipartFile image,
            Meal.MealType mealType,
            LocalDateTime mealTime,
            String description
    ) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String objectName = null;
        String tempAnalyzerUrl = null;
        boolean hasImage = image != null && !image.isEmpty();

        // STEP 1: Extract photo metadata (EXIF GPS, timestamp, device info)
        com.nutritheous.common.dto.PhotoMetadata photoMetadata = null;
        if (hasImage) {
            logger.info("Extracting metadata from photo for user: {}", userId);
            photoMetadata = photoMetadataService.extractMetadata(image);
        }

        // STEP 2: Get location context from GPS coordinates via Google Maps
        com.nutritheous.common.dto.LocationContext locationContext = null;
        if (photoMetadata != null && photoMetadata.hasGPS()) {
            logger.info("Getting location context from GPS: ({}, {})",
                    photoMetadata.getLatitude(), photoMetadata.getLongitude());
            locationContext = locationContextService.getLocationContext(
                    photoMetadata.getLatitude(),
                    photoMetadata.getLongitude()
            );
        }

        // STEP 3: Upload image to storage if provided
        if (hasImage) {
            logger.info("Uploading image to storage for user: {}", userId);
            objectName = storageService.uploadFile(image, userId);
            // Generate temporary presigned URL for the AI analyzer
            tempAnalyzerUrl = storageService.getPresignedUrl(objectName);
        } else {
            logger.info("No image provided, creating text-only meal entry");
        }

        // STEP 4: Determine effective meal time (use photo timestamp if available)
        LocalDateTime effectiveMealTime = mealTime;
        if (effectiveMealTime == null && photoMetadata != null && photoMetadata.getCapturedAt() != null) {
            effectiveMealTime = photoMetadata.getCapturedAt();
            logger.info("Using photo capture time as meal time: {}", effectiveMealTime);
        } else if (effectiveMealTime == null) {
            effectiveMealTime = LocalDateTime.now();
        }

        // STEP 5: Create meal entity with metadata and location context
        Meal.MealBuilder mealBuilder = Meal.builder()
                .user(user)
                .mealTime(effectiveMealTime)
                .mealType(mealType)
                .objectName(objectName)
                .description(description)
                .analysisStatus(Meal.AnalysisStatus.PENDING);

        // Add photo metadata if available
        if (photoMetadata != null) {
            mealBuilder
                    .photoCapturedAt(photoMetadata.getCapturedAt())
                    .photoLatitude(photoMetadata.getLatitude())
                    .photoLongitude(photoMetadata.getLongitude())
                    .photoDeviceMake(photoMetadata.getDeviceMake())
                    .photoDeviceModel(photoMetadata.getDeviceModel());
        }

        // Add location context if available
        if (locationContext != null && locationContext.isKnown()) {
            mealBuilder
                    .locationPlaceName(locationContext.getPlaceName())
                    .locationPlaceType(locationContext.getPlaceType())
                    .locationCuisineType(locationContext.getCuisineType())
                    .locationPriceLevel(locationContext.getPriceLevel())
                    .locationIsRestaurant(locationContext.isRestaurant())
                    .locationIsHome(locationContext.isHome())
                    .locationAddress(locationContext.getAddress());
        }

        Meal meal = mealBuilder.build();

        meal = mealRepository.save(meal);
        logger.info("Created meal with id: {}", meal.getId());

        // STEP 6: Analyze the meal with AI (now with location and time context!)
        UUID mealId = meal.getId();
        try {
            if (hasImage) {
                logger.info("Sending image to AI analyzer with context - description: {}, location: {}, time: {}",
                        description,
                        locationContext != null ? locationContext.getPlaceName() : "none",
                        effectiveMealTime);
                AnalysisResponse analysisResponse = analyzerService.analyzeImage(
                        tempAnalyzerUrl, description, locationContext, effectiveMealTime);
                updateMealWithAnalysis(meal, analysisResponse);
            } else if (description != null && !description.isBlank()) {
                logger.info("Analyzing text-only meal with context - description: {}, location: {}, time: {}",
                        description,
                        locationContext != null ? locationContext.getPlaceName() : "none",
                        effectiveMealTime);
                AnalysisResponse analysisResponse = analyzerService.analyzeTextOnly(
                        description, locationContext, effectiveMealTime);
                updateMealWithAnalysis(meal, analysisResponse);
            } else {
                // This shouldn't happen due to validation in controller, but handle it
                logger.warn("Meal {} has neither image nor description", mealId);
                meal.setAnalysisStatus(Meal.AnalysisStatus.FAILED);
            }

            meal = mealRepository.save(meal);
            logger.info("Updated meal {} with analysis results", mealId);

        } catch (AnalyzerException e) {
            logger.error("Failed to analyze meal {}", mealId, e);
            meal.setAnalysisStatus(Meal.AnalysisStatus.FAILED);
            meal = mealRepository.save(meal);
        }

        return MealResponse.fromMeal(meal, storageService);
    }

    private void updateMealWithAnalysis(Meal meal, AnalysisResponse analysisResponse) {
        // STEP 1: Validate AI response for sanity checks
        ValidationResult validation = validationService.validate(analysisResponse);

        // STEP 2: Handle validation failures
        if (!validation.isValid()) {
            logger.error("AI returned invalid data for meal {}: {} errors, {} warnings",
                    meal.getId(),
                    validation.getErrors().size(),
                    validation.getWarnings().size());

            // Log each error
            validation.getErrors().forEach(error ->
                    logger.error("  ERROR - {}: {}", error.getField(), error.getMessage()));

            // Store validation failure for analysis
            ValidationFailure failure = ValidationFailure.builder()
                    .meal(meal)
                    .issueCount(validation.getIssues().size())
                    .errorCount(validation.getErrors().size())
                    .warningCount(validation.getWarnings().size())
                    .issues(validation.getIssues())
                    .confidenceScore(analysisResponse.getConfidence())
                    .mealDescription(meal.getDescription())
                    .build();

            validationFailureRepository.save(failure);

            // Mark meal as failed - don't save invalid AI data
            meal.setAnalysisStatus(Meal.AnalysisStatus.FAILED);
            logger.warn("Meal {} marked as FAILED due to validation errors", meal.getId());
            return;
        }

        // STEP 3: Log warnings (suspicious but not invalid)
        validation.getWarnings().forEach(warning ->
                logger.warn("AI validation warning for meal {}: {} - {}",
                        meal.getId(), warning.getField(), warning.getMessage()));

        // STEP 4: Update meal with validated data
        meal.setServingSize(analysisResponse.getServingSize());
        meal.setCalories(analysisResponse.getCalories());
        meal.setProteinG(analysisResponse.getProteinG());
        meal.setFatG(analysisResponse.getFatG());
        meal.setSaturatedFatG(analysisResponse.getSaturatedFatG());
        meal.setCarbohydratesG(analysisResponse.getCarbohydratesG());
        meal.setFiberG(analysisResponse.getFiberG());
        meal.setSugarG(analysisResponse.getSugarG());
        meal.setSodiumMg(analysisResponse.getSodiumMg());
        meal.setCholesterolMg(analysisResponse.getCholesterolMg());
        meal.setIngredients(analysisResponse.getIngredients());
        meal.setAllergens(analysisResponse.getAllergens());
        meal.setHealthNotes(analysisResponse.getHealthNotes());
        meal.setConfidence(analysisResponse.getConfidence());

        // Enhanced AI-extracted fields
        meal.setCookingMethod(analysisResponse.getCookingMethod());
        meal.setNovaScore(analysisResponse.getNovaScore());
        meal.setIsUltraProcessed(analysisResponse.getIsUltraProcessed());
        meal.setIsFried(analysisResponse.getIsFried());
        meal.setHasRefinedGrains(analysisResponse.getHasRefinedGrains());
        meal.setEstimatedGi(analysisResponse.getEstimatedGi());
        meal.setEstimatedGl(analysisResponse.getEstimatedGl());
        meal.setPlantCount(analysisResponse.getPlantCount());
        meal.setUniquePlants(analysisResponse.getUniquePlants());
        meal.setIsFermented(analysisResponse.getIsFermented());
        meal.setProteinSourceType(analysisResponse.getProteinSourceType());
        meal.setFatQuality(analysisResponse.getFatQuality());
        meal.setMealTypeGuess(analysisResponse.getMealTypeGuess());

        meal.setAnalysisStatus(Meal.AnalysisStatus.COMPLETED);
    }

    public MealResponse getMealById(UUID mealId, UUID userId) {
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal not found with id: " + mealId));

        // Ensure the meal belongs to the user
        if (!meal.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Meal not found with id: " + mealId);
        }

        return MealResponse.fromMeal(meal, storageService);
    }

    public List<MealResponse> getUserMeals(UUID userId) {
        return mealRepository.findByUserIdOrderByMealTimeDesc(userId)
                .stream()
                .map(meal -> MealResponse.fromMeal(meal, storageService))
                .collect(Collectors.toList());
    }

    public List<MealResponse> getUserMealsByDateRange(
            UUID userId,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        return mealRepository.findByUserIdAndMealTimeBetweenOrderByMealTimeDesc(userId, startDate, endDate)
                .stream()
                .map(meal -> MealResponse.fromMeal(meal, storageService))
                .collect(Collectors.toList());
    }

    public List<MealResponse> getUserMealsByType(UUID userId, Meal.MealType mealType) {
        return mealRepository.findByUserIdAndMealTypeOrderByMealTimeDesc(userId, mealType)
                .stream()
                .map(meal -> MealResponse.fromMeal(meal, storageService))
                .collect(Collectors.toList());
    }

    @Transactional
    public MealResponse updateMeal(UUID mealId, UUID userId, MealUpdateRequest request) {
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal not found with id: " + mealId));

        // Ensure the meal belongs to the user
        if (!meal.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Meal not found with id: " + mealId);
        }

        // Update meal metadata (only update non-null fields)
        if (request.getMealType() != null) {
            meal.setMealType(request.getMealType());
        }
        if (request.getMealTime() != null) {
            meal.setMealTime(request.getMealTime());
        }
        if (request.getDescription() != null) {
            meal.setDescription(request.getDescription());
        }
        if (request.getServingSize() != null) {
            meal.setServingSize(request.getServingSize());
        }

        // Update nutritional information
        if (request.getCalories() != null) {
            meal.setCalories(request.getCalories());
        }
        if (request.getProteinG() != null) {
            meal.setProteinG(request.getProteinG());
        }
        if (request.getFatG() != null) {
            meal.setFatG(request.getFatG());
        }
        if (request.getSaturatedFatG() != null) {
            meal.setSaturatedFatG(request.getSaturatedFatG());
        }
        if (request.getCarbohydratesG() != null) {
            meal.setCarbohydratesG(request.getCarbohydratesG());
        }
        if (request.getFiberG() != null) {
            meal.setFiberG(request.getFiberG());
        }
        if (request.getSugarG() != null) {
            meal.setSugarG(request.getSugarG());
        }
        if (request.getSodiumMg() != null) {
            meal.setSodiumMg(request.getSodiumMg());
        }
        if (request.getCholesterolMg() != null) {
            meal.setCholesterolMg(request.getCholesterolMg());
        }

        // Update additional metadata
        if (request.getIngredients() != null) {
            meal.setIngredients(request.getIngredients());
        }
        if (request.getAllergens() != null) {
            meal.setAllergens(request.getAllergens());
        }
        if (request.getHealthNotes() != null) {
            meal.setHealthNotes(request.getHealthNotes());
        }

        meal = mealRepository.save(meal);
        logger.info("Updated meal with id: {}", mealId);

        return MealResponse.fromMeal(meal, storageService);
    }

    @Transactional
    public void deleteMeal(UUID mealId, UUID userId) {
        Meal meal = mealRepository.findById(mealId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal not found with id: " + mealId));

        // Ensure the meal belongs to the user
        if (!meal.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Meal not found with id: " + mealId);
        }

        // Delete image from storage
        try {
            if (meal.getObjectName() != null) {
                storageService.deleteFile(meal.getObjectName());
            }
        } catch (Exception e) {
            logger.error("Failed to delete image from storage for meal {}", mealId, e);
        }

        // Delete meal from database
        mealRepository.delete(meal);
        logger.info("Deleted meal with id: {}", mealId);
    }
}
