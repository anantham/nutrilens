package com.nutritheous.meal;

import com.nutritheous.auth.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "meals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "meal_time", nullable = false)
    private LocalDateTime mealTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", length = 20)
    private MealType mealType;

    @Column(name = "object_name", length = 500)
    private String objectName;

    @Column(length = 500)
    private String description;

    @Column(name = "serving_size", length = 255)
    private String servingSize;

    @Column
    private Integer calories;

    @Column(name = "protein_g")
    private Double proteinG;

    @Column(name = "fat_g")
    private Double fatG;

    @Column(name = "saturated_fat_g")
    private Double saturatedFatG;

    @Column(name = "carbohydrates_g")
    private Double carbohydratesG;

    @Column(name = "fiber_g")
    private Double fiberG;

    @Column(name = "sugar_g")
    private Double sugarG;

    @Column(name = "sodium_mg")
    private Double sodiumMg;

    @Column(name = "cholesterol_mg")
    private Double cholesterolMg;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ingredients", columnDefinition = "jsonb")
    private java.util.List<String> ingredients;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allergens", columnDefinition = "jsonb")
    private java.util.List<String> allergens;

    @Column(name = "health_notes", length = 1000)
    private String healthNotes;

    @Column
    private Double confidence;

    // Enhanced AI-extracted fields
    @Column(name = "cooking_method", length = 50)
    private String cookingMethod;

    @Column(name = "nova_score")
    private Double novaScore;

    @Column(name = "is_ultra_processed")
    @Builder.Default
    private Boolean isUltraProcessed = false;

    @Column(name = "is_fried")
    @Builder.Default
    private Boolean isFried = false;

    @Column(name = "has_refined_grains")
    @Builder.Default
    private Boolean hasRefinedGrains = false;

    @Column(name = "estimated_gi")
    private Integer estimatedGi;

    @Column(name = "estimated_gl")
    private Integer estimatedGl;

    @Column(name = "plant_count")
    private Integer plantCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unique_plants", columnDefinition = "jsonb")
    private java.util.List<String> uniquePlants;

    @Column(name = "is_fermented")
    @Builder.Default
    private Boolean isFermented = false;

    @Column(name = "protein_source_type", length = 50)
    private String proteinSourceType;

    @Column(name = "fat_quality", length = 20)
    private String fatQuality;

    @Column(name = "meal_type_guess", length = 20)
    private String mealTypeGuess;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 20)
    @Builder.Default
    private AnalysisStatus analysisStatus = AnalysisStatus.PENDING;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (mealTime == null) {
            mealTime = LocalDateTime.now();
        }
    }

    public enum MealType {
        BREAKFAST, LUNCH, DINNER, SNACK
    }

    public enum AnalysisStatus {
        PENDING, COMPLETED, FAILED
    }
}
