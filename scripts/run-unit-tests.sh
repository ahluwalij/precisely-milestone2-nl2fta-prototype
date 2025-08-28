#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo ""
echo "🔍 NL2FTA Code Coverage Analysis"
echo "================================"
echo ""

# Backend Coverage
echo -e "${BLUE}📊 Running backend tests with coverage...${NC}"
cd backend

# Ensure Gradle wrapper is available (works in CI and client envs)
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo -e "${YELLOW}⚠️  gradle-wrapper.jar missing. Bootstrapping wrapper...${NC}"
    if command -v gradle >/dev/null 2>&1; then
        gradle wrapper || true
    elif command -v sudo >/dev/null 2>&1 && command -v apt-get >/dev/null 2>&1; then
        sudo apt-get update -y >/dev/null 2>&1 || true
        sudo apt-get install -y gradle >/dev/null 2>&1 || true
        if command -v gradle >/dev/null 2>&1; then
            gradle wrapper || true
        fi
    fi
fi

# Run tests and capture exit code (continue on test failures to generate coverage)
./gradlew clean test jacocoTestReport --no-daemon --quiet --continue -PFTA_CUSTOM_TYPES_FILE=build/test-custom-semantic-types.json
BACKEND_TEST_EXIT_CODE=$?

if [ $BACKEND_TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Backend tests completed successfully${NC}"
else
    echo -e "${GREEN}⚠️  Backend tests completed with failures (exit code: $BACKEND_TEST_EXIT_CODE)${NC}"
    echo -e "${GREEN}   Continuing with coverage report generation...${NC}"
fi

# Parse coverage from XML regardless of test results
if [ -f "build/reports/jacoco/test/jacocoTestReport.xml" ]; then
        # Extract the final 6 project-level counters (the very last ones in the XML)
        FINAL_COUNTERS=$(grep -o '<counter type="[^"]*" missed="[0-9]*" covered="[0-9]*"/>' build/reports/jacoco/test/jacocoTestReport.xml | tail -6)
        
        # Extract LINE coverage from the final counters
        LINES_COVERED=$(echo "$FINAL_COUNTERS" | grep 'type="LINE"' | grep -o 'covered="[0-9]*"' | grep -o '[0-9]*')
        LINES_MISSED=$(echo "$FINAL_COUNTERS" | grep 'type="LINE"' | grep -o 'missed="[0-9]*"' | grep -o '[0-9]*')
        
        # Calculate backend line coverage percentage
        if [ -n "$LINES_COVERED" ] && [ -n "$LINES_MISSED" ]; then
            TOTAL_LINES=$((LINES_COVERED + LINES_MISSED))
            if [ $TOTAL_LINES -gt 0 ]; then
                BACKEND_LINE_PERCENT=$(echo "scale=2; $LINES_COVERED * 100 / $TOTAL_LINES" | bc)
            fi
        fi
else
    echo "  ❌ No coverage report found"
fi

cd ..

# Ensure the real custom types file wasn't polluted by tests
if [ -f "backend/config/custom-semantic-types.json" ]; then
  # Replace with an empty JSON array if TEST.TYPE or any test artifact leaked in
  if grep -q 'TEST.TYPE\|TEST_TYPE' backend/config/custom-semantic-types.json; then
    echo "[]" > backend/config/custom-semantic-types.json
  fi
fi
echo ""

# Frontend Coverage  
echo -e "${BLUE}📊 Running frontend tests with coverage...${NC}"
cd frontend

# Check if node_modules exists, if not run npm install
if [ ! -d "node_modules" ]; then
    echo -e "${YELLOW}📦 Installing frontend dependencies...${NC}"
    npm install --quiet
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Failed to install frontend dependencies${NC}"
        cd ..
        exit 1
    fi
fi

# Run frontend tests and capture output
FRONTEND_OUTPUT=$(npm run test:coverage 2>&1)
FRONTEND_TEST_EXIT_CODE=$?

# Display the npm output
echo "$FRONTEND_OUTPUT"

# Extract line coverage from the output
FRONTEND_LINE_COVERAGE=$(echo "$FRONTEND_OUTPUT" | grep -E "Lines\s*:\s*[0-9.]+%" | tail -1 | grep -oE "[0-9.]+%" | head -1)

if [ $FRONTEND_TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Frontend tests completed successfully${NC}"
else
    echo -e "${GREEN}⚠️  Frontend tests completed with failures (exit code: $FRONTEND_TEST_EXIT_CODE)${NC}"
    echo -e "${GREEN}   Continuing with coverage report generation...${NC}"
fi

cd ..
echo ""

# Combine coverage reports
echo -e "${BLUE}📊 Combining coverage reports...${NC}"
mkdir -p coverage

if [ -d "backend/build/reports/jacoco/test/html" ]; then
    cp -r backend/build/reports/jacoco/test/html coverage/backend
fi

if [ -d "frontend/coverage/frontend" ]; then
    cp -r frontend/coverage/frontend coverage/frontend
elif [ -d "frontend/coverage" ]; then
    cp -r frontend/coverage coverage/frontend
fi

echo -e "${GREEN}✅ Combined coverage reports available in: coverage/${NC}"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 CODE COVERAGE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
if [ -n "$BACKEND_LINE_PERCENT" ]; then
    echo "  Backend:  ${BACKEND_LINE_PERCENT}%"
fi
if [ -n "$FRONTEND_LINE_COVERAGE" ]; then
    echo "  Frontend: ${FRONTEND_LINE_COVERAGE}"
fi
echo ""
echo "📂 Detailed Reports:"
echo "  • Backend: backend/build/reports/jacoco/test/html/index.html"
echo "  • Frontend: coverage/frontend/index.html"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}✅ Coverage analysis completed!${NC}" 

# Fail CI if any unit test suite failed
if [ -n "$BACKEND_TEST_EXIT_CODE" ] && [ "$BACKEND_TEST_EXIT_CODE" -ne 0 ]; then
    echo -e "${RED}❌ Backend unit tests failed (exit code: $BACKEND_TEST_EXIT_CODE)${NC}"
    exit 1
fi

if [ -n "$FRONTEND_TEST_EXIT_CODE" ] && [ "$FRONTEND_TEST_EXIT_CODE" -ne 0 ]; then
    echo -e "${RED}❌ Frontend unit tests failed (exit code: $FRONTEND_TEST_EXIT_CODE)${NC}"
    exit 1
fi

exit 0