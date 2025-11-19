# CI/CD Test Quality Gates

**Last Updated:** 2024-11-19
**Phase:** 4 - Continuous Improvement & Excellence

---

## Overview

This document describes the automated test quality gates enforced in our CI/CD pipeline. These gates ensure that:

1. **All tests pass** before code can be merged
2. **Code coverage** meets minimum thresholds
3. **Mutation coverage** (real test quality) meets minimum thresholds
4. **Test quality** doesn't regress over time

## Table of Contents

1. [What Are Quality Gates?](#what-are-quality-gates)
2. [Quality Metrics Enforced](#quality-metrics-enforced)
3. [GitHub Actions Workflows](#github-actions-workflows)
4. [How to Pass Quality Gates](#how-to-pass-quality-gates)
5. [Common Failure Scenarios](#common-failure-scenarios)
6. [Local Development Workflow](#local-development-workflow)
7. [Troubleshooting](#troubleshooting)

---

## What Are Quality Gates?

Quality gates are automated checks that run on every Pull Request to ensure code changes meet quality standards.

### Traditional Approach (Bad)
```
Developer writes code → Creates PR → Tests pass → MERGED ✅
```

**Problem:** Tests might pass but catch no bugs ("Nokkukuthi" tests)

### Our Approach (Good)
```
Developer writes code → Creates PR → Tests pass →
Mutation testing runs → Quality gates check →
If quality sufficient → MERGED ✅
If quality insufficient → BLOCKED ❌
```

**Benefit:** Tests are **proven** to catch bugs before merge

---

## Quality Metrics Enforced

### 1. Test Execution (Basic Gate)

**Requirement:** All tests must pass

**Enforced by:** GitHub Actions `test-quality-gates.yml`

**Tests Run:**
- Unit tests (`*Test.java`)
- Property-based tests (`*PropertyTest.java`)
- Integration tests (`*IntegrationTest.java`)

**Command:**
```bash
./gradlew test
```

**Status:** ✅ Always enforced (since beginning)

---

### 2. Code Coverage (Intermediate Gate)

**Requirement:** Minimum 75% line coverage (Phase 4: increased from 70%)

**Enforced by:** JaCoCo plugin + GitHub Actions

**What it measures:** What percentage of code lines are executed by tests

**Command:**
```bash
./gradlew jacocoTestReport
./gradlew jacocoTestCoverageVerification
```

**View Report:**
```bash
open build/reports/jacoco/test/html/index.html
```

**Status:** ✅ Enforced in CI/CD

**Exclusions:**
- Configuration classes (`*.config.*`)
- DTOs (`*.dto.*`)
- Entities (`*.entity.*`)
- Generated code (`*.generated.*`)

---

### 3. Mutation Coverage (Advanced Gate) ⭐

**Requirement:** Minimum 70% mutation coverage (Phase 4: increased from 65%)

**Enforced by:** Pitest plugin + GitHub Actions

**What it measures:** What percentage of introduced bugs (mutations) are caught by tests

**This is the REAL measure of test quality!**

#### How Mutation Testing Works

1. **Pitest mutates your code:**
   ```java
   // Original:
   if (calories > 0) { ... }

   // Mutated:
   if (calories >= 0) { ... }  // Changed > to >=
   ```

2. **Runs your tests against the mutant:**
   - If tests **FAIL** → Mutation **KILLED** ✅ (Good! Tests caught the bug)
   - If tests **PASS** → Mutation **SURVIVED** ❌ (Bad! Tests missed the bug)

3. **Calculates mutation coverage:**
   ```
   Mutation Coverage = (Killed Mutations / Total Mutations) × 100%
   ```

**Command:**
```bash
./gradlew pitest
```

**View Report:**
```bash
open build/reports/pitest/index.html
```

**Status:** ✅ Enforced in Phase 4 (current)

**Threshold History:**
- Phase 1: 50% (baseline)
- Phase 3: 65% (automated enforcement)
- **Phase 4: 70%** (current - excellence standard)

---

## GitHub Actions Workflows

### Workflow: `test-quality-gates.yml`

**Trigger:** Pull requests to `main` or `develop` branches

**Jobs:**

#### Job 1: `test-quality`

**Steps:**
1. ✅ Checkout code
2. ✅ Set up JDK 17
3. ✅ Run unit tests
4. ✅ Run property-based tests
5. ✅ Run integration tests
6. ✅ Generate JaCoCo coverage report
7. ✅ Verify code coverage threshold (75%)
8. ✅ Run mutation tests with Pitest
9. ✅ Verify mutation coverage threshold (70%)
10. ✅ Run performance benchmarks (JMH)
11. ✅ Track quality metrics over time
12. ✅ Upload test reports as artifacts
13. ✅ Post test quality metrics as PR comment
14. ❌ **FAIL PR** if any quality gate fails

#### Job 2: `regression-check`

**Purpose:** Detect test quality regressions

**Steps:**
1. Run Pitest on PR branch
2. Run Pitest on base branch
3. Compare mutation coverage
4. Warn if coverage decreased

---

## How to Pass Quality Gates

### Step 1: Write Code with Tests

Write your feature/bugfix code along with tests.

### Step 2: Run Tests Locally

```bash
cd backend

# Run all tests
./gradlew test

# Expected output:
# BUILD SUCCESSFUL
# All tests passed ✅
```

### Step 3: Check Code Coverage

```bash
# Generate coverage report
./gradlew jacocoTestReport

# Open report
open build/reports/jacoco/test/html/index.html
```

**Look for:**
- Overall coverage: **≥ 70%** ✅
- New code coverage: **100%** (ideally)

**If coverage is low:**
- Add more unit tests
- Add integration tests for new endpoints
- Test edge cases (null, zero, negative, boundary)

### Step 4: Run Mutation Tests

```bash
# Run mutation testing (this takes a few minutes)
./gradlew pitest
```

**Expected output:**
```
================================================================
- Mutators
================================================================
> org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator
> org.pitest.mutationtest.engine.gregor.mutators.IncrementssMutator
> ... (more mutators)

================================================================
- Statistics
================================================================
>> Generated 150 mutations Killed 105 (70%)
>> Ran 250 tests (1.67 tests per mutation)
```

**Target:** ≥ 70% killed ✅

### Step 5: Review Survived Mutations

```bash
# Open mutation report
open build/reports/pitest/index.html
```

**Navigate through report:**
1. Click on a package (e.g., `com.nutritheous.meal`)
2. Click on a class (e.g., `MealService`)
3. See highlighted mutations:
   - 🟢 **Green** = Killed (good!)
   - 🔴 **Red** = Survived (bad!)

**For each survived mutation:**

Ask yourself: **"Is this mutation important to catch?"**

#### Example 1: Critical Survived Mutation ❌
```java
// Original:
if (calories > 0) {
    return "Valid";
}

// Mutant (SURVIVED):
if (calories >= 0) {  // Changed > to >=
    return "Valid";
}
```

**Why critical:** Zero calories should be invalid (or valid)—behavior differs!

**Fix:** Add test for boundary:
```java
@Test
void testValidation_zeroCalories_shouldBeInvalid() {
    assertFalse(validator.isValid(0));
}
```

#### Example 2: Harmless Survived Mutation ✅
```java
// Original:
logger.info("Processing meal ID: " + mealId);

// Mutant (SURVIVED):
logger.info("Processing meal ID: ");  // Removed concatenation
```

**Why harmless:** Logging doesn't affect business logic

**Fix:** None needed (you can't test log output, and it's not critical)

### Step 6: Improve Test Quality

If mutation coverage < 70%, improve tests:

**Pro Tip:** Use the Phase 4 mutation analysis tool for guided suggestions:
```bash
./scripts/analyze-mutations.sh --verbose
```

#### Strategy 1: Replace Vague Assertions
```java
// ❌ BAD: Vague assertion (mutation-vulnerable)
verify(mealRepository).save(any());

// ✅ GOOD: Specific assertion (mutation-resistant)
verify(mealRepository).save(argThat(meal ->
    meal.getCalories() == 500 &&
    meal.getMealType() == MealType.LUNCH &&
    meal.getDescription().equals("Chicken rice")
));
```

#### Strategy 2: Add Property-Based Tests
```java
// ❌ BAD: Testing one example
@Test
void testPercentError_example() {
    assertEquals(20.0, calculatePercentError(100, 120));
}

// ✅ GOOD: Testing universal property
@Property
void percentError_alwaysPositive_whenUserValueGreater(
    @ForAll @DoubleRange(min = 1, max = 5000) double aiValue,
    @ForAll @DoubleRange(min = 1, max = 5000) double userValue
) {
    Assume.that(userValue > aiValue);

    double percentError = calculatePercentError(aiValue, userValue);

    assertTrue(percentError > 0, "Percent error should be positive");
}
```

#### Strategy 3: Test Boundary Conditions
```java
@Test
void testFiberValidation_exactlyEqualToCarbs_shouldBeValid() {
    // Test boundary: fiber == carbs (edge case)
    assertTrue(validator.isValid(fiber: 10.0, carbs: 10.0));
}

@Test
void testFiberValidation_slightlyExceedsCarbs_shouldBeInvalid() {
    // Test boundary: fiber > carbs (should fail)
    assertFalse(validator.isValid(fiber: 10.1, carbs: 10.0));
}
```

#### Strategy 4: Use Independent Calculations
```java
// ❌ BAD: Mirroring implementation
@Test
void testCalorieCalculation() {
    // This just copies the formula from production code
    int expected = (int) (protein * 4 + fat * 9 + carbs * 4);
    assertEquals(expected, meal.calculateCalories());
}

// ✅ GOOD: Independent calculation
@Test
void testCalorieCalculation() {
    // Atwater factors (from nutritional science, not code)
    int expectedFromProtein = 50 * 4;   // 200 cal
    int expectedFromFat = 20 * 9;       // 180 cal
    int expectedFromCarbs = 100 * 4;    // 400 cal
    int totalExpected = 780;            // Independent sum

    assertEquals(totalExpected, meal.calculateCalories());
}
```

### Step 7: Push to PR

Once mutation coverage ≥ 70%:

```bash
git add .
git commit -m "Add feature X with comprehensive tests (72% mutation coverage)"
git push origin feature/my-feature
```

### Step 8: Monitor CI/CD

1. Go to your PR on GitHub
2. Wait for `Test Quality Gates` workflow to complete
3. Check the PR comment for test quality metrics
4. Review workflow logs if any failures

---

## Common Failure Scenarios

### Scenario 1: Tests Pass Locally, Fail in CI

**Symptoms:**
```
✅ Local: ./gradlew test → PASSED
❌ CI: Test Quality Gates → FAILED
```

**Possible causes:**

1. **Environment differences:**
   - Different Java version
   - Different timezone
   - Different file paths

   **Fix:** Check CI logs for exact error

2. **Non-deterministic tests:**
   - Tests using `new Date()` without mocking
   - Tests using random values without seeds
   - Tests depending on execution order

   **Fix:** Make tests deterministic

3. **Missing test resources:**
   - `application-test.yml` not committed
   - Test fixtures not in git

   **Fix:** Commit test resources

### Scenario 2: Mutation Coverage Below Threshold

**Symptoms:**
```
❌ Mutation coverage (58%) is below threshold (70%)
```

**Fix:**
1. Run `./gradlew pitest` locally
2. **Use the mutation analyzer:** `./scripts/analyze-mutations.sh --verbose`
3. Open `build/reports/pitest/index.html`
4. Find classes with many survived mutations
5. Add tests to kill critical mutations (follow analyzer suggestions)
6. Re-run `./gradlew pitest` to verify
7. Push changes

### Scenario 3: Code Coverage OK, Mutation Coverage Low

**Symptoms:**
```
✅ Line coverage: 85%
❌ Mutation coverage: 45%
```

**This means: Your tests RUN the code but don't VERIFY it!**

**Example:**
```java
@Test
void testUploadMeal() {
    service.uploadMeal(meal);  // Code runs (coverage ✅)

    // No assertions! (mutations survive ❌)
}
```

**Fix:** Add specific assertions
```java
@Test
void testUploadMeal() {
    MealResponse response = service.uploadMeal(meal);

    assertEquals(500, response.getCalories());
    assertEquals(MealType.LUNCH, response.getMealType());

    verify(repo).save(argThat(m ->
        m.getCalories() == 500 &&
        m.getMealType() == MealType.LUNCH
    ));
}
```

### Scenario 4: Pitest Times Out

**Symptoms:**
```
[ERROR] Pitest execution timed out
```

**Causes:**
- Tests are too slow
- Too many mutations generated
- Infinite loops in code

**Fix:**

**Option 1:** Increase timeout in `build.gradle`
```gradle
pitest {
    timeoutConstInMillis = 20000  // Increase from 10000 to 20000
}
```

**Option 2:** Exclude slow tests
```gradle
pitest {
    excludedTestClasses = [
        '*SlowIntegrationTest'
    ]
}
```

**Option 3:** Fix slow tests
- Remove `Thread.sleep()`
- Mock external services
- Use in-memory database

---

## Local Development Workflow

### Daily Development

```bash
# 1. Write code and tests
vim src/main/java/com/nutritheous/meal/MealService.java
vim src/test/java/com/nutritheous/meal/MealServiceTest.java

# 2. Run tests continuously (TDD)
./gradlew test --continuous

# 3. When feature complete, check coverage
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html

# 4. Run mutation tests
./gradlew pitest

# 5. Analyze mutations (Phase 4 tool)
./scripts/analyze-mutations.sh --verbose

# 6. Review and improve
open build/reports/pitest/index.html

# 7. Run benchmarks (for critical paths)
./gradlew jmh

# 8. Commit when mutation coverage ≥ 70%
git add .
git commit -m "Feature X with 72% mutation coverage"
git push
```

### Before Creating PR

```bash
# Full quality check
./gradlew clean test jacocoTestReport pitest

# Check all reports
echo "Code Coverage:"
open build/reports/jacoco/test/html/index.html

echo "Mutation Coverage:"
open build/reports/pitest/index.html

# If all green, create PR
gh pr create --title "Feature X" --body "..."
```

---

## Troubleshooting

### Problem: "Pitest report not found"

**Cause:** Pitest didn't run or crashed

**Debug:**
```bash
./gradlew pitest --info --stacktrace
```

**Check:**
- Do tests pass? (`./gradlew test`)
- Is Java 17 installed?
- Is there enough memory?

---

### Problem: "100% line coverage, 30% mutation coverage"

**Diagnosis:** Tests are "Nokkukuthi" (scarecrows) - they run but catch nothing

**Example:**
```java
@Test
void testService() {
    service.doSomething();  // Runs code ✅
    // No assertions ❌
}
```

**Fix:** Add assertions
```java
@Test
void testService() {
    Result result = service.doSomething();

    assertNotNull(result);
    assertEquals("expected", result.getValue());
    verify(mockDep).wasCalled();
}
```

---

### Problem: "Mutation coverage decreased from 68% to 62%"

**Cause:** New code has weak tests

**Fix:**
1. Find which file has low mutation coverage:
   ```bash
   open build/reports/pitest/index.html
   # Sort by "Mutation Coverage" column
   ```

2. Improve tests for that file

3. Run Pitest again:
   ```bash
   ./gradlew pitest
   ```

---

### Problem: "Cannot push to remote - quality gates failing"

**Quick fix for unblocking (NOT recommended):**

Temporarily reduce threshold in `build.gradle`:
```gradle
pitest {
    mutationThreshold = 50  // Reduced from 65
}
```

**Commit, push, then immediately:**
1. Create follow-up PR to restore threshold to 65
2. Add missing tests to meet threshold
3. Never reduce threshold permanently!

---

## Quality Gate Evolution

### Phase 1 (Completed)
- ✅ Pitest infrastructure
- ✅ Fixed "Nokkukuthi" tests
- ✅ Mutation threshold: 50%

### Phase 2 (Completed)
- ✅ Property-based testing (jqwik)
- ✅ Real HTTP integration tests
- ✅ Expected mutation coverage: 60-70%

### Phase 3 (Completed)
- ✅ CI/CD quality gates
- ✅ Mutation threshold: 65%
- ✅ PR blocking on quality regression
- ✅ Automated test quality reporting

### Phase 4 (Current)
- ✅ Mutation threshold: **70%**
- ✅ Line coverage threshold: **75%**
- ✅ Performance testing with JMH
- ✅ Mutation analysis tool with guided suggestions
- ✅ Quality metrics tracking over time (365-day retention)
- ✅ Continuous improvement framework

---

## Resources

### Documentation
- [Phase 1 Implementation](./PHASE_1_IMPLEMENTATION_SUMMARY.md)
- [Phase 2 Implementation](./PHASE_2_IMPLEMENTATION_SUMMARY.md)
- [Phase 3 Implementation](./PHASE_3_IMPLEMENTATION_SUMMARY.md) (this document's companion)

### Tools
- [Pitest Official Docs](https://pitest.org/)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [jqwik User Guide](https://jqwik.net/docs/current/user-guide.html)

### Recommended Reading
- *Effective Software Testing* by Mauricio Aniche
- *Property-Based Testing* by Fred Hebert
- "Mutation Testing: Better Tests Through Mutation" (Pitest blog)

---

**Questions?** Ask in #engineering Slack channel or create an issue in the repo.

**Feedback?** Submit a PR to improve this documentation!
