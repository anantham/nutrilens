package com.nutritheous.correction;

import com.nutritheous.correction.dto.FieldAccuracyStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for CorrectionAnalyticsService
 */
@ExtendWith(MockitoExtension.class)
class CorrectionAnalyticsServiceTest {

    @Mock
    private AiCorrectionLogRepository correctionLogRepository;

    private CorrectionAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new CorrectionAnalyticsService(correctionLogRepository);
    }

    @Test
    void testGetOverallAccuracy_EmptyResults() {
        // Mock empty results
        when(correctionLogRepository.getAccuracyStatsByField()).thenReturn(new ArrayList<>());

        List<FieldAccuracyStats> stats = analyticsService.getOverallAccuracy();

        assertNotNull(stats);
        assertTrue(stats.isEmpty());
        verify(correctionLogRepository, times(1)).getAccuracyStatsByField();
    }

    @Test
    void testGetOverallAccuracy_WithData() {
        // Mock repository returning sample data
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{"calories", BigDecimal.valueOf(15.5), 100, BigDecimal.valueOf(75.2)});
        mockResults.add(new Object[]{"protein_g", BigDecimal.valueOf(12.3), 80, BigDecimal.valueOf(5.1)});

        when(correctionLogRepository.getAccuracyStatsByField()).thenReturn(mockResults);

        List<FieldAccuracyStats> stats = analyticsService.getOverallAccuracy();

        assertNotNull(stats);
        assertEquals(2, stats.size());

        // Check first stat (calories)
        FieldAccuracyStats caloriesStat = stats.get(0);
        assertEquals("calories", caloriesStat.getFieldName());
        assertEquals(0, caloriesStat.getAvgAbsPercentError().compareTo(BigDecimal.valueOf(15.5)));
        assertEquals(100L, caloriesStat.getCorrectionCount());
        assertEquals(0, caloriesStat.getMeanAbsoluteError().compareTo(BigDecimal.valueOf(75.2)));

        // Check second stat (protein)
        FieldAccuracyStats proteinStat = stats.get(1);
        assertEquals("protein_g", proteinStat.getFieldName());
        assertEquals(0, proteinStat.getAvgAbsPercentError().compareTo(BigDecimal.valueOf(12.3)));

        verify(correctionLogRepository, times(1)).getAccuracyStatsByField();
    }

    @Test
    void testDetectSystematicBias() {
        // Mock data showing AI underestimates calories, overestimates protein
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{"calories", BigDecimal.valueOf(18.5)});    // Positive = underestimate
        mockResults.add(new Object[]{"protein_g", BigDecimal.valueOf(-12.3)});  // Negative = overestimate

        when(correctionLogRepository.detectSystematicBias()).thenReturn(mockResults);

        List<FieldAccuracyStats> biases = analyticsService.detectSystematicBias();

        assertNotNull(biases);
        assertEquals(2, biases.size());

        // Verify calories bias (AI underestimates)
        FieldAccuracyStats caloriesBias = biases.get(0);
        assertEquals("calories", caloriesBias.getFieldName());
        assertTrue(caloriesBias.getBias().compareTo(BigDecimal.ZERO) > 0,
                "Positive bias should indicate underestimation");

        // Verify protein bias (AI overestimates)
        FieldAccuracyStats proteinBias = biases.get(1);
        assertEquals("protein_g", proteinBias.getFieldName());
        assertTrue(proteinBias.getBias().compareTo(BigDecimal.ZERO) < 0,
                "Negative bias should indicate overestimation");

        verify(correctionLogRepository, times(1)).detectSystematicBias();
    }

    @Test
    void testGetFieldSummary() {
        // Mock repository methods
        when(correctionLogRepository.calculateAverageErrorByField("calories"))
                .thenReturn(BigDecimal.valueOf(15.5));
        when(correctionLogRepository.calculateMAEByField("calories"))
                .thenReturn(BigDecimal.valueOf(75.2));
        when(correctionLogRepository.findByFieldName("calories"))
                .thenReturn(createMockCorrections(100)); // 100 corrections

        FieldAccuracyStats stats = analyticsService.getFieldSummary("calories");

        assertNotNull(stats);
        assertEquals("calories", stats.getFieldName());
        assertEquals(0, stats.getAvgAbsPercentError().compareTo(BigDecimal.valueOf(15.5)));
        assertEquals(0, stats.getMeanAbsoluteError().compareTo(BigDecimal.valueOf(75.2)));
        assertEquals(100L, stats.getCorrectionCount());

        verify(correctionLogRepository, times(1)).calculateAverageErrorByField("calories");
        verify(correctionLogRepository, times(1)).calculateMAEByField("calories");
    }

    @Test
    void testGenerateAccuracyReport_ContainsExpectedSections() {
        // Mock minimal data
        when(correctionLogRepository.getAccuracyStatsByField()).thenReturn(new ArrayList<>());
        when(correctionLogRepository.getAccuracyByLocationType()).thenReturn(new ArrayList<>());
        when(correctionLogRepository.detectSystematicBias()).thenReturn(new ArrayList<>());

        String report = analyticsService.generateAccuracyReport();

        assertNotNull(report);
        assertTrue(report.contains("=== AI Accuracy Report ==="));
        assertTrue(report.contains("Overall Accuracy by Field:"));
        assertTrue(report.contains("Accuracy by Location:"));
        assertTrue(report.contains("Systematic Bias Detection:"));

        verify(correctionLogRepository, times(1)).getAccuracyStatsByField();
        verify(correctionLogRepository, times(1)).getAccuracyByLocationType();
        verify(correctionLogRepository, times(1)).detectSystematicBias();
    }

    @Test
    void testGetHighConfidenceAccuracy() {
        BigDecimal minConfidence = BigDecimal.valueOf(0.8);
        BigDecimal expectedError = BigDecimal.valueOf(10.5);

        when(correctionLogRepository.calculateErrorForHighConfidence(minConfidence))
                .thenReturn(expectedError);

        BigDecimal result = analyticsService.getHighConfidenceAccuracy(minConfidence);

        assertEquals(expectedError, result);
        verify(correctionLogRepository, times(1)).calculateErrorForHighConfidence(minConfidence);
    }

    // Helper method to create mock correction logs
    private List<AiCorrectionLog> createMockCorrections(int count) {
        List<AiCorrectionLog> corrections = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            corrections.add(new AiCorrectionLog());
        }
        return corrections;
    }
}
