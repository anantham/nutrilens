package com.nutritheous.performance;

import com.nutritheous.common.dto.AnalysisResponse;
import com.nutritheous.validation.AiValidationService;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH Performance Benchmarks for Critical Code Paths
 *
 * Phase 4: Performance Testing
 *
 * Run with: ./gradlew jmh
 * Compare with baseline: ./gradlew jmhBaseline
 *
 * Performance Quality Gates:
 * - Validation: < 1ms per operation
 * - Calorie calculation: < 100μs per operation
 * - No performance regression > 10%
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class NutritionValidationBenchmark {

    private AiValidationService validationService;
    private AnalysisResponse validResponse;
    private AnalysisResponse invalidResponse;
    private AnalysisResponse complexResponse;

    @Setup
    public void setup() {
        validationService = new AiValidationService();

        // Valid response (typical case)
        validResponse = AnalysisResponse.builder()
                .calories(500)
                .proteinG(25.0)
                .fatG(15.0)
                .carbohydratesG(60.0)
                .fiberG(5.0)
                .sugarG(10.0)
                .saturatedFatG(4.0)
                .sodiumMg(400.0)
                .confidence(0.85)
                .build();

        // Invalid response (fiber > carbs)
        invalidResponse = AnalysisResponse.builder()
                .calories(300)
                .proteinG(10.0)
                .fatG(5.0)
                .carbohydratesG(20.0)
                .fiberG(25.0)  // Invalid: exceeds carbs
                .sugarG(5.0)
                .confidence(0.75)
                .build();

        // Complex response (all fields populated)
        complexResponse = AnalysisResponse.builder()
                .calories(800)
                .proteinG(50.0)
                .fatG(30.0)
                .carbohydratesG(100.0)
                .fiberG(15.0)
                .sugarG(20.0)
                .saturatedFatG(10.0)
                .transFatG(0.5)
                .cholesterolMg(150.0)
                .sodiumMg(800.0)
                .potassiumMg(500.0)
                .calciumMg(200.0)
                .ironMg(5.0)
                .vitaminAMcg(100.0)
                .vitaminCMg(50.0)
                .confidence(0.90)
                .build();
    }

    /**
     * Benchmark: Validate typical valid response
     *
     * Expected: < 500 microseconds (0.5ms)
     * Critical: This runs on every meal upload
     */
    @Benchmark
    public void benchmarkValidateTypicalValidResponse(Blackhole blackhole) {
        blackhole.consume(validationService.validate(validResponse));
    }

    /**
     * Benchmark: Validate invalid response (fiber > carbs)
     *
     * Expected: < 500 microseconds (0.5ms)
     * Should be same speed as valid - validation shouldn't short-circuit
     */
    @Benchmark
    public void benchmarkValidateInvalidResponse(Blackhole blackhole) {
        blackhole.consume(validationService.validate(invalidResponse));
    }

    /**
     * Benchmark: Validate complex response (all fields)
     *
     * Expected: < 1 millisecond (1000 microseconds)
     * Worst case: All validation rules checked
     */
    @Benchmark
    public void benchmarkValidateComplexResponse(Blackhole blackhole) {
        blackhole.consume(validationService.validate(complexResponse));
    }

    /**
     * Benchmark: Calorie calculation (Atwater factors)
     *
     * Expected: < 100 microseconds
     * This is pure math, should be very fast
     */
    @Benchmark
    public void benchmarkCalorieCalculation(Blackhole blackhole) {
        // Atwater calculation: protein*4 + fat*9 + carbs*4
        double protein = validResponse.getProteinG();
        double fat = validResponse.getFatG();
        double carbs = validResponse.getCarbohydratesG();

        int calculatedCalories = (int) (protein * 4 + fat * 9 + carbs * 4);

        blackhole.consume(calculatedCalories);
    }

    /**
     * Benchmark: Energy balance check
     *
     * Expected: < 200 microseconds
     * Tests the thermodynamic validation
     */
    @Benchmark
    public void benchmarkEnergyBalanceCheck(Blackhole blackhole) {
        double protein = validResponse.getProteinG();
        double fat = validResponse.getFatG();
        double carbs = validResponse.getCarbohydratesG();
        int reportedCalories = validResponse.getCalories();

        int calculatedCalories = (int) (protein * 4 + fat * 9 + carbs * 4);
        double percentDiff = Math.abs(calculatedCalories - reportedCalories) / (double) calculatedCalories;

        boolean valid = percentDiff <= 0.20;  // 20% tolerance

        blackhole.consume(valid);
    }
}
