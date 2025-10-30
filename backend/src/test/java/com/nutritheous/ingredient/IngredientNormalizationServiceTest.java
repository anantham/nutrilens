package com.nutritheous.ingredient;

import com.nutritheous.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IngredientNormalizationService.
 * Tests Levenshtein distance algorithm, normalization rules, and fuzzy matching.
 */
class IngredientNormalizationServiceTest {

    private IngredientNormalizationService service;

    @BeforeEach
    void setUp() {
        service = new IngredientNormalizationService();
    }

    // ===== Normalization Tests =====

    /**
     * Test: Basic normalization (lowercase, trim, remove special chars).
     */
    @Test
    void testNormalize_BasicNormalization() {
        assertEquals("coconut chutney", service.normalize("Coconut Chutney"));
        assertEquals("coconut chutney", service.normalize("  Coconut Chutney  "));
        assertEquals("coconut chutney", service.normalize("Coconut, Chutney!"));
        assertEquals("coconut chutney", service.normalize("Coconut--Chutney"));
    }

    /**
     * Test: Known aliases are applied.
     */
    @Test
    void testNormalize_KnownAliases() {
        // South Indian variations
        assertEquals("idli", service.normalize("idly"));
        assertEquals("idli", service.normalize("idlies"));
        assertEquals("dosa", service.normalize("dosai"));
        assertEquals("dosa", service.normalize("dosay"));
        assertEquals("chutney", service.normalize("chutny"));
        assertEquals("sambar", service.normalize("sambhar"));

        // Common variations
        assertEquals("yoghurt", service.normalize("yogurt"));
        assertEquals("yoghurt", service.normalize("curd"));
        assertEquals("eggplant", service.normalize("brinjal"));
        assertEquals("bell pepper", service.normalize("capsicum"));
        assertEquals("okra", service.normalize("ladies finger"));
    }

    /**
     * Test: Plural handling.
     */
    @Test
    void testNormalize_PluralHandling() {
        // Plurals that don't match aliases directly should be handled
        assertEquals("dosa", service.normalize("dosais"));  // "dosais" → "dosai" → "dosa"
        assertEquals("idli", service.normalize("idlies"));  // "idlies" → alias
    }

    /**
     * Test: Empty and null inputs.
     */
    @Test
    void testNormalize_EmptyAndNull() {
        assertEquals("", service.normalize(null));
        assertEquals("", service.normalize(""));
        assertEquals("", service.normalize("   "));
    }

    /**
     * Test: Multiple spaces are collapsed.
     */
    @Test
    void testNormalize_CollapseSpaces() {
        assertEquals("coconut chutney", service.normalize("Coconut    Chutney"));
        assertEquals("coconut chutney", service.normalize("Coconut\t\tChutney"));
    }

    // ===== Levenshtein Distance Tests =====

    /**
     * Test: Levenshtein distance for identical strings.
     */
    @Test
    void testLevenshteinDistance_IdenticalStrings() {
        assertEquals(0, service.levenshteinDistance("hello", "hello"));
        assertEquals(0, service.levenshteinDistance("", ""));
    }

    /**
     * Test: Levenshtein distance for single character edits.
     */
    @Test
    void testLevenshteinDistance_SingleEdits() {
        // Substitution
        assertEquals(1, service.levenshteinDistance("cat", "bat"));

        // Insertion
        assertEquals(1, service.levenshteinDistance("cat", "cats"));

        // Deletion
        assertEquals(1, service.levenshteinDistance("cats", "cat"));
    }

    /**
     * Test: Levenshtein distance for multiple edits.
     */
    @Test
    void testLevenshteinDistance_MultipleEdits() {
        assertEquals(3, service.levenshteinDistance("kitten", "sitting"));  // 3 edits
        assertEquals(3, service.levenshteinDistance("saturday", "sunday"));  // 3 edits
    }

    /**
     * Test: Levenshtein distance for completely different strings.
     */
    @Test
    void testLevenshteinDistance_DifferentStrings() {
        assertTrue(service.levenshteinDistance("abc", "xyz") >= 3);
    }

    /**
     * Test: Levenshtein distance with empty strings.
     */
    @Test
    void testLevenshteinDistance_EmptyStrings() {
        assertEquals(5, service.levenshteinDistance("", "hello"));
        assertEquals(5, service.levenshteinDistance("hello", ""));
    }

    /**
     * Test: Levenshtein distance with null inputs.
     */
    @Test
    void testLevenshteinDistance_NullInputs() {
        assertEquals(Integer.MAX_VALUE, service.levenshteinDistance(null, "hello"));
        assertEquals(Integer.MAX_VALUE, service.levenshteinDistance("hello", null));
    }

    /**
     * Test: Typo detection (distance 1-2).
     */
    @Test
    void testLevenshteinDistance_Typos() {
        // Common typos should have small distance
        assertEquals(1, service.levenshteinDistance("idli", "idly"));  // 1 edit (substitute i→y)
        assertEquals(1, service.levenshteinDistance("dosa", "dosai"));  // 1 edit (insert i)
        assertEquals(1, service.levenshteinDistance("rice", "rices"));  // 1 edit (insert s)
    }

    // ===== Fuzzy Matching Tests =====

    /**
     * Test: Exact match is found.
     */
    @Test
    void testFindBestMatch_ExactMatch() {
        List<UserIngredientLibrary> candidates = createCandidates(
                "idli", "dosa", "sambar"
        );

        Optional<UserIngredientLibrary> match = service.findBestMatch("idli", candidates);

        assertTrue(match.isPresent());
        assertEquals("idli", match.get().getIngredientName());
    }

    /**
     * Test: Fuzzy match within threshold (distance <= 2).
     */
    @Test
    void testFindBestMatch_FuzzyMatch() {
        List<UserIngredientLibrary> candidates = createCandidates(
                "idli", "dosa", "sambar"
        );

        // "idly" should match "idli" (distance 1)
        Optional<UserIngredientLibrary> match = service.findBestMatch("idly", candidates);

        assertTrue(match.isPresent());
        assertEquals("idli", match.get().getIngredientName());
    }

    /**
     * Test: No match beyond threshold.
     */
    @Test
    void testFindBestMatch_NoMatchBeyondThreshold() {
        List<UserIngredientLibrary> candidates = createCandidates(
                "idli", "dosa", "sambar"
        );

        // "pizza" is too different from any candidate
        Optional<UserIngredientLibrary> match = service.findBestMatch("pizza", candidates);

        assertFalse(match.isPresent());
    }

    /**
     * Test: Best match is chosen when multiple candidates are similar.
     */
    @Test
    void testFindBestMatch_BestMatchChosen() {
        List<UserIngredientLibrary> candidates = createCandidates(
                "rice", "rices", "ricee"  // All similar to "rice"
        );

        // Should match "rice" (exact match, distance 0)
        Optional<UserIngredientLibrary> match = service.findBestMatch("rice", candidates);

        assertTrue(match.isPresent());
        assertEquals("rice", match.get().getIngredientName());
    }

    /**
     * Test: Custom distance threshold.
     */
    @Test
    void testFindBestMatch_CustomThreshold() {
        // Test with non-aliased words to verify threshold logic
        List<UserIngredientLibrary> candidates = createCandidates("rice");

        // Threshold 0 = exact match only; "rices" has distance 1 from "rice"
        Optional<UserIngredientLibrary> match = service.findBestMatch("rices", candidates, 0);
        assertFalse(match.isPresent());

        // Threshold 1 = 1 edit allowed; "rices" should match "rice"
        match = service.findBestMatch("rices", candidates, 1);
        assertTrue(match.isPresent());

        // Threshold 2 = 2 edits allowed; "ricees" (distance 2) should match
        match = service.findBestMatch("ricees", candidates, 2);
        assertTrue(match.isPresent());

        // Beyond threshold; "pizza" is too different
        match = service.findBestMatch("pizza", candidates, 2);
        assertFalse(match.isPresent());
    }

    /**
     * Test: Empty candidates list.
     */
    @Test
    void testFindBestMatch_EmptyCandidates() {
        Optional<UserIngredientLibrary> match = service.findBestMatch("idli", List.of());

        assertFalse(match.isPresent());
    }

    /**
     * Test: Null or blank query.
     */
    @Test
    void testFindBestMatch_NullQuery() {
        List<UserIngredientLibrary> candidates = createCandidates("idli");

        assertFalse(service.findBestMatch(null, candidates).isPresent());
        assertFalse(service.findBestMatch("", candidates).isPresent());
        assertFalse(service.findBestMatch("   ", candidates).isPresent());
    }

    // ===== areSimilar Tests =====

    /**
     * Test: Similar ingredient names.
     */
    @Test
    void testAreSimilar_SimilarNames() {
        assertTrue(service.areSimilar("idli", "idly"));
        assertTrue(service.areSimilar("dosa", "dosai"));
        assertTrue(service.areSimilar("rice", "rices"));
        assertTrue(service.areSimilar("Coconut Chutney", "coconut chutney"));  // Case insensitive
    }

    /**
     * Test: Dissimilar ingredient names.
     */
    @Test
    void testAreSimilar_DissimilarNames() {
        assertFalse(service.areSimilar("idli", "dosa"));
        assertFalse(service.areSimilar("rice", "pizza"));
        assertFalse(service.areSimilar("sambar", "chutney"));
    }

    /**
     * Test: Exact matches are similar.
     */
    @Test
    void testAreSimilar_ExactMatch() {
        assertTrue(service.areSimilar("idli", "idli"));
        assertTrue(service.areSimilar("coconut chutney", "coconut chutney"));
    }

    /**
     * Test: Custom threshold.
     */
    @Test
    void testAreSimilar_CustomThreshold() {
        // "kitten" and "sitting" have distance 3
        assertFalse(service.areSimilar("kitten", "sitting", 2));  // Beyond threshold
        assertTrue(service.areSimilar("kitten", "sitting", 3));   // Within threshold
    }

    // ===== Edge Cases =====

    /**
     * Test: Unicode and special characters.
     */
    @Test
    void testNormalize_UnicodeAndSpecialChars() {
        assertEquals("idl", service.normalize("idlí"));  // Unicode accent removed (ASCII normalization)
        assertEquals("coconut chutney", service.normalize("Coconut/Chutney"));
        assertEquals("123 rice", service.normalize("123 Rice"));  // Numbers preserved
    }

    /**
     * Test: Very long ingredient names.
     */
    @Test
    void testLevenshteinDistance_LongStrings() {
        String long1 = "a".repeat(100);
        String long2 = "a".repeat(100) + "b";

        assertEquals(1, service.levenshteinDistance(long1, long2));
    }

    // ===== Helper Methods =====

    /**
     * Create test ingredient library entries.
     */
    private List<UserIngredientLibrary> createCandidates(String... names) {
        User testUser = new User();
        testUser.setId(UUID.randomUUID());

        return Arrays.stream(names)
                .map(name -> UserIngredientLibrary.builder()
                        .id(UUID.randomUUID())
                        .user(testUser)
                        .ingredientName(name)
                        .normalizedName(service.normalize(name))
                        .build())
                .toList();
    }
}
