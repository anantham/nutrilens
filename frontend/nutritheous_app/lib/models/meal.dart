import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/equatable.dart';
import 'nutrition_data.dart';

part 'meal.g.dart';

/// Analysis status for a meal
enum AnalysisStatus {
  @JsonValue('PENDING')
  pending,
  @JsonValue('COMPLETED')
  completed,
  @JsonValue('FAILED')
  failed,
}

/// Meal type categories (matches API enum values)
enum MealType {
  @JsonValue('BREAKFAST')
  breakfast,
  @JsonValue('LUNCH')
  lunch,
  @JsonValue('DINNER')
  dinner,
  @JsonValue('SNACK')
  snack,
}

// Extension to handle display name
extension MealTypeExtension on MealType {
  String get displayName {
    switch (this) {
      case MealType.breakfast:
        return 'Breakfast';
      case MealType.lunch:
        return 'Lunch';
      case MealType.dinner:
        return 'Dinner';
      case MealType.snack:
        return 'Snack';
    }
  }
}

/// Represents a meal entry with nutritional analysis (matches API MealResponse)
@JsonSerializable()
class Meal extends Equatable {
  final String id;

  @JsonKey(name: 'mealTime')
  final DateTime mealTime;

  @JsonKey(name: 'mealType', unknownEnumValue: MealType.snack)
  final MealType? mealType;

  @JsonKey(name: 'imageUrl')
  final String? imageUrl;

  @JsonKey(name: 'objectName')
  final String? objectName;

  final String? description;

  @JsonKey(name: 'analysisJson')
  final Map<String, dynamic>? analysisJson;

  final double? calories;

  @JsonKey(name: 'protein_g')
  final double? proteinG;

  @JsonKey(name: 'fat_g')
  final double? fatG;

  @JsonKey(name: 'saturated_fat_g')
  final double? saturatedFatG;

  @JsonKey(name: 'carbohydrates_g')
  final double? carbohydratesG;

  @JsonKey(name: 'fiber_g')
  final double? fiberG;

  @JsonKey(name: 'sugar_g')
  final double? sugarG;

  @JsonKey(name: 'sodium_mg')
  final double? sodiumMg;

  @JsonKey(name: 'cholesterol_mg')
  final double? cholesterolMg;

  @JsonKey(name: 'serving_size')
  final String? servingSize;

  @JsonKey(name: 'health_notes')
  final String? healthNotes;

  final List<String>? ingredients;

  final List<String>? allergens;

  final double? confidence;

  @JsonKey(name: 'analysisStatus')
  final AnalysisStatus analysisStatus;

  @JsonKey(name: 'createdAt')
  final DateTime? createdAt;

  // Photo metadata fields
  @JsonKey(name: 'photo_captured_at')
  final DateTime? photoCapturedAt;

  @JsonKey(name: 'photo_latitude')
  final double? photoLatitude;

  @JsonKey(name: 'photo_longitude')
  final double? photoLongitude;

  @JsonKey(name: 'photo_device_make')
  final String? photoDeviceMake;

  @JsonKey(name: 'photo_device_model')
  final String? photoDeviceModel;

  // Location context fields
  @JsonKey(name: 'location_place_name')
  final String? locationPlaceName;

  @JsonKey(name: 'location_place_type')
  final String? locationPlaceType;

  @JsonKey(name: 'location_cuisine_type')
  final String? locationCuisineType;

  @JsonKey(name: 'location_price_level')
  final int? locationPriceLevel;

  @JsonKey(name: 'location_is_restaurant')
  final bool? locationIsRestaurant;

  @JsonKey(name: 'location_is_home')
  final bool? locationIsHome;

  @JsonKey(name: 'location_address')
  final String? locationAddress;

  const Meal({
    required this.id,
    required this.mealTime,
    this.mealType,
    this.imageUrl,
    this.objectName,
    this.description,
    this.analysisJson,
    this.calories,
    this.proteinG,
    this.fatG,
    this.saturatedFatG,
    this.carbohydratesG,
    this.fiberG,
    this.sugarG,
    this.sodiumMg,
    this.cholesterolMg,
    this.servingSize,
    this.healthNotes,
    this.ingredients,
    this.allergens,
    this.confidence,
    this.analysisStatus = AnalysisStatus.pending,
    this.createdAt,
    this.photoCapturedAt,
    this.photoLatitude,
    this.photoLongitude,
    this.photoDeviceMake,
    this.photoDeviceModel,
    this.locationPlaceName,
    this.locationPlaceType,
    this.locationCuisineType,
    this.locationPriceLevel,
    this.locationIsRestaurant,
    this.locationIsHome,
    this.locationAddress,
  });

  factory Meal.fromJson(Map<String, dynamic> json) => _$MealFromJson(json);

  Map<String, dynamic> toJson() => _$MealToJson(this);

  @override
  List<Object?> get props => [
        id,
        mealTime,
        mealType,
        imageUrl,
        objectName,
        description,
        analysisJson,
        calories,
        proteinG,
        fatG,
        saturatedFatG,
        carbohydratesG,
        fiberG,
        sugarG,
        sodiumMg,
        cholesterolMg,
        servingSize,
        healthNotes,
        ingredients,
        allergens,
        confidence,
        analysisStatus,
        createdAt,
        photoCapturedAt,
        photoLatitude,
        photoLongitude,
        photoDeviceMake,
        photoDeviceModel,
        locationPlaceName,
        locationPlaceType,
        locationCuisineType,
        locationPriceLevel,
        locationIsRestaurant,
        locationIsHome,
        locationAddress,
      ];

  Meal copyWith({
    String? id,
    DateTime? mealTime,
    MealType? mealType,
    String? imageUrl,
    String? objectName,
    String? description,
    Map<String, dynamic>? analysisJson,
    double? calories,
    double? proteinG,
    double? fatG,
    double? saturatedFatG,
    double? carbohydratesG,
    double? fiberG,
    double? sugarG,
    double? sodiumMg,
    double? cholesterolMg,
    String? servingSize,
    String? healthNotes,
    List<String>? ingredients,
    List<String>? allergens,
    double? confidence,
    AnalysisStatus? analysisStatus,
    DateTime? createdAt,
    DateTime? photoCapturedAt,
    double? photoLatitude,
    double? photoLongitude,
    String? photoDeviceMake,
    String? photoDeviceModel,
    String? locationPlaceName,
    String? locationPlaceType,
    String? locationCuisineType,
    int? locationPriceLevel,
    bool? locationIsRestaurant,
    bool? locationIsHome,
    String? locationAddress,
  }) {
    return Meal(
      id: id ?? this.id,
      mealTime: mealTime ?? this.mealTime,
      mealType: mealType ?? this.mealType,
      imageUrl: imageUrl ?? this.imageUrl,
      objectName: objectName ?? this.objectName,
      description: description ?? this.description,
      analysisJson: analysisJson ?? this.analysisJson,
      calories: calories ?? this.calories,
      proteinG: proteinG ?? this.proteinG,
      fatG: fatG ?? this.fatG,
      saturatedFatG: saturatedFatG ?? this.saturatedFatG,
      carbohydratesG: carbohydratesG ?? this.carbohydratesG,
      fiberG: fiberG ?? this.fiberG,
      sugarG: sugarG ?? this.sugarG,
      sodiumMg: sodiumMg ?? this.sodiumMg,
      cholesterolMg: cholesterolMg ?? this.cholesterolMg,
      servingSize: servingSize ?? this.servingSize,
      healthNotes: healthNotes ?? this.healthNotes,
      ingredients: ingredients ?? this.ingredients,
      allergens: allergens ?? this.allergens,
      confidence: confidence ?? this.confidence,
      analysisStatus: analysisStatus ?? this.analysisStatus,
      createdAt: createdAt ?? this.createdAt,
      photoCapturedAt: photoCapturedAt ?? this.photoCapturedAt,
      photoLatitude: photoLatitude ?? this.photoLatitude,
      photoLongitude: photoLongitude ?? this.photoLongitude,
      photoDeviceMake: photoDeviceMake ?? this.photoDeviceMake,
      photoDeviceModel: photoDeviceModel ?? this.photoDeviceModel,
      locationPlaceName: locationPlaceName ?? this.locationPlaceName,
      locationPlaceType: locationPlaceType ?? this.locationPlaceType,
      locationCuisineType: locationCuisineType ?? this.locationCuisineType,
      locationPriceLevel: locationPriceLevel ?? this.locationPriceLevel,
      locationIsRestaurant: locationIsRestaurant ?? this.locationIsRestaurant,
      locationIsHome: locationIsHome ?? this.locationIsHome,
      locationAddress: locationAddress ?? this.locationAddress,
    );
  }

  /// Get nutrition data as a NutritionData object
  NutritionData? get nutrition {
    if (calories == null || proteinG == null || fatG == null || carbohydratesG == null) {
      return null;
    }
    return NutritionData(
      calories: calories!,
      proteinG: proteinG!,
      fatG: fatG!,
      carbohydratesG: carbohydratesG!,
      saturatedFatG: saturatedFatG,
      fiberG: fiberG,
      sugarG: sugarG,
      sodiumMg: sodiumMg,
      cholesterolMg: cholesterolMg,
      servingSize: servingSize,
      healthNotes: healthNotes,
      ingredients: ingredients,
      allergens: allergens,
    );
  }

  /// Check if the meal analysis is complete
  bool get isAnalysisComplete => analysisStatus == AnalysisStatus.completed;

  /// Check if the meal analysis is pending
  bool get isAnalysisPending => analysisStatus == AnalysisStatus.pending;

  /// Check if the meal analysis has failed
  bool get isAnalysisFailed => analysisStatus == AnalysisStatus.failed;

  /// Check if location data is available
  bool get hasLocationData => locationPlaceName != null || (photoLatitude != null && photoLongitude != null);

  /// Get a user-friendly location description
  String? get locationDescription {
    if (locationPlaceName != null) {
      return locationPlaceName;
    } else if (photoLatitude != null && photoLongitude != null) {
      return 'Lat: ${photoLatitude!.toStringAsFixed(4)}, Lng: ${photoLongitude!.toStringAsFixed(4)}';
    }
    return null;
  }

  /// Get location badge text for UI
  String? get locationBadge {
    if (locationIsHome == true) {
      return '🏠 Home-cooked';
    } else if (locationIsRestaurant == true) {
      if (locationPlaceName != null) {
        return '🍽️ $locationPlaceName';
      }
      return '🍽️ Restaurant';
    } else if (locationPlaceType != null) {
      return '📍 ${locationPlaceType![0].toUpperCase()}${locationPlaceType!.substring(1)}';
    }
    return null;
  }
}
