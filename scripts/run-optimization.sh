#!/usr/bin/env bash

set -euo pipefail

# Usage:
#   scripts/run-optimization.sh \
#     --dataset banking/banking.csv \
#     --type "CUSTOM.MY_TYPE" \
#     --description "My type description" \
#     --pos-values pos_values.txt \
#     --neg-values neg_values.txt \
#     --pos-headers pos_headers.txt \
#     --neg-headers neg_headers.txt \
#     --columns columns.txt \
#     --ground-truth gt_pairs.txt \
#     --persist true \
#     --auto-learn true \
#     --base-url http://localhost:8081

BASE_URL="http://localhost:8081"
DATASET=""
TYPE_NAME=""
DESCRIPTION=""
POS_VALUES=""
NEG_VALUES=""
POS_HEADERS=""
NEG_HEADERS=""
COLUMNS_FILE=""
GT_FILE=""
PERSIST="false"
AUTO_LEARN="true"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dataset) DATASET="$2"; shift 2;;
    --type) TYPE_NAME="$2"; shift 2;;
    --description) DESCRIPTION="$2"; shift 2;;
    --pos-values) POS_VALUES="$2"; shift 2;;
    --neg-values) NEG_VALUES="$2"; shift 2;;
    --pos-headers) POS_HEADERS="$2"; shift 2;;
    --neg-headers) NEG_HEADERS="$2"; shift 2;;
    --columns) COLUMNS_FILE="$2"; shift 2;;
    --ground-truth) GT_FILE="$2"; shift 2;;
    --persist) PERSIST="$2"; shift 2;;
    --auto-learn) AUTO_LEARN="$2"; shift 2;;
    --base-url) BASE_URL="$2"; shift 2;;
    *) echo "Unknown arg: $1" >&2; exit 1;;
  esac
done

if [[ -z "$DATASET" || -z "$DESCRIPTION" ]]; then
  echo "--dataset and --description are required" >&2
  exit 1
fi

read_list() {
  local file="$1"
  if [[ -z "$file" || ! -f "$file" ]]; then echo "[]"; return; fi
  jq -Rs 'split("\n") | map(select(length>0))' < "$file"
}

POS_VALUES_JSON=$(read_list "$POS_VALUES")
NEG_VALUES_JSON=$(read_list "$NEG_VALUES")
POS_HEADERS_JSON=$(read_list "$POS_HEADERS")
NEG_HEADERS_JSON=$(read_list "$NEG_HEADERS")
COLUMNS_JSON=$(read_list "$COLUMNS_FILE")
GT_JSON=$(read_list "$GT_FILE")

REQ=$(jq -n \
  --arg dataset "$DATASET" \
  --arg type "$TYPE_NAME" \
  --arg desc "$DESCRIPTION" \
  --argjson posVals "$POS_VALUES_JSON" \
  --argjson negVals "$NEG_VALUES_JSON" \
  --argjson posHdrs "$POS_HEADERS_JSON" \
  --argjson negHdrs "$NEG_HEADERS_JSON" \
  --argjson cols "$COLUMNS_JSON" \
  --argjson gt "$GT_JSON" \
  --arg persist "$PERSIST" \
  --arg auto "$AUTO_LEARN" \
  '{
    optimization: {
      description: $desc,
      typeName: ($type | select(length>0)),
      positiveValues: $posVals,
      negativeValues: $negVals,
      positiveHeaders: $posHdrs,
      negativeHeaders: $negHdrs,
      datasetCsv: $dataset,
      persist: ($persist == "true"),
      autoLearn: ($auto == "true"),
      finiteThreshold: 92,
      regexThreshold: 96,
      topKUnmatched: 10
    },
    evaluation: {
      datasetCsv: $dataset,
      columns: $cols,
      groundTruthPairs: $gt
    }
  }')

echo "Submitting optimize-and-eval to $BASE_URL ..." >&2
curl -sS -X POST "$BASE_URL/api/optimization/optimize-and-eval" \
  -H 'Content-Type: application/json' \
  -d "$REQ" | jq .


