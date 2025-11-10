package com.nutritheous.correction;

import com.nutritheous.auth.User;
import com.nutritheous.meal.Meal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for AiCorrectionLog entity, focusing on error calculation logic.
 */
class AiCorrectionLogTest {

    @Test
    void testErrorCalculation_AIUnderestimates() {
        // AI said 500 cal, user corrected to 650 cal
        // Expected: percentError = ((650-500)/650)*100 = 23.08%, absoluteError = 150
        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("calories")
                .aiValue(BigDecimal.valueOf(500))
                .userValue(BigDecimal.valueOf(650))
                .build();

        assertNotNull(log.getPercentError(), "Percent error should be auto-calculated");
        assertNotNull(log.getAbsoluteError(), "Absolute error should be auto-calculated");

        // Percent error = (650 - 500) / 650 * 100 = 23.08%
        assertEquals(0, log.getPercentError().compareTo(BigDecimal.valueOf(23.08)),
                "Percent error should be 23.08% (AI underestimated by 23%)");

        // Absolute error = |650 - 500| = 150
        assertEquals(0, log.getAbsoluteError().compareTo(BigDecimal.valueOf(150)),
                "Absolute error should be 150");
    }

    @Test
    void testErrorCalculation_AIOverestimates() {
        // AI said 800 cal, user corrected to 600 cal
        // Expected: percentError = ((600-800)/600)*100 = -33.33%, absoluteError = 200
        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("calories")
                .aiValue(BigDecimal.valueOf(800))
                .userValue(BigDecimal.valueOf(600))
                .build();

        // Percent error = (600 - 800) / 600 * 100 = -33.33%
        assertEquals(0, log.getPercentError().compareTo(BigDecimal.valueOf(-33.33)),
                "Percent error should be -33.33% (AI overestimated by 33%)");

        // Absolute error = |600 - 800| = 200
        assertEquals(0, log.getAbsoluteError().compareTo(BigDecimal.valueOf(200)),
                "Absolute error should be 200");
    }

    @Test
    void testErrorCalculation_PerfectMatch() {
        // AI said 500 cal, user also said 500 cal (no correction needed, but testing edge case)
        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("calories")
                .aiValue(BigDecimal.valueOf(500))
                .userValue(BigDecimal.valueOf(500))
                .build();

        // Both should be zero
        assertEquals(0, log.getPercentError().compareTo(BigDecimal.ZERO),
                "Percent error should be 0% for perfect match");
        assertEquals(0, log.getAbsoluteError().compareTo(BigDecimal.ZERO),
                "Absolute error should be 0 for perfect match");
    }

    @Test
    void testErrorCalculation_SmallValues() {
        // AI said 8g fiber, user corrected to 10g
        // Expected: percentError = ((10-8)/10)*100 = 20%, absoluteError = 2
        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("fiber_g")
                .aiValue(BigDecimal.valueOf(8))
                .userValue(BigDecimal.valueOf(10))
                .build();

        assertEquals(0, log.getPercentError().compareTo(BigDecimal.valueOf(20.00)),
                "Percent error should be 20%");
        assertEquals(0, log.getAbsoluteError().compareTo(BigDecimal.valueOf(2)),
                "Absolute error should be 2");
    }

    @Test
    void testErrorCalculation_DecimalValues() {
        // AI said 35.5g protein, user corrected to 42.3g
        // Expected: percentError = ((42.3-35.5)/42.3)*100 = 16.08%, absoluteError = 6.8
        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("protein_g")
                .aiValue(BigDecimal.valueOf(35.5))
                .userValue(BigDecimal.valueOf(42.3))
                .build();

        // Allow small rounding differences
        assertTrue(log.getPercentError().subtract(BigDecimal.valueOf(16.08)).abs()
                        .compareTo(BigDecimal.valueOf(0.01)) < 0,
                "Percent error should be approximately 16.08%");

        assertEquals(0, log.getAbsoluteError().compareTo(BigDecimal.valueOf(6.8)),
                "Absolute error should be 6.8");
    }

    @Test
    void testErrorCalculation_NullValues() {
        // Test that null values don't cause NPE
        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("calories")
                .aiValue(null)
                .userValue(BigDecimal.valueOf(500))
                .build();

        // Should not crash, errors should be null
        assertNull(log.getPercentError(), "Percent error should be null when AI value is null");
        assertNull(log.getAbsoluteError(), "Absolute error should be null when AI value is null");
    }

    @Test
    void testManualErrorCalculation() {
        // Test the calculateErrors() method directly
        AiCorrectionLog log = new AiCorrectionLog();
        log.setAiValue(BigDecimal.valueOf(500));
        log.setUserValue(BigDecimal.valueOf(650));

        // Initially null
        assertNull(log.getPercentError());
        assertNull(log.getAbsoluteError());

        // Calculate manually
        log.calculateErrors();

        // Now should be populated
        assertNotNull(log.getPercentError());
        assertNotNull(log.getAbsoluteError());
        assertEquals(0, log.getPercentError().compareTo(BigDecimal.valueOf(23.08)));
    }

    @Test
    void testBuilderAutomaticallySetsTimestamp() {
        // Test that builder sets correctedAt automatically if not provided
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        AiCorrectionLog log = AiCorrectionLog.builder()
                .fieldName("calories")
                .aiValue(BigDecimal.valueOf(500))
                .userValue(BigDecimal.valueOf(600))
                .build();

        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertNotNull(log.getCorrectedAt(), "Corrected timestamp should be auto-set");
        assertTrue(log.getCorrectedAt().isAfter(before) && log.getCorrectedAt().isBefore(after),
                "Corrected timestamp should be close to now");
    }

    @Test
    void testFullLogCreation() {
        // Test creating a complete correction log with all fields
        User user = new User();
        user.setId(java.util.UUID.randomUUID());

        Meal meal = new Meal();
        meal.setId(java.util.UUID.randomUUID());

        AiCorrectionLog log = AiCorrectionLog.builder()
                .meal(meal)
                .user(user)
                .fieldName("calories")
                .aiValue(BigDecimal.valueOf(500))
                .userValue(BigDecimal.valueOf(650))
                .confidenceScore(BigDecimal.valueOf(0.85))
                .locationType("restaurant")
                .locationPlaceName("Chipotle")
                .mealType("lunch")
                .mealDescription("Burrito bowl with chicken")
                .aiAnalyzedAt(LocalDateTime.now().minusHours(1))
                .correctedAt(LocalDateTime.now())
                .build();

        // Verify all fields are set
        assertNotNull(log.getMeal());
        assertNotNull(log.getUser());
        assertEquals("calories", log.getFieldName());
        assertEquals("restaurant", log.getLocationType());
        assertEquals("Chipotle", log.getLocationPlaceName());
        assertNotNull(log.getPercentError());
        assertNotNull(log.getAbsoluteError());
    }
}
