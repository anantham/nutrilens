#!/bin/bash

# ============================================================================
# Mutation Analysis and Improvement Suggestions
# Phase 4: Automated Test Quality Improvement Assistant
# ============================================================================
#
# This script analyzes Pitest mutation reports and provides actionable
# suggestions for improving test quality.
#
# Usage:
#   ./scripts/analyze-mutations.sh [options]
#
# Options:
#   --report PATH    Path to Pitest mutations.xml (default: build/reports/pitest/mutations.xml)
#   --threshold NUM  Minimum mutation coverage threshold (default: 70)
#   --verbose        Show detailed analysis
#   --json           Output as JSON
#
# Example:
#   ./scripts/analyze-mutations.sh --verbose
#   ./scripts/analyze-mutations.sh --threshold 75
#

set -e

# Default configuration
REPORT_PATH="build/reports/pitest/mutations.xml"
THRESHOLD=70
VERBOSE=false
JSON_OUTPUT=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --report)
            REPORT_PATH="$2"
            shift 2
            ;;
        --threshold)
            THRESHOLD="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --json)
            JSON_OUTPUT=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo_header() {
    if [ "$JSON_OUTPUT" = false ]; then
        echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo -e "${CYAN}$1${NC}"
        echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
    fi
}

echo_success() {
    if [ "$JSON_OUTPUT" = false ]; then
        echo -e "${GREEN}✓${NC} $1"
    fi
}

echo_warning() {
    if [ "$JSON_OUTPUT" = false ]; then
        echo -e "${YELLOW}⚠${NC} $1"
    fi
}

echo_error() {
    if [ "$JSON_OUTPUT" = false ]; then
        echo -e "${RED}✗${NC} $1"
    fi
}

# Check if report exists
if [ ! -f "$REPORT_PATH" ]; then
    echo_error "Mutation report not found at: $REPORT_PATH"
    echo "Run './gradlew pitest' first to generate the report"
    exit 1
fi

echo_header "🧬 Mutation Testing Analysis"

# Parse XML report (simplified - in production use proper XML parser)
TOTAL_MUTATIONS=$(grep -c '<mutation' "$REPORT_PATH" || echo "0")
KILLED_MUTATIONS=$(grep -c 'detected="true"' "$REPORT_PATH" || echo "0")
SURVIVED_MUTATIONS=$((TOTAL_MUTATIONS - KILLED_MUTATIONS))

if [ "$TOTAL_MUTATIONS" -eq 0 ]; then
    echo_error "No mutations found in report"
    exit 1
fi

MUTATION_COVERAGE=$((KILLED_MUTATIONS * 100 / TOTAL_MUTATIONS))

# Display summary
echo_header "📊 Mutation Coverage Summary"
echo "Total Mutations:      $TOTAL_MUTATIONS"
echo "Killed:               $KILLED_MUTATIONS (${GREEN}caught by tests${NC})"
echo "Survived:             $SURVIVED_MUTATIONS (${RED}missed by tests${NC})"
echo "Mutation Coverage:    ${MUTATION_COVERAGE}%"
echo "Threshold:            ${THRESHOLD}%"
echo ""

if [ "$MUTATION_COVERAGE" -ge "$THRESHOLD" ]; then
    echo_success "Mutation coverage meets threshold! 🎉"
else
    DEFICIT=$((THRESHOLD - MUTATION_COVERAGE))
    echo_error "Mutation coverage is ${DEFICIT}% below threshold"
    echo ""
    echo "You need to improve test quality to catch $((DEFICIT * TOTAL_MUTATIONS / 100)) more mutations"
fi

# Analyze survived mutations by type
echo_header "🔍 Survived Mutation Analysis"

# Extract mutation types from XML (simplified)
echo "Analyzing mutation patterns..."

# Common mutation categories and improvement suggestions
declare -A SUGGESTIONS=(
    ["ConditionalsBoundaryMutator"]="
    ${YELLOW}Issue:${NC} Boundary conditions not tested (e.g., > changed to >=)

    ${CYAN}Example Problem:${NC}
      if (calories > 0)  // Mutated to: if (calories >= 0)

    ${GREEN}Fix:${NC} Add boundary tests
      @Test
      void testValidation_zeroCalories_shouldBeInvalid() {
          assertFalse(validator.isValid(0));
      }

      @Test
      void testValidation_positiveCalories_shouldBeValid() {
          assertTrue(validator.isValid(1));
      }
    "

    ["NegateConditionalsMutator"]="
    ${YELLOW}Issue:${NC} Conditional logic not properly tested (e.g., == changed to !=)

    ${CYAN}Example Problem:${NC}
      if (status == COMPLETED)  // Mutated to: if (status != COMPLETED)

    ${GREEN}Fix:${NC} Test both branches explicitly
      @Test
      void whenCompleted_shouldProcessMeal() {
          meal.setStatus(COMPLETED);
          assertTrue(service.shouldProcess(meal));
      }

      @Test
      void whenNotCompleted_shouldNotProcessMeal() {
          meal.setStatus(PENDING);
          assertFalse(service.shouldProcess(meal));
      }
    "

    ["MathMutator"]="
    ${YELLOW}Issue:${NC} Mathematical operations not verified (e.g., + changed to -, * to /)

    ${CYAN}Example Problem:${NC}
      calories = protein * 4 + fat * 9  // Mutated: protein * 4 - fat * 9

    ${GREEN}Fix:${NC} Use independent calculations in tests
      @Test
      void testCalorieCalculation() {
          // Independent calculation (not mirroring implementation)
          int expectedCalories = 50*4 + 20*9;  // 200 + 180 = 380
          assertEquals(380, meal.calculateCalories());
      }

      // Better: Property-based test
      @Property
      void caloriesFromFat_cannotBeNegative(
          @ForAll @DoubleRange(min=0, max=200) double fat
      ) {
          assertTrue(calculateCaloriesFromFat(fat) >= 0);
      }
    "

    ["IncrementsMutator"]="
    ${YELLOW}Issue:${NC} Increment/decrement operators not tested (e.g., ++ to --)

    ${CYAN}Example Problem:${NC}
      count++  // Mutated to: count--

    ${GREEN}Fix:${NC} Verify the count explicitly
      @Test
      void testIncrementCount() {
          int initialCount = service.getCount();
          service.incrementCount();
          assertEquals(initialCount + 1, service.getCount());
      }
    "

    ["ReturnValsMutator"]="
    ${YELLOW}Issue:${NC} Return values not verified (e.g., return true → return false)

    ${CYAN}Example Problem:${NC}
      return isValid;  // Mutated to: return !isValid

    ${GREEN}Fix:${NC} Add specific assertion on return value
      @Test
      void testValidation_validInput_returnsTrue() {
          boolean result = validator.validate(validInput);
          assertTrue(result);  // Explicit assertion
      }

      ${RED}BAD:${NC}
      assertDoesNotThrow(() -> validator.validate(validInput));  // Doesn't check return!
    "
)

# Display suggestions for common mutation types
if [ "$VERBOSE" = true ]; then
    echo_header "💡 Improvement Suggestions"

    for mutator in "${!SUGGESTIONS[@]}"; do
        if grep -q "$mutator" "$REPORT_PATH" 2>/dev/null; then
            echo -e "${CYAN}╔═══════════════════════════════════════════════════════════════════════╗${NC}"
            echo -e "${CYAN}║${NC} ${mutator}${CYAN}${NC}"
            echo -e "${CYAN}╚═══════════════════════════════════════════════════════════════════════╝${NC}"
            echo -e "${SUGGESTIONS[$mutator]}"
            echo ""
        fi
    done
fi

# Top classes with most survived mutations
echo_header "📉 Classes with Most Survived Mutations"

# Extract class names with survived mutations (simplified grep)
echo "Analyzing classes..."
# In a real implementation, use proper XML parsing with xmllint or Python

echo ""
echo_warning "Run './gradlew pitest' and open build/reports/pitest/index.html for detailed view"

# Generate action items
echo_header "✅ Action Items"

if [ "$MUTATION_COVERAGE" -lt "$THRESHOLD" ]; then
    echo "1. Run: ./gradlew pitest"
    echo "2. Open: build/reports/pitest/index.html"
    echo "3. Find classes with red highlights (survived mutations)"
    echo "4. For each survived mutation:"
    echo "   - Ask: Is this mutation important to catch?"
    echo "   - If YES: Add test following suggestions above"
    echo "   - If NO: Consider excluding with @Generated or pitest filter"
    echo "5. Re-run: ./gradlew pitest"
    echo "6. Repeat until coverage >= ${THRESHOLD}%"
else
    echo_success "Mutation coverage is excellent! Consider:"
    echo "  • Increase threshold to $((THRESHOLD + 5))% for continuous improvement"
    echo "  • Add property-based tests for mathematical invariants"
    echo "  • Review remaining survived mutations for edge cases"
fi

# JSON output
if [ "$JSON_OUTPUT" = true ]; then
    cat <<EOF
{
    "total_mutations": $TOTAL_MUTATIONS,
    "killed_mutations": $KILLED_MUTATIONS,
    "survived_mutations": $SURVIVED_MUTATIONS,
    "mutation_coverage": $MUTATION_COVERAGE,
    "threshold": $THRESHOLD,
    "passed": $([ "$MUTATION_COVERAGE" -ge "$THRESHOLD" ] && echo "true" || echo "false"),
    "deficit": $((THRESHOLD - MUTATION_COVERAGE))
}
EOF
fi

# Exit code based on threshold
if [ "$MUTATION_COVERAGE" -ge "$THRESHOLD" ]; then
    exit 0
else
    exit 1
fi
