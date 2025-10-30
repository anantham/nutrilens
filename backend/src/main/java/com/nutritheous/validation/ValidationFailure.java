package com.nutritheous.validation;

import com.nutritheous.meal.Meal;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entity for tracking AI validation failures.
 * Stores cases where AI generated invalid/impossible nutrition data.
 */
@Entity
@Table(name = "validation_failures")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    /**
     * Number of validation issues found (errors + warnings)
     */
    @Column(name = "issue_count")
    private Integer issueCount;

    /**
     * Number of ERROR-level issues
     */
    @Column(name = "error_count")
    private Integer errorCount;

    /**
     * Number of WARNING-level issues
     */
    @Column(name = "warning_count")
    private Integer warningCount;

    /**
     * List of validation issues as JSON
     * Example: [{"severity":"ERROR","field":"fiber_g","message":"Fiber cannot exceed carbs"}]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "issues", columnDefinition = "jsonb")
    private List<ValidationIssue> issues;

    /**
     * AI's confidence score when it failed
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /**
     * Raw AI response that failed validation (for debugging)
     */
    @Column(name = "raw_ai_response", columnDefinition = "TEXT")
    private String rawAiResponse;

    /**
     * Meal description for pattern analysis
     */
    @Column(name = "meal_description", columnDefinition = "TEXT")
    private String mealDescription;

    /**
     * When the validation failure occurred
     */
    @CreationTimestamp
    @Column(name = "failed_at", nullable = false, updatable = false)
    private LocalDateTime failedAt;
}
