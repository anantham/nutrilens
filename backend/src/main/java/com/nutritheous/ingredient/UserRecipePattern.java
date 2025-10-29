package com.nutritheous.ingredient;

import com.nutritheous.auth.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks recipe patterns learned from user behavior.
 * E.g., "When user makes idli, they typically include sambar, chutney, and ghee"
 */
@Entity
@Table(name = "user_recipe_patterns",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "recipe_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRecipePattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Recipe name (e.g., "idli", "dosa", "sambar")
     */
    @Column(name = "recipe_name", nullable = false, length = 200)
    private String recipeName;

    /**
     * Keywords for fuzzy matching
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "recipe_keywords", columnDefinition = "text[]")
    private String[] recipeKeywords;

    /**
     * Common ingredients for this recipe as JSON
     * Format: [{"name":"rice batter","quantity":100,"unit":"g"}, ...]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "common_ingredients", columnDefinition = "jsonb")
    private List<Map<String, Object>> commonIngredients;

    /**
     * Number of times user has made this recipe
     */
    @Column(name = "times_made")
    @Builder.Default
    private Integer timesMade = 1;

    @Column(name = "last_made")
    private LocalDateTime lastMade;
}
