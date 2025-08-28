#!/bin/bash
# Validate datasets in evaluator against the new format and show ground-truth mappings

set -e
shopt -s nullglob

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
EVALUATOR_DIR="$PROJECT_ROOT/evaluator"
ROW_CAP_LIMIT="${ROW_CAP_LIMIT:-1000}"
FILE_LIMIT="${FILE_LIMIT:-25}"

usage() {
  cat <<EOF
Usage: $(basename "$0") [--dataset NAME]

Validates one or more datasets in: $EVALUATOR_DIR/datasets/data

Data CSV format (strict):
  - Row 1: Baseline ground truth semantic types per column
  - Row 2: Custom-types ground truth semantic types per column
  - Row 3: Column headers
  - Row 4+: Data rows

Options:
  --dataset NAME   Validate only the dataset directory named NAME under data/
  -h, --help       Show this help
EOF
}

DATASET_FILTER=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dataset)
      shift
      DATASET_FILTER="$1"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo -e "${RED}Unknown argument:${NC} $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

if [ ! -d "$EVALUATOR_DIR/datasets/data" ]; then
  echo -e "${RED}❌ Missing directory:${NC} $EVALUATOR_DIR/datasets/data"
  exit 1
fi

echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Dataset Validation Report${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo ""

TOTAL_DATASETS=0
READY_DATASETS=0
TOTAL_TYPE_FILES=0
DATASETS_WITH_ISSUES=0

# Build dataset list from directories inside data/ (portable for macOS bash)
DATA_ROOT="$EVALUATOR_DIR/datasets/data"

DATASET_DIRS=()
if [ -n "$DATASET_FILTER" ]; then
  FILTERED_DIR="$DATA_ROOT/$DATASET_FILTER"
  if [ -d "$FILTERED_DIR" ]; then
    DATASET_DIRS+=("$FILTERED_DIR")
  else
    echo -e "${RED}❌ Dataset directory not found:${NC} $FILTERED_DIR"
    exit 1
  fi
else
  for d in "$DATA_ROOT"/*/; do
    [ -d "$d" ] || continue
    DATASET_DIRS+=("${d%/}")
  done
  # Sort dataset list lexicographically
  IFS=$'\n' DATASET_DIRS=($(printf '%s\n' "${DATASET_DIRS[@]}" | sort))
  unset IFS
fi

if [ ${#DATASET_DIRS[@]} -eq 0 ]; then
  echo -e "${YELLOW}No dataset directories found inside:${NC} $DATA_ROOT"
  exit 0
fi

for DATASET_PATH in "${DATASET_DIRS[@]}"; do
  DATASET_NAME=$(basename "$DATASET_PATH")
  ((TOTAL_DATASETS++))

  echo -e "${BLUE}Dataset:${NC} $DATASET_NAME"
  echo "────────────────────────"

  DATASET_VALID=true
  CSV_FILES=("$DATASET_PATH"/*.csv)
  if [ ${#CSV_FILES[@]} -eq 0 ]; then
    echo -e "  Data files:     ${RED}❌ None found${NC} (expected at least one .csv)"
    DATASET_VALID=false
  else
    echo -e "  Data files:     ${GREEN}✅${NC} $(ls -1 "$DATASET_PATH"/*.csv 2>/dev/null | wc -l | tr -d ' ') file(s)"
  fi

  # Show per-file stats and ground-truth mappings
  FILES_PROCESSED=0
  for FILE in "$DATASET_PATH"/*.csv; do
    [ -f "$FILE" ] || continue
    if [ "$FILES_PROCESSED" -ge "$FILE_LIMIT" ]; then
      echo -e "  Note: file limit reached (${FILE_LIMIT}); skipping remaining files in dataset"
      break
    fi
    FILES_PROCESSED=$((FILES_PROCESSED + 1))
    FILE_SIZE=$(ls -lh "$FILE" | awk '{print $5}')
    echo -e "  File: $(basename "$FILE")  (${FILE_SIZE})"

    # Use Python csv module for robust parsing and pretty output
    if ! python3 - "$FILE" "$ROW_CAP_LIMIT" <<'PYCODE'
import csv, sys, json
from pathlib import Path
path = Path(sys.argv[1])
try:
    cap = int(sys.argv[2]) if len(sys.argv) > 2 else 1000
except Exception:
    cap = 1000
try:
    with path.open('r', newline='') as f:
        reader = csv.reader(f)
        # Read first three rows explicitly (baseline, custom, headers)
        baseline = next(reader, None)
        custom = next(reader, None)
        headers = next(reader, None)
        if baseline is None or custom is None or headers is None:
            print("    Invalid format: expected at least 3 rows (baseline, custom, headers)")
            found = sum(x is not None for x in [baseline, custom, headers])
            print(f"    Found only {found} row(s)")
            sys.exit(3)
        # Read up to cap data rows without loading the entire file
        data_rows = []
        truncated = False
        for idx, row in enumerate(reader):
            if idx < cap:
                data_rows.append(row)
            else:
                truncated = True
                break
except Exception as e:
    print(f"    Error reading CSV: {e}")
    sys.exit(2)

def safe_get(row, idx):
    return row[idx] if idx < len(row) else ''

col_count = len(headers)
data_count = len(data_rows)
print(f"    Columns: {col_count}")
if truncated:
    print(f"    Data rows: {data_count}+ (capped at {cap})")
else:
    print(f"    Data rows: {data_count}")

# If no data rows in the first cap, flag as error (structure likely incorrect)
if data_count == 0:
    print("    Warning: no data rows present (rows 4+). Verify CSV layout and ground truth rows.")
    sys.exit(4)

# Validate row lengths
issues = []
if len(baseline) != col_count:
    issues.append(f"baseline has {len(baseline)} entries (expected {col_count})")
if len(custom) != col_count:
    issues.append(f"custom has {len(custom)} entries (expected {col_count})")
if issues:
    print("    Warning: " + "; ".join(issues))
    # Mark invalid to force user to fix GT alignment
    sys.exit(6)

# Heuristic header validation: if majority of headers look numeric/symbolic, warn
def looks_invalid_header(s: str) -> bool:
    s = (s or '').strip()
    if not s:
        return True
    # Mostly digits or non-letters
    letters = sum(ch.isalpha() for ch in s)
    nonletters = len(s) - letters
    return letters == 0 or nonletters > letters * 2

invalid_headers = sum(1 for h in headers if looks_invalid_header(h))
if col_count > 0 and invalid_headers / col_count > 0.5:
    print(f"    Warning: {invalid_headers}/{col_count} headers look invalid (mostly numeric/empty). Check header row (row 3).")
    sys.exit(5)

# Display ground-truth mappings in a compact, readable way
print("    Ground truth mappings (baseline vs custom):")
for i, h in enumerate(headers):
    b = safe_get(baseline, i).strip()
    c = safe_get(custom, i).strip()
    # Truncate very long values for readability
    def trunc(s, n=80):
        return (s[:n] + '…') if len(s) > n else s
    h_disp = trunc(h)
    b_disp = trunc(b)
    c_disp = trunc(c)
    print(f"      - {h_disp}: baseline='{b_disp}'  custom='{c_disp}'")
PYCODE
    then
      # Mark dataset invalid if python validation failed
      DATASET_VALID=false
    fi

  done

  # Generated types detection (directory-based preferred, legacy fallback)
  TYPES_DIR="$EVALUATOR_DIR/generated_semantic_types/$DATASET_NAME"
  echo -n "  Generated types:  "
  if [ -d "$TYPES_DIR" ]; then
    COUNT_JSON=$(find "$TYPES_DIR" -type f -name "*.json" | wc -l | tr -d ' ')
    if [ "$COUNT_JSON" -gt 0 ]; then
      echo -e "${GREEN}✅${NC} $COUNT_JSON JSON file(s) in $DATASET_NAME/"
      ((TOTAL_TYPE_FILES+=COUNT_JSON))
    else
      echo -e "${YELLOW}─${NC} Directory present, no JSON files"
    fi
    FOUND_TYPES=true
  else
    # Legacy flat naming fallback
    FOUND_TYPES=false
    for i in {1..6}; do
      for file in "$EVALUATOR_DIR/generated_semantic_types/${DATASET_NAME}_description${i}"*.json; do
        [ -f "$file" ] || continue
        ((TOTAL_TYPE_FILES++))
        FOUND_TYPES=true
        break
      done
    done
    if [ "$FOUND_TYPES" = true ]; then
      echo -e "${GREEN}✅${NC} Legacy description files found"
    else
      echo -e "${YELLOW}─${NC} None found"
    fi
  fi

  # Dataset status line
  echo -n "  Status:          "
  if [ "$DATASET_VALID" = true ]; then
    echo -e "${GREEN}Valid structure${NC}"
    ((READY_DATASETS++))
  else
    echo -e "${RED}Issues detected${NC}"
    ((DATASETS_WITH_ISSUES++))
  fi

  echo ""
done

# Overall summary
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Summary${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo ""
echo "Datasets validated: $TOTAL_DATASETS"
echo "Datasets with valid structure: $READY_DATASETS"
echo "Datasets with issues: $DATASETS_WITH_ISSUES"
echo "Generated type files detected: $TOTAL_TYPE_FILES"
echo "File limit per dataset: $FILE_LIMIT"
echo ""

if [ $READY_DATASETS -gt 0 ]; then
  echo -e "${GREEN}✅ $READY_DATASETS dataset(s) appear structurally valid${NC}"
  echo ""
  echo "Manual checklist:"
  echo "  - Review the printed ground-truth mappings per column"
  echo "  - Confirm baseline vs custom labels align with your expectations"
  echo "  - Ensure generated types (if any) correspond to this dataset"
else
  echo -e "${RED}❌ No structurally valid datasets detected${NC}"
  echo ""
  echo "Please ensure:"
  echo "  1) Each dataset is a directory under evaluator/datasets/data/"
  echo "  2) Each CSV has: baseline row, custom row, headers row, then data rows"
  echo "  3) Generated types (optional) are under evaluator/generated_semantic_types/<dataset>/"
fi