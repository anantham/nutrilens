package com.nutritheous.ingredient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for normalizing ingredient names and fuzzy matching.
 * Handles typos, variants, plurals, and common aliases.
 *
 * Examples:
 * - "idly" → "idli"
 * - "Coconut Chutney  " → "coconut chutney"
 * - "dosais" → matches "dosa" with Levenshtein distance
 */
@Service
@Slf4j
public class IngredientNormalizationService {

    /**
     * Known aliases for common South Indian and other foods.
     * Maps variant spellings to canonical names.
     */
    private static final Map<String, String> ALIASES = new HashMap<>() {{
        // South Indian variations
        put("idly", "idli");
        put("idlies", "idli");
        put("dosai", "dosa");
        put("dosay", "dosa");
        put("dosais", "dosa");
        put("chutny", "chutney");
        put("sambhar", "sambar");
        put("sambhaar", "sambar");
        put("vadai", "vada");
        put("vade", "vada");

        // Common variations
        put("yogurt", "yoghurt");
        put("yoghourt", "yoghurt");
        put("curd", "yoghurt");
        put("paneer", "cottage cheese");
        put("dal", "lentils");
        put("daal", "lentils");
        put("roti", "chapati");
        put("chapathi", "chapati");

        // Vegetables
        put("brinjal", "eggplant");
        put("aubergine", "eggplant");
        put("capsicum", "bell pepper");
        put("ladies finger", "okra");
        put("ladyfinger", "okra");

        // Grains
        put("atta", "wheat flour");
        put("maida", "all purpose flour");
        put("sooji", "semolina");
        put("rava", "semolina");
    }};

    /**
     * Common plural suffixes to strip during normalization.
     */
    private static final String[] PLURAL_SUFFIXES = {"ies", "es", "s"};

    /**
     * Normalize an ingredient name to a canonical form.
     *
     * Normalization steps:
     * 1. Lowercase
     * 2. Trim whitespace
     * 3. Remove special characters (keep alphanumeric and spaces)
     * 4. Apply known aliases
     * 5. Handle plurals
     *
     * @param ingredientName Raw ingredient name
     * @return Normalized ingredient name
     */
    public String normalize(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return "";
        }

        // Step 1-2: Lowercase and trim
        String normalized = ingredientName.toLowerCase().trim();

        // Step 3: Replace special characters with spaces to preserve word boundaries
        // This converts "Coconut, Chutney!" → "coconut  chutney " instead of "coconutchutney"
        normalized = normalized.replaceAll("[^a-z0-9\\s]", " ");

        // Collapse multiple spaces and trim
        normalized = normalized.replaceAll("\\s+", " ").trim();

        // Step 4: Apply known aliases
        if (ALIASES.containsKey(normalized)) {
            String alias = ALIASES.get(normalized);
            log.debug("Normalized '{}' to alias '{}'", ingredientName, alias);
            return alias;
        }

        // Step 5: Try removing plural suffixes and check aliases again
        for (String suffix : PLURAL_SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length() + 2) {
                String singular = normalized.substring(0, normalized.length() - suffix.length());
                if (ALIASES.containsKey(singular)) {
                    log.debug("Normalized plural '{}' to alias '{}'", ingredientName, ALIASES.get(singular));
                    return ALIASES.get(singular);
                }
            }
        }

        return normalized;
    }

    /**
     * Find the best matching ingredient from a list using fuzzy matching.
     *
     * Uses Levenshtein distance to find ingredients similar to the query.
     * Returns match if edit distance is within threshold.
     *
     * @param query Normalized ingredient name to search for
     * @param candidates List of existing ingredients from user's library
     * @param maxDistance Maximum Levenshtein distance to consider a match (default: 2)
     * @return Best matching ingredient, or empty if no match within threshold
     */
    public Optional<UserIngredientLibrary> findBestMatch(
            String query,
            List<UserIngredientLibrary> candidates,
            int maxDistance
    ) {
        if (query == null || query.isBlank() || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        String normalizedQuery = normalize(query);

        UserIngredientLibrary bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (UserIngredientLibrary candidate : candidates) {
            String candidateName = candidate.getNormalizedName();

            // Exact match
            if (normalizedQuery.equals(candidateName)) {
                log.debug("Found exact match for '{}': {}", query, candidate.getIngredientName());
                return Optional.of(candidate);
            }

            // Fuzzy match
            int distance = levenshteinDistance(normalizedQuery, candidateName);
            if (distance < bestDistance && distance <= maxDistance) {
                bestDistance = distance;
                bestMatch = candidate;
            }
        }

        if (bestMatch != null) {
            log.debug("Found fuzzy match for '{}' → '{}' (distance: {})",
                    query, bestMatch.getIngredientName(), bestDistance);
            return Optional.of(bestMatch);
        }

        log.debug("No match found for '{}' within distance {}", query, maxDistance);
        return Optional.empty();
    }

    /**
     * Overload with default maxDistance of 2.
     */
    public Optional<UserIngredientLibrary> findBestMatch(
            String query,
            List<UserIngredientLibrary> candidates
    ) {
        return findBestMatch(query, candidates, 2);
    }

    /**
     * Calculate Levenshtein distance between two strings.
     *
     * The Levenshtein distance is the minimum number of single-character edits
     * (insertions, deletions, or substitutions) required to change one string
     * into another.
     *
     * Algorithm: Dynamic programming with O(m*n) time and space complexity.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Edit distance between the strings
     */
    public int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }

        if (s1.equals(s2)) {
            return 0;
        }

        int m = s1.length();
        int n = s2.length();

        // Handle empty strings
        if (m == 0) return n;
        if (n == 0) return m;

        // Create DP table
        int[][] dp = new int[m + 1][n + 1];

        // Initialize base cases
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;  // Cost of deletions
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;  // Cost of insertions
        }

        // Fill DP table
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    // Characters match - no cost
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    // Take minimum of three operations
                    int substitution = dp[i - 1][j - 1] + 1;
                    int deletion = dp[i - 1][j] + 1;
                    int insertion = dp[i][j - 1] + 1;
                    dp[i][j] = Math.min(substitution, Math.min(deletion, insertion));
                }
            }
        }

        return dp[m][n];
    }

    /**
     * Check if two ingredient names are similar enough to be considered the same.
     *
     * @param name1 First ingredient name
     * @param name2 Second ingredient name
     * @param threshold Maximum edit distance to consider similar (default: 2)
     * @return True if ingredients are similar
     */
    public boolean areSimilar(String name1, String name2, int threshold) {
        String normalized1 = normalize(name1);
        String normalized2 = normalize(name2);

        if (normalized1.equals(normalized2)) {
            return true;
        }

        int distance = levenshteinDistance(normalized1, normalized2);
        return distance <= threshold;
    }

    /**
     * Overload with default threshold of 2.
     */
    public boolean areSimilar(String name1, String name2) {
        return areSimilar(name1, name2, 2);
    }
}
