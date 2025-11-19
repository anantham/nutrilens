#!/bin/bash

# ============================================================================
# Test Quality Metrics Tracker
# Phase 4: Continuous Improvement Tracking
# ============================================================================
#
# This script tracks mutation coverage and other test quality metrics over time.
# It appends metrics to a history file for trend analysis.
#
# Usage:
#   ./scripts/track-quality-metrics.sh
#
# Output:
#   Appends to: backend/quality-metrics-history.csv
#
# Integration with CI/CD:
#   Run this after ./gradlew pitest in CI to track trends
#
# Visualization:
#   Import quality-metrics-history.csv into Google Sheets, Excel, or Grafana
#

set -e

# Configuration
HISTORY_FILE="quality-metrics-history.csv"
PITEST_REPORT="build/reports/pitest/mutations.xml"
JACOCO_REPORT="build/reports/jacoco/test/jacocoTestReport.xml"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}Test Quality Metrics Tracker${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════${NC}\n"

# Get current timestamp
TIMESTAMP=$(date -u +"%Y-%m-%d %H:%M:%S")
DATE=$(date -u +"%Y-%m-%d")
COMMIT_HASH=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

# Extract mutation coverage
if [ -f "$PITEST_REPORT" ]; then
    TOTAL_MUTATIONS=$(grep -c '<mutation' "$PITEST_REPORT" || echo "0")
    KILLED_MUTATIONS=$(grep -c 'detected="true"' "$PITEST_REPORT" || echo "0")

    if [ "$TOTAL_MUTATIONS" -gt 0 ]; then
        MUTATION_COVERAGE=$((KILLED_MUTATIONS * 100 / TOTAL_MUTATIONS))
    else
        MUTATION_COVERAGE=0
    fi

    echo -e "${GREEN}✓${NC} Mutation Coverage: ${MUTATION_COVERAGE}% (${KILLED_MUTATIONS}/${TOTAL_MUTATIONS})"
else
    MUTATION_COVERAGE="N/A"
    TOTAL_MUTATIONS="N/A"
    KILLED_MUTATIONS="N/A"
    echo -e "${YELLOW}⚠${NC} Pitest report not found: $PITEST_REPORT"
fi

# Extract line coverage
if [ -f "$JACOCO_REPORT" ]; then
    # Parse JaCoCo XML for line coverage (simplified - would need proper XML parsing)
    LINE_COVERAGE=$(grep -oP 'type="LINE".*?covered="\K[0-9]+' "$JACOCO_REPORT" | head -1 || echo "0")
    LINE_MISSED=$(grep -oP 'type="LINE".*?missed="\K[0-9]+' "$JACOCO_REPORT" | head -1 || echo "0")

    if [ "$LINE_COVERAGE" != "0" ] && [ "$LINE_MISSED" != "0" ]; then
        LINE_TOTAL=$((LINE_COVERAGE + LINE_MISSED))
        LINE_COVERAGE_PCT=$((LINE_COVERAGE * 100 / LINE_TOTAL))
    else
        LINE_COVERAGE_PCT="N/A"
    fi

    echo -e "${GREEN}✓${NC} Line Coverage: ${LINE_COVERAGE_PCT}%"
else
    LINE_COVERAGE_PCT="N/A"
    echo -e "${YELLOW}⚠${NC} JaCoCo report not found: $JACOCO_REPORT"
fi

# Count test files
UNIT_TESTS=$(find src/test/java -name '*Test.java' -not -name '*IntegrationTest.java' -not -name '*PropertyTest.java' 2>/dev/null | wc -l)
INTEGRATION_TESTS=$(find src/test/java -name '*IntegrationTest.java' 2>/dev/null | wc -l)
PROPERTY_TESTS=$(find src/test/java -name '*PropertyTest.java' 2>/dev/null | wc -l)
TOTAL_TESTS=$((UNIT_TESTS + INTEGRATION_TESTS + PROPERTY_TESTS))

echo -e "${GREEN}✓${NC} Total Test Classes: ${TOTAL_TESTS} (Unit: ${UNIT_TESTS}, Integration: ${INTEGRATION_TESTS}, Property: ${PROPERTY_TESTS})"

# Create CSV header if file doesn't exist
if [ ! -f "$HISTORY_FILE" ]; then
    echo "timestamp,date,commit,branch,mutation_coverage,line_coverage,total_mutations,killed_mutations,total_test_classes,unit_tests,integration_tests,property_tests" > "$HISTORY_FILE"
    echo -e "${GREEN}✓${NC} Created history file: $HISTORY_FILE"
fi

# Append metrics
echo "$TIMESTAMP,$DATE,$COMMIT_HASH,$BRANCH,$MUTATION_COVERAGE,$LINE_COVERAGE_PCT,$TOTAL_MUTATIONS,$KILLED_MUTATIONS,$TOTAL_TESTS,$UNIT_TESTS,$INTEGRATION_TESTS,$PROPERTY_TESTS" >> "$HISTORY_FILE"

echo -e "\n${GREEN}✓${NC} Metrics recorded to: $HISTORY_FILE"

# Show recent trend (last 5 entries)
echo -e "\n${CYAN}═══════════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}Recent Quality Trend (Last 5 Entries)${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════${NC}\n"

if [ -f "$HISTORY_FILE" ]; then
    echo "Date       | Commit  | Mutation % | Line % | Tests"
    echo "-----------|---------|------------|--------|-------"
    tail -5 "$HISTORY_FILE" | while IFS=',' read -r timestamp date commit branch mut_cov line_cov total_mut killed_mut total_test unit integ prop; do
        # Skip header
        if [ "$timestamp" != "timestamp" ]; then
            printf "%-10s | %-7s | %-10s | %-6s | %-5s\n" "$date" "$commit" "$mut_cov" "$line_cov" "$total_test"
        fi
    done
fi

# Calculate improvement trend
if [ -f "$HISTORY_FILE" ] && [ "$(wc -l < "$HISTORY_FILE")" -gt 2 ]; then
    PREVIOUS_MUTATION=$(tail -2 "$HISTORY_FILE" | head -1 | cut -d',' -f5)

    if [ "$MUTATION_COVERAGE" != "N/A" ] && [ "$PREVIOUS_MUTATION" != "N/A" ] && [ "$PREVIOUS_MUTATION" != "mutation_coverage" ]; then
        DIFF=$((MUTATION_COVERAGE - PREVIOUS_MUTATION))

        if [ "$DIFF" -gt 0 ]; then
            echo -e "\n${GREEN}📈 Mutation coverage improved by +${DIFF}%${NC}"
        elif [ "$DIFF" -lt 0 ]; then
            echo -e "\n${YELLOW}📉 Mutation coverage decreased by ${DIFF}%${NC}"
        else
            echo -e "\n${CYAN}→ Mutation coverage unchanged${NC}"
        fi
    fi
fi

echo -e "\n${CYAN}═══════════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}Visualization Tips:${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════${NC}\n"
echo "1. Import $HISTORY_FILE into Google Sheets or Excel"
echo "2. Create line chart with 'date' on X-axis"
echo "3. Plot 'mutation_coverage' and 'line_coverage' on Y-axis"
echo "4. Track trend over sprints/releases"
echo ""
echo "Example visualization tools:"
echo "  • Google Sheets (manual import)"
echo "  • Grafana (automated dashboard)"
echo "  • Python matplotlib (scripts/visualize-trends.py)"
echo ""
