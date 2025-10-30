package com.nutritheous.ingredient;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user recipe patterns
 */
@Repository
public interface UserRecipePatternRepository extends JpaRepository<UserRecipePattern, UUID> {

    /**
     * Find recipe pattern by user and recipe name
     */
    Optional<UserRecipePattern> findByUserIdAndRecipeName(UUID userId, String recipeName);

    /**
     * Find all recipe patterns for a user
     */
    List<UserRecipePattern> findByUserIdOrderByTimesMadeDesc(UUID userId);

    /**
     * Find frequently made recipes (for quick suggestions)
     */
    @Query("SELECT r FROM UserRecipePattern r WHERE r.user.id = :userId " +
           "AND r.timesMade >= :minTimesMade ORDER BY r.timesMade DESC")
    List<UserRecipePattern> findFrequentRecipes(
            @Param("userId") UUID userId,
            @Param("minTimesMade") Integer minTimesMade
    );

    /**
     * Search recipe patterns by name or keywords
     */
    @Query("SELECT r FROM UserRecipePattern r WHERE r.user.id = :userId " +
           "AND (LOWER(r.recipeName) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<UserRecipePattern> searchByNameOrKeywords(
            @Param("userId") UUID userId,
            @Param("searchTerm") String searchTerm
    );

    /**
     * Count recipe patterns for a user
     */
    Long countByUserId(UUID userId);

    /**
     * Get user's most frequently made recipes (top N)
     * Use PageRequest.of(0, limit) to limit results
     */
    @Query("SELECT r FROM UserRecipePattern r WHERE r.user.id = :userId " +
           "ORDER BY r.timesMade DESC")
    List<UserRecipePattern> findTopRecipes(
            @Param("userId") UUID userId,
            Pageable pageable
    );
}
