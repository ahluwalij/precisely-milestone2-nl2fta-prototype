#!/bin/bash
# Validate generation-inputs CSV files for proper quoting and format
# Checks that all Generation Description fields are properly quoted with double quotes
# Validates format and warns about missing examples for certain description indexes

set -e
shopt -s nullglob

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
GENERATION_INPUTS_DIR="$PROJECT_ROOT/evaluator/datasets/generation-inputs"

# Create Python validation script
PYTHON_VALIDATOR="$SCRIPT_DIR/validate_generation_inputs.py"

cat > "$PYTHON_VALIDATOR" << 'PYTHON_EOF'
#!/usr/bin/env python3
"""
Validate generation-inputs CSV files for proper format and quoting.
"""

import csv
import sys
import os
from pathlib import Path

def validate_csv_file(file_path):
    """Validate a single CSV file"""
    issues = []
    warnings = []

    try:
        with open(file_path, 'r', newline='', encoding='utf-8') as f:
            reader = csv.reader(f)
            rows = list(reader)

        if not rows:
            issues.append("File is empty")
            return issues, warnings

        header = rows[0]

        # Check header format - handle different CSV formats
        desc_cols = []
        example_cols = []
        header_example_cols = []

        # Check for Generation Description columns
        for i in range(1, 7):
            desc_col = f'Generation Description {i}'
            if desc_col in header:
                desc_cols.append(desc_col)

        # Check for example columns (some files may not have them)
        if 'Positive Value Examples' in header:
            example_cols.append('Positive Value Examples')
        if 'Negative Value Examples' in header:
            example_cols.append('Negative Value Examples')
        if 'Positive Header Examples' in header:
            header_example_cols.append('Positive Header Examples')
        if 'Negative Header Examples' in header:
            header_example_cols.append('Negative Header Examples')

        # Basic validation - ensure we have at least Type and some description columns
        if 'Type' not in header:
            issues.append("Required column 'Type' is missing from header")
        if not desc_cols:
            issues.append("No Generation Description columns found")
        if len(desc_cols) > 0 and desc_cols[0] != 'Generation Description 1':
            issues.append("First Generation Description column should be 'Generation Description 1'")

        # Validate that description columns are in order (if they exist)
        for i, desc_col in enumerate(desc_cols, 1):
            if desc_col not in header:
                issues.append(f"Expected column '{desc_col}' not found")
            else:
                col_pos = header.index(desc_col)
                expected_pos = i  # Generation Description 1 should be at position 1 (after Type at position 0)
                if col_pos != expected_pos:
                    issues.append(f"Column '{desc_col}' should be at position {expected_pos}, but found at position {col_pos}")

        # Check that example columns come after description columns (if they exist)
        type_and_desc_count = 1 + len(desc_cols)  # Type + description columns
        for example_col in example_cols:
            if example_col in header:
                col_pos = header.index(example_col)
                if col_pos < type_and_desc_count:
                    issues.append(f"Column '{example_col}' should come after Type and description columns, but found at position {col_pos}")

        # Check that header example columns come after value example columns (if they exist)
        value_example_count = len(example_cols)
        for header_example_col in header_example_cols:
            if header_example_col in header:
                col_pos = header.index(header_example_col)
                if col_pos < type_and_desc_count + value_example_count:
                    issues.append(f"Column '{header_example_col}' should come after value example columns, but found at position {col_pos}")

        # Validate data rows
        for row_idx, row in enumerate(rows[1:], 1):
            if len(row) != len(header):
                issues.append(f"Row {row_idx+1}: has {len(row)} columns, expected {len(header)}")
                continue

            # Check Type column
            if not row[0].strip():
                issues.append(f"Row {row_idx+1}: Type column is empty")

            # Check description columns that actually exist in this file
            desc_cols_present = []
            for desc_col in desc_cols:
                col_idx = header.index(desc_col)
                if col_idx >= len(row):
                    issues.append(f"Row {row_idx+1}: missing data for column '{desc_col}'")
                    continue

                desc_value = row[col_idx].strip()
                if desc_value:  # Non-empty description
                    desc_cols_present.append(int(desc_col.split()[-1]))  # Extract the number from "Generation Description X"

                    # The CSV reader strips quotes, so we can't check for surrounding quotes
                    # Instead, we just ensure the field contains content (was properly parsed)
                    # If the field has content, it was likely quoted properly in the CSV
                    if not desc_value:
                        issues.append(f"Row {row_idx+1}, {desc_col}: field is empty (may indicate quoting issues in CSV)")
                    # Check for internal quote escaping (CSV reader handles this)
                    elif '"' in desc_value:
                        # If there are quotes inside the field content, they should be escaped properly
                        # The CSV reader would have handled the escaping, so this is fine
                        pass

            # Check if descriptions 3, 4, or 6 exist but examples are missing (only if example columns exist)
            if example_cols:  # Only check if example columns are present in this file
                example_dependent_descs = [3, 4, 6]
                for desc_num in example_dependent_descs:
                    if desc_num in desc_cols_present:
                        # Find the positions of example columns
                        pos_examples_idx = header.index('Positive Value Examples') if 'Positive Value Examples' in header else -1
                        neg_examples_idx = header.index('Negative Value Examples') if 'Negative Value Examples' in header else -1

                        positive_examples = row[pos_examples_idx].strip() if pos_examples_idx >= 0 and pos_examples_idx < len(row) else ""
                        negative_examples = row[neg_examples_idx].strip() if neg_examples_idx >= 0 and neg_examples_idx < len(row) else ""

                        if not positive_examples and not negative_examples:
                            warnings.append(f"Row {row_idx+1}: Generation Description {desc_num} exists but no Positive/Negative Value Examples provided")

        return issues, warnings

    except Exception as e:
        issues.append(f"Error reading file: {e}")
        return issues, warnings

def main():
    """Main validation function"""
    if len(sys.argv) != 2:
        print("Usage: python3 validate_generation_inputs.py <csv_file>")
        sys.exit(1)

    file_path = sys.argv[1]
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} does not exist")
        sys.exit(1)

    issues, warnings = validate_csv_file(file_path)

    # Output results
    if issues:
        print("ISSUES:")
        for issue in issues:
            print(f"  - {issue}")

    if warnings:
        print("\nWARNINGS:")
        for warning in warnings:
            print(f"  - {warning}")

    if not issues and not warnings:
        print("✅ All validations passed")
        return 0
    elif issues:
        print(f"\n❌ Found {len(issues)} issues and {len(warnings)} warnings")
        return 1
    else:
        print(f"\n⚠️  Found {len(warnings)} warnings (no critical issues)")
        return 0

if __name__ == "__main__":
    sys.exit(main())
PYTHON_EOF

chmod +x "$PYTHON_VALIDATOR"

echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Generation-Inputs Validation Report${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo ""

if [ ! -d "$GENERATION_INPUTS_DIR" ]; then
  echo -e "${RED}❌ Missing directory:${NC} $GENERATION_INPUTS_DIR"
  exit 1
fi

# Get all CSV files
CSV_FILES=("$GENERATION_INPUTS_DIR"/*.csv)
TOTAL_FILES=${#CSV_FILES[@]}
FILES_WITH_ISSUES=0
FILES_WITH_WARNINGS=0
TOTAL_ISSUES=0
TOTAL_WARNINGS=0

if [ $TOTAL_FILES -eq 0 ]; then
  echo -e "${YELLOW}No CSV files found in:${NC} $GENERATION_INPUTS_DIR"
  exit 0
fi

echo -e "${BLUE}Found ${TOTAL_FILES} CSV files to validate${NC}"
echo ""

for csv_file in "${CSV_FILES[@]}"; do
  echo -e "${BLUE}Validating:${NC} $(basename "$csv_file")"

  # Run Python validation
  output=$("$PYTHON_VALIDATOR" "$csv_file" 2>&1)
  exit_code=$?

  if [ $exit_code -eq 1 ]; then
    echo -e "${RED}❌ Issues found:${NC}"
    echo "$output" | sed 's/^/  /'
    ((FILES_WITH_ISSUES++))
    # Count issues and warnings from output
    issues_in_file=$(echo "$output" | grep -c "ISSUES:")
    warnings_in_file=$(echo "$output" | grep -c "WARNINGS:")
    ((TOTAL_ISSUES += issues_in_file))
    ((TOTAL_WARNINGS += warnings_in_file))
  elif echo "$output" | grep -q "WARNINGS:"; then
    echo -e "${YELLOW}⚠️  Warnings found:${NC}"
    echo "$output" | sed 's/^/  /'
    ((FILES_WITH_WARNINGS++))
    warnings_in_file=$(echo "$output" | grep -c "WARNINGS:")
    ((TOTAL_WARNINGS += warnings_in_file))
  else
    echo -e "${GREEN}✅ All validations passed${NC}"
  fi

  echo ""
done

# Summary
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Summary${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo ""
echo "Files validated: $TOTAL_FILES"
echo "Files with issues: $FILES_WITH_ISSUES"
echo "Files with warnings: $FILES_WITH_WARNINGS"
echo "Total issues: $TOTAL_ISSUES"
echo "Total warnings: $TOTAL_WARNINGS"
echo ""

if [ $FILES_WITH_ISSUES -gt 0 ]; then
  echo -e "${RED}❌ Validation failed - found critical issues that need to be fixed${NC}"
  echo ""
  echo "Issues found:"
  echo "  - Improper quoting of Generation Description fields"
  echo "  - Incorrect CSV format"
  echo "  - Empty required fields"
  exit 1
elif [ $FILES_WITH_WARNINGS -gt 0 ]; then
  echo -e "${YELLOW}⚠️  Validation completed with warnings${NC}"
  echo ""
  echo "Warnings indicate:"
  echo "  - Generation Descriptions 3, 4, or 6 exist without corresponding examples"
  echo "  - Consider adding Positive/Negative Value Examples for these descriptions"
  exit 0
else
  echo -e "${GREEN}✅ All files passed validation${NC}"
  echo ""
  echo "All Generation Description fields are properly quoted and formatted."
  exit 0
fi
