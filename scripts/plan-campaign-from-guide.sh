#!/usr/bin/env bash

set -euo pipefail

# Usage:
#   scripts/plan-campaign-from-guide.sh --base-url http://localhost:8081 --agent-count 4

BASE_URL="http://localhost:8081"
COUNT=4

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2;;
    --agent-count|--count) COUNT="$2"; shift 2;;
    *) echo "Unknown arg: $1" >&2; exit 1;;
  esac
done

command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 1; }

echo "Fetching guide..."
GUIDE=$(curl -sS "$BASE_URL/api/guide")

if [[ -z "$GUIDE" || "$GUIDE" == null ]]; then
  echo "Guide not available" >&2
  exit 1
fi

DATASETS=$(echo "$GUIDE" | jq -r '.datasets[]')
PRIORITIES=$(echo "$GUIDE" | jq -r '.priorityDescriptions[]')

echo "Building campaign payload..."
OPTIMIZATIONS=()
EVALUATIONS=()

for DS in $DATASETS; do
  for DESC in $PRIORITIES; do
    # Minimal placeholder optimization request; agents will enrich examples per guide
    OPTIMIZATIONS+=("{\"description\":\"description_$DESC\",\"datasetCsv\":\"$DS\",\"autoLearn\":true,\"persist\":true}")
    EVALUATIONS+=("{\"datasetCsv\":\"$DS\"}")
  done
done

OPTS=$(printf '%s,' "${OPTIMIZATIONS[@]}")
OPTS=[${OPTS%,}]
EVALS=$(printf '%s,' "${EVALUATIONS[@]}")
EVALS=[${EVALS%,}]

CAMPAIGN_PAYLOAD=$(cat <<JSON
{"campaignId":"auto-$(date +%s)","optimizations":$OPTS,"evaluations":$EVALS,"maxParallelAgents":$COUNT}
JSON
)

echo "Submitting campaign..."
curl -sS -X POST "$BASE_URL/api/agents/submit-campaign" -H 'Content-Type: application/json' -d "$CAMPAIGN_PAYLOAD"
echo
echo "Done. Use scripts/spawn-agents.sh to execute with $COUNT agents."


