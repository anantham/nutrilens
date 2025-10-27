package com.nutritheous.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nutritheous.meal.Meal;
import com.nutritheous.storage.GoogleCloudStorageService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealResponse {

    private UUID id;
    private LocalDateTime mealTime;
    private Meal.MealType mealType;
    private String imageUrl;
    private String objectName;
    private String description;

    @JsonProperty("serving_size")
    private String servingSize;

    private Integer calories;

    @JsonProperty("protein_g")
    private Double proteinG;

    @JsonProperty("fat_g")
    private Double fatG;

    @JsonProperty("saturated_fat_g")
    private Double saturatedFatG;

    @JsonProperty("carbohydrates_g")
    private Double carbohydratesG;

    @JsonProperty("fiber_g")
    private Double fiberG;

    @JsonProperty("sugar_g")
    private Double sugarG;

    @JsonProperty("sodium_mg")
    private Double sodiumMg;

    @JsonProperty("cholesterol_mg")
    private Double cholesterolMg;

    private List<String> ingredients;
    private List<String> allergens;

    @JsonProperty("health_notes")
    private String healthNotes;

    private Double confidence;
    private Meal.AnalysisStatus analysisStatus;
    private LocalDateTime createdAt;

    // Enhanced AI-extracted fields
    @JsonProperty("cooking_method")
    private String cookingMethod;

    @JsonProperty("nova_score")
    private Double novaScore;

    @JsonProperty("is_ultra_processed")
    private Boolean isUltraProcessed;

    @JsonProperty("is_fried")
    private Boolean isFried;

    @JsonProperty("has_refined_grains")
    private Boolean hasRefinedGrains;

    @JsonProperty("estimated_gi")
    private Integer estimatedGi;

    @JsonProperty("estimated_gl")
    private Integer estimatedGl;

    @JsonProperty("plant_count")
    private Integer plantCount;

    @JsonProperty("unique_plants")
    private List<String> uniquePlants;

    @JsonProperty("is_fermented")
    private Boolean isFermented;

    @JsonProperty("protein_source_type")
    private String proteinSourceType;

    @JsonProperty("fat_quality")
    private String fatQuality;

    @JsonProperty("meal_type_guess")
    private String mealTypeGuess;

    // Photo metadata fields
    @JsonProperty("photo_captured_at")
    private LocalDateTime photoCapturedAt;

    @JsonProperty("photo_latitude")
    private Double photoLatitude;

    @JsonProperty("photo_longitude")
    private Double photoLongitude;

    @JsonProperty("photo_device_make")
    private String photoDeviceMake;

    @JsonProperty("photo_device_model")
    private String photoDeviceModel;

    // Location context fields
    @JsonProperty("location_place_name")
    private String locationPlaceName;

    @JsonProperty("location_place_type")
    private String locationPlaceType;

    @JsonProperty("location_cuisine_type")
    private String locationCuisineType;

    @JsonProperty("location_price_level")
    private Integer locationPriceLevel;

    @JsonProperty("location_is_restaurant")
    private Boolean locationIsRestaurant;

    @JsonProperty("location_is_home")
    private Boolean locationIsHome;

    @JsonProperty("location_address")
    private String locationAddress;

    public static MealResponse fromMeal(Meal meal, GoogleCloudStorageService storageService) {
        // Generate fresh presigned URL from object name
        String imageUrl = "";
        if (meal.getObjectName() != null) {
            imageUrl = storageService.getPresignedImageUrl(meal.getObjectName());
        }

        return MealResponse.builder()
                .id(meal.getId())
                .mealTime(meal.getMealTime())
                .mealType(meal.getMealType())
                .imageUrl(imageUrl)
                .objectName(meal.getObjectName())
                .description(meal.getDescription())
                .servingSize(meal.getServingSize())
                .calories(meal.getCalories())
                .proteinG(meal.getProteinG())
                .fatG(meal.getFatG())
                .saturatedFatG(meal.getSaturatedFatG())
                .carbohydratesG(meal.getCarbohydratesG())
                .fiberG(meal.getFiberG())
                .sugarG(meal.getSugarG())
                .sodiumMg(meal.getSodiumMg())
                .cholesterolMg(meal.getCholesterolMg())
                .ingredients(meal.getIngredients())
                .allergens(meal.getAllergens())
                .healthNotes(meal.getHealthNotes())
                .confidence(meal.getConfidence())
                .analysisStatus(meal.getAnalysisStatus())
                .createdAt(meal.getCreatedAt())
                // Enhanced AI-extracted fields
                .cookingMethod(meal.getCookingMethod())
                .novaScore(meal.getNovaScore())
                .isUltraProcessed(meal.getIsUltraProcessed())
                .isFried(meal.getIsFried())
                .hasRefinedGrains(meal.getHasRefinedGrains())
                .estimatedGi(meal.getEstimatedGi())
                .estimatedGl(meal.getEstimatedGl())
                .plantCount(meal.getPlantCount())
                .uniquePlants(meal.getUniquePlants())
                .isFermented(meal.getIsFermented())
                .proteinSourceType(meal.getProteinSourceType())
                .fatQuality(meal.getFatQuality())
                .mealTypeGuess(meal.getMealTypeGuess())
                // Photo metadata
                .photoCapturedAt(meal.getPhotoCapturedAt())
                .photoLatitude(meal.getPhotoLatitude())
                .photoLongitude(meal.getPhotoLongitude())
                .photoDeviceMake(meal.getPhotoDeviceMake())
                .photoDeviceModel(meal.getPhotoDeviceModel())
                // Location context
                .locationPlaceName(meal.getLocationPlaceName())
                .locationPlaceType(meal.getLocationPlaceType())
                .locationCuisineType(meal.getLocationCuisineType())
                .locationPriceLevel(meal.getLocationPriceLevel())
                .locationIsRestaurant(meal.getLocationIsRestaurant())
                .locationIsHome(meal.getLocationIsHome())
                .locationAddress(meal.getLocationAddress())
                .build();
    }
}
