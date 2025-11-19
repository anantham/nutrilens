## Description

<!-- Provide a brief description of the changes in this PR -->

## Type of Change

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Refactoring (no functional changes, code improvement)
- [ ] Documentation update
- [ ] Test improvements

## Test Quality Checklist

<!-- ⚠️ IMPORTANT: Test quality is enforced by CI/CD -->
<!-- The PR will be blocked if mutation coverage falls below 65% -->

### Tests Added/Updated
- [ ] I have added/updated tests to cover my changes
- [ ] All tests pass locally (`./gradlew test`)
- [ ] Property-based tests added for mathematical invariants (if applicable)
- [ ] Integration tests added for new API endpoints (if applicable)

### Test Quality Standards
- [ ] Tests use **specific assertions**, not vague matchers like `any()`
  - ❌ `verify(repo).save(any())`
  - ✅ `verify(repo).save(argThat(m -> m.getCalories() == 500))`

- [ ] Tests use **independent calculations**, not mirroring implementation
  - ❌ Copying formula from production code
  - ✅ Calculating expected value independently

- [ ] Tests verify **actual behavior**, not just that code runs
  - ❌ `assertDoesNotThrow(() -> service.method())`
  - ✅ `assertEquals(expectedResult, service.method())`

- [ ] Integration tests verify **database state**, not just mocks
  - ❌ `verify(repo).save(any())`
  - ✅ `assertEquals(1, repo.findAll().size())`

### Mutation Testing
- [ ] I have run mutation tests locally: `./gradlew pitest`
- [ ] Mutation coverage meets or exceeds 65% threshold
- [ ] I have reviewed survived mutations and understand why they survived
- [ ] I have added tests to kill critical survived mutations

**Current Mutation Coverage:** __%  <!-- Run ./gradlew pitest to get this -->

## How to Test

<!-- Describe how reviewers can test your changes -->

1.
2.
3.

## Edge Cases Considered

<!-- List edge cases you've tested -->

- [ ] Zero/null values
- [ ] Negative values
- [ ] Boundary conditions (min/max)
- [ ] Empty collections
- [ ] Concurrent access (if applicable)

## Documentation

- [ ] Code is self-documenting with clear variable/method names
- [ ] Complex logic has explanatory comments
- [ ] API documentation updated (if applicable)
- [ ] README updated (if applicable)

## Performance Impact

- [ ] No performance regression (or performance improvement described)
- [ ] Database queries are optimized (N+1 queries avoided)
- [ ] External API calls are minimized/cached

## Security Considerations

- [ ] No sensitive data logged
- [ ] Input validation added
- [ ] No SQL injection vulnerabilities
- [ ] No XSS vulnerabilities
- [ ] Authentication/authorization checked

## Breaking Changes

<!-- If this PR introduces breaking changes, describe them here -->

N/A

## Related Issues

<!-- Link related issues here -->

Closes #

## Screenshots (if applicable)

<!-- Add screenshots for UI changes -->

---

## For Reviewers

### Test Quality Review

When reviewing, please check:

1. **Test Coverage**: Are all new code paths tested?
2. **Test Strength**: Do tests use specific assertions?
3. **Edge Cases**: Are boundary conditions tested?
4. **Property Tests**: Are mathematical invariants tested with property-based tests?
5. **Integration Tests**: Do new endpoints have full HTTP → DB integration tests?
6. **Mutation Coverage**: Check CI report - is mutation coverage ≥ 65%?

### Helpful Commands

```bash
# Run all tests
./gradlew test

# Run mutation tests (check actual test quality)
./gradlew pitest

# View mutation report
open backend/build/reports/pitest/index.html

# Run specific test
./gradlew test --tests ClassName.testName

# Run tests with coverage
./gradlew test jacocoTestReport
```

---

<sub>💡 **Tip:** Mutation coverage is the real measure of test quality. It tells you what percentage of bugs your tests actually catch, not just what code they run.</sub>
