package com.nutritheous.ingredient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user ingredient library (learned ingredients)
 */
@Repository
public interface UserIngredientLibraryRepository extends JpaRepository<UserIngredientLibrary, UUID> {

    /**
     * Find ingredient by user and normalized name
     */
    Optional<UserIngredientLibrary> findByUserIdAndNormalizedName(UUID userId, String normalizedName);

    /**
     * Find all ingredients for a user
     */
    List<UserIngredientLibrary> findByUserIdOrderByConfidenceScoreDesc(UUID userId);

    /**
     * Find ingredients by category for a user
     */
    List<UserIngredientLibrary> findByUserIdAndIngredientCategoryOrderByConfidenceScoreDesc(
            UUID userId, String category);

    /**
     * Find high-confidence ingredients (for auto-fill)
     */
    @Query("SELECT u FROM UserIngredientLibrary u WHERE u.user.id = :userId " +
           "AND u.confidenceScore >= :minConfidence ORDER BY u.confidenceScore DESC")
    List<UserIngredientLibrary> findHighConfidenceIngredients(
            @Param("userId") UUID userId,
            @Param("minConfidence") Double minConfidence
    );

    /**
     * Search ingredients by name
     */
    @Query("SELECT u FROM UserIngredientLibrary u WHERE u.user.id = :userId " +
           "AND (LOWER(u.ingredientName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.normalizedName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY u.confidenceScore DESC")
    List<UserIngredientLibrary> searchByName(
            @Param("userId") UUID userId,
            @Param("searchTerm") String searchTerm
    );

    /**
     * Count learned ingredients for a user
     */
    Long countByUserId(UUID userId);

    /**
     * Get average confidence score for a user's ingredient library
     */
    @Query("SELECT AVG(u.confidenceScore) FROM UserIngredientLibrary u WHERE u.user.id = :userId")
    Double getAverageConfidence(@Param("userId") UUID userId);
}
