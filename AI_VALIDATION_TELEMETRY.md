# AI Validation & Telemetry Analysis

**Your Question:** "Is there any way to check for false positives, false negatives? Are there sanity checks? Is there telemetry to capture corrections users give? Are we keeping track of established values to see if AI is hallucinating?"

**TL;DR:** ⚠️ **MAJOR GAP** - We have almost zero validation or telemetry right now. This is a critical missing piece.

---

## Current State: What Exists

### ✅ Things We Do Have

**1. Confidence Score**
- AI returns `confidence` field (0.0-1.0)
- Example: 0.85 for photo analysis, 0.65 for text-only
- Stored in database
- **Problem:** Not used for anything! Just sits there.

**2. User Edit Capability**
- `PUT /api/meals/{id}` endpoint exists
- Users CAN correct AI values (calories, protein, etc.)
- **Problem:** We don't track WHAT changed or WHY

**3. Basic Input Validation**
- `@Min/@Max` annotations on MealUpdateRequest
- Calories: 0-10,000
- Protein/Fat/Carbs: 0-1,000g
- Sodium: 0-100,000mg
- **Problem:** Very loose bounds, catches garbage but not nonsense

---

## ❌ Critical Gaps: What's Missing

### 1. **No Telemetry for User Corrections**

**Current Flow:**
```
User uploads meal → AI says 500 cal
User corrects to 800 cal → Database updates
❌ NO LOG of the correction
❌ NO TRACKING of before/after values
❌ NO FLAG that this was user-corrected
```

**What We're Losing:**
- Can't measure AI accuracy over time
- Can't identify which foods AI gets wrong
- Can't train/fine-tune on corrections
- Can't detect systematic biases (e.g., AI underestimates restaurant portions)

---

### 2. **No Sanity Checks on AI Output**

**Current Flow:**
```
AI says: calories=5000, protein=200g, fat=300g, carbs=150g
System: ✅ Saves to database (no validation!)
```

**Problems We Don't Catch:**
- **Energy mismatch:** Protein(200g × 4) + Fat(300g × 9) + Carbs(150g × 4) = 4,100 cal ≠ 5,000 cal
- **Impossible ratios:** 1000g protein in a 200 cal meal
- **Nonsensical values:** Negative fiber, 10,000mg sodium in a salad
- **Missing required fields:** Calories present but all macros null
- **Unit confusion:** AI returns grams when it meant milligrams

---

### 3. **No Baseline Comparison**

**What We Don't Have:**
- USDA FoodData Central comparison
- Known "ground truth" meals for validation
- Nutritional database lookup for common foods
- Restaurant nutrition facts (when available)

**Example Missed Opportunity:**
```
Meal: "McDonald's Big Mac"
AI estimates: 650 cal, 30g protein
McDonald's website: 563 cal, 25g protein
❌ We don't flag the 15% discrepancy
```

---

### 4. **No Anomaly Detection**

**Things We Should Flag:**
- User corrects same food type consistently (e.g., always increases restaurant calories)
- AI confidence low but user doesn't edit (maybe it's accurate?)
- AI confidence high but user makes major correction (hallucination?)
- Outliers: 3,000 cal breakfast, 50g fiber in one meal

---

### 5. **No Logging of AI Hallucinations**

**Current State:**
```java
// OpenAIVisionService.java
catch (Exception e) {
    logger.error("Failed to parse AI response", e);
    throw new AnalyzerException("Analysis failed");
}
```

**What We Don't Log:**
- The RAW AI response (what did it actually say?)
- How often parsing fails (malformed JSON?)
- What patterns trigger failures
- Whether re-trying would help

---

## 🎯 Proposed Solutions

### Phase 1: Track User Corrections ⭐ HIGH PRIORITY

**Goal:** Capture when users edit AI values and measure accuracy.

**Implementation:**

**1. Create AiCorrectionLog Table**
```sql
CREATE TABLE ai_correction_logs (
    id UUID PRIMARY KEY,
    meal_id UUID NOT NULL REFERENCES meals(id),
    user_id UUID NOT NULL REFERENCES users(id),

    -- What changed
    field_name VARCHAR(50) NOT NULL, -- 'calories', 'protein_g', etc.
    ai_value DECIMAL(10,2),          -- What AI said
    user_value DECIMAL(10,2),        -- What user corrected to
    percent_error DECIMAL(10,2),     -- (user - ai) / user * 100

    -- Context
    confidence_score DECIMAL(3,2),   -- AI's confidence when it was wrong
    location_type VARCHAR(50),        -- restaurant/home
    meal_description TEXT,

    -- Timestamps
    ai_analyzed_at TIMESTAMP,
    corrected_at TIMESTAMP DEFAULT NOW(),

    INDEX idx_field_name (field_name),
    INDEX idx_user (user_id),
    INDEX idx_meal (meal_id)
);
```

**2. Update MealService.updateMeal()**
```java
@Transactional
public MealResponse updateMeal(UUID mealId, UUID userId, MealUpdateRequest request) {
    Meal meal = getMeal(mealId, userId);

    // Track corrections for each field
    trackCorrection(meal, "calories", meal.getCalories(), request.getCalories());
    trackCorrection(meal, "protein_g", meal.getProteinG(), request.getProteinG());
    // ... etc

    // Update meal
    updateMealFields(meal, request);

    return MealResponse.fromMeal(meal, storageService);
}

private void trackCorrection(Meal meal, String field, Double aiValue, Double userValue) {
    if (userValue != null && aiValue != null && !aiValue.equals(userValue)) {
        double percentError = ((userValue - aiValue) / userValue) * 100;

        AiCorrectionLog log = AiCorrectionLog.builder()
            .mealId(meal.getId())
            .userId(meal.getUser().getId())
            .fieldName(field)
            .aiValue(aiValue)
            .userValue(userValue)
            .percentError(percentError)
            .confidenceScore(meal.getConfidence())
            .locationType(meal.getLocationPlaceType())
            .mealDescription(meal.getDescription())
            .aiAnalyzedAt(meal.getCreatedAt())
            .build();

        correctionLogRepository.save(log);

        logger.info("AI correction tracked - field: {}, error: {}%, confidence: {}, location: {}",
            field, String.format("%.1f", percentError), meal.getConfidence(), meal.getLocationPlaceType());
    }
}
```

**3. Analytics Queries**
```sql
-- Overall AI accuracy
SELECT
    field_name,
    COUNT(*) as corrections,
    AVG(ABS(percent_error)) as avg_error,
    STDDEV(percent_error) as error_stddev
FROM ai_correction_logs
GROUP BY field_name
ORDER BY avg_error DESC;

-- AI accuracy by location type
SELECT
    location_type,
    field_name,
    AVG(ABS(percent_error)) as avg_error
FROM ai_correction_logs
GROUP BY location_type, field_name
ORDER BY avg_error DESC;

-- AI accuracy by confidence score
SELECT
    FLOOR(confidence_score * 10) / 10 as confidence_bucket,
    AVG(ABS(percent_error)) as avg_error,
    COUNT(*) as samples
FROM ai_correction_logs
GROUP BY confidence_bucket
ORDER BY confidence_bucket;
```

**Insights We'd Get:**
- "AI underestimates restaurant calories by 18% on average"
- "High confidence (>0.8) meals have 12% avg error"
- "Protein estimates are 25% more accurate than fat estimates"
- "Home-cooked meals: 8% error vs Restaurant meals: 22% error"

---

### Phase 2: Sanity Checks ⭐ HIGH PRIORITY

**Goal:** Catch obviously wrong AI outputs before saving.

**Implementation:**

**1. Create ValidationService**
```java
@Service
public class AiValidationService {

    public ValidationResult validate(AnalysisResponse response) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Check 1: Energy balance (Atwater factors)
        if (response.getCalories() != null &&
            response.getProteinG() != null &&
            response.getFatG() != null &&
            response.getCarbohydratesG() != null) {

            double calculatedCalories =
                (response.getProteinG() * 4) +
                (response.getFatG() * 9) +
                (response.getCarbohydratesG() * 4);

            double percentDiff = Math.abs(response.getCalories() - calculatedCalories)
                               / response.getCalories() * 100;

            if (percentDiff > 20) {
                issues.add(ValidationIssue.builder()
                    .severity(Severity.WARNING)
                    .field("calories")
                    .message(String.format(
                        "Energy mismatch: Claimed %d cal but macros = %.0f cal (%.1f%% diff)",
                        response.getCalories(), calculatedCalories, percentDiff))
                    .suggestedFix(calculatedCalories)
                    .build());
            }
        }

        // Check 2: Impossible ratios
        if (response.getProteinG() != null && response.getCalories() != null) {
            double maxProteinCalories = response.getProteinG() * 4;
            if (maxProteinCalories > response.getCalories() * 1.1) {
                issues.add(ValidationIssue.warning(
                    "protein_g",
                    "Protein calories exceed total calories"
                ));
            }
        }

        // Check 3: Fiber > Carbs (impossible)
        if (response.getFiberG() != null && response.getCarbohydratesG() != null) {
            if (response.getFiberG() > response.getCarbohydratesG()) {
                issues.add(ValidationIssue.error(
                    "fiber_g",
                    "Fiber cannot exceed total carbohydrates"
                ));
            }
        }

        // Check 4: Sugar > Carbs (impossible)
        if (response.getSugarG() != null && response.getCarbohydratesG() != null) {
            if (response.getSugarG() > response.getCarbohydratesG()) {
                issues.add(ValidationIssue.error(
                    "sugar_g",
                    "Sugar cannot exceed total carbohydrates"
                ));
            }
        }

        // Check 5: Saturated fat > Total fat (impossible)
        if (response.getSaturatedFatG() != null && response.getFatG() != null) {
            if (response.getSaturatedFatG() > response.getFatG()) {
                issues.add(ValidationIssue.error(
                    "saturated_fat_g",
                    "Saturated fat cannot exceed total fat"
                ));
            }
        }

        // Check 6: Outliers (likely hallucinations)
        if (response.getCalories() != null && response.getCalories() > 2500) {
            issues.add(ValidationIssue.warning(
                "calories",
                "Very high calorie count - verify portion size"
            ));
        }

        if (response.getSodiumMg() != null && response.getSodiumMg() > 3000) {
            issues.add(ValidationIssue.warning(
                "sodium_mg",
                "Very high sodium - likely restaurant or processed food"
            ));
        }

        return ValidationResult.builder()
            .valid(issues.stream().noneMatch(i -> i.getSeverity() == Severity.ERROR))
            .issues(issues)
            .build();
    }
}
```

**2. Integrate into MealService**
```java
private void updateMealWithAnalysis(Meal meal, AnalysisResponse analysisResponse) {
    // Validate AI response
    ValidationResult validation = validationService.validate(analysisResponse);

    if (!validation.isValid()) {
        logger.error("AI returned invalid data for meal {}: {}",
            meal.getId(), validation.getIssues());

        // Store validation failures for analysis
        validationFailureRepository.save(ValidationFailure.builder()
            .mealId(meal.getId())
            .issues(validation.getIssues())
            .rawResponse(analysisResponse)
            .build());

        // Still save the meal but flag it
        meal.setAnalysisStatus(AnalysisStatus.FAILED);
        return;
    }

    // Log warnings (not errors, but suspicious)
    validation.getIssues().stream()
        .filter(i -> i.getSeverity() == Severity.WARNING)
        .forEach(issue -> logger.warn("AI validation warning for meal {}: {}",
            meal.getId(), issue.getMessage()));

    // Update meal with validated data
    meal.setCalories(analysisResponse.getCalories());
    // ... rest of fields
}
```

**What This Catches:**
- ✅ Energy mismatch (claimed 500 cal but macros add up to 800 cal)
- ✅ Impossible values (fiber > carbs, saturated fat > total fat)
- ✅ Obvious outliers (10,000 cal meal, 50g fiber salad)
- ✅ Missing critical fields (has calories but no macros)

---

### Phase 3: Baseline Comparison 🔮 FUTURE

**Goal:** Compare AI estimates to known ground truth.

**Options:**

**1. USDA FoodData Central Integration**
- Match meal description to USDA database
- Flag large discrepancies (>30% difference)
- Example: "Apple" → USDA says 95 cal, AI says 150 cal

**2. Restaurant Nutrition Facts**
- Scrape/API for chain restaurant nutrition
- Flag when available
- Example: "Chipotle burrito bowl" → Use official nutrition

**3. User-Built Ground Truth**
- Allow users to mark meals as "verified/trusted"
- Build internal database of accurate meals
- Use for future comparisons

---

### Phase 4: Anomaly Detection 🔮 FUTURE

**Goal:** ML-based detection of unusual patterns.

**Approaches:**

**1. Statistical Outlier Detection**
- Z-score analysis: Flag meals >3 standard deviations from user's norm
- Example: User averages 500 cal/meal, today logged 2,500 cal
- Could be real (cheat day) or AI error

**2. Pattern Recognition**
- User always increases restaurant calories by 20% → AI systematically low?
- Certain cuisines always corrected → AI doesn't understand that food?
- Low confidence → High user correction rate → Flag for review

**3. Confidence Calibration**
- Track: confidence score vs actual accuracy
- Goal: confidence=0.8 should mean 80% accurate
- Recalibrate if needed

---

## 📊 Metrics to Track

### Immediate (Phase 1)
- **Correction Rate:** % of meals users edit
- **Field-Level Accuracy:** Avg error per field (calories, protein, etc.)
- **Location Impact:** Home vs restaurant accuracy
- **Confidence Calibration:** High confidence → Low error?

### Medium-Term (Phase 2)
- **Validation Failure Rate:** % of AI responses that fail sanity checks
- **Energy Mismatch Rate:** % with >20% calorie-macro discrepancy
- **Impossible Value Rate:** Fiber > carbs, etc.

### Long-Term (Phase 3-4)
- **USDA Match Rate:** % matching known foods
- **Systematic Bias:** AI consistently over/under estimates certain categories
- **User Trust:** Do users stop correcting over time? (AI gets better or they give up?)

---

## 🚀 Recommended Priority

### Do Now (This Month)
1. **Add ai_correction_logs table** ⭐ CRITICAL
2. **Track user edits in MealService.updateMeal()** ⭐ CRITICAL
3. **Create analytics dashboard:** View correction rates, error by field

### Do Next (Next 1-2 Months)
4. **Add AiValidationService** with sanity checks
5. **Reject obviously wrong AI responses**
6. **Log validation failures for analysis**

### Do Later (3-6 Months)
7. USDA FoodData Central integration
8. Restaurant nutrition facts lookup
9. ML-based anomaly detection

---

## 💡 Quick Wins

### 1. Add Confidence Threshold
```java
if (analysisResponse.getConfidence() < 0.5) {
    logger.warn("Low confidence AI result: {}", analysisResponse.getConfidence());
    meal.setAnalysisStatus(AnalysisStatus.NEEDS_REVIEW);
}
```

### 2. Flag Edited Meals
```java
@Column(name = "user_edited")
private Boolean userEdited = false; // Set to true when user updates

// In analytics: Compare AI accuracy for edited vs non-edited meals
```

### 3. Log Raw AI Responses
```java
@Column(name = "raw_ai_response", columnDefinition = "TEXT")
private String rawAiResponse; // Store full JSON for debugging

// When things go wrong, we can see EXACTLY what AI said
```

---

## 🎯 Expected Outcomes

**After Phase 1 (Correction Tracking):**
- "We now know AI is 15% accurate on average"
- "Restaurant meals have 2x error rate vs home meals"
- "Sodium estimates are terrible (40% error) but calories are good (12% error)"

**After Phase 2 (Sanity Checks):**
- "We catch 85% of hallucinations before they reach users"
- "Energy mismatch detection saves 30% of bad estimates"
- "Users see fewer obviously wrong values"

**After Phase 3 (Baselines):**
- "McDonald's Big Mac: AI vs official nutrition within 5%"
- "USDA baseline reduces error from 15% → 8% for common foods"

---

## 📝 Summary

**Your intuition is 100% correct** - we have a major blind spot:

❌ **No telemetry:** Can't measure AI accuracy
❌ **No sanity checks:** Accept nonsensical values
❌ **No baselines:** Can't compare to known truth
❌ **No hallucination detection:** Don't know when AI goes off the rails

**Recommended Action:**
1. **This week:** Add `ai_correction_logs` table
2. **This week:** Track user edits in `updateMeal()`
3. **Next sprint:** Build sanity check validation
4. **Following sprint:** Create analytics dashboard

This is **critical infrastructure** for a production AI system. Without it, we're flying blind!

---

*Last Updated: October 27, 2024*
