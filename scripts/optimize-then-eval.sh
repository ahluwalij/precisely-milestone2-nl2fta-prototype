#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$PROJECT_ROOT"
# Auto-load env and force CI default
if [ -f .env ]; then set -a; . ./.env; set +a; fi
export CI="${CI:-true}"
DATASET="all"
DESCRIPTIONS="1"
GEN_FLAGS=(--default --use-saved-credentials)
EVAL_FLAGS=(--default)
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dataset) DATASET="$2"; shift 2;;
    --descriptions) DESCRIPTIONS="$2"; shift 2;;
    --) shift; break;;
    *) echo "Unknown arg: $1" >&2; exit 1;;
  esac
done
echo "[1/2] Generating types: dataset=$DATASET descriptions=$DESCRIPTIONS" >&2
./scripts/generate-semantic-types.sh --dataset "$DATASET" --descriptions "$DESCRIPTIONS" "${GEN_FLAGS[@]}"
echo "[2/2] Evaluating F1: dataset=$DATASET descriptions=$DESCRIPTIONS" >&2
./scripts/run-eval.sh --dataset "$DATASET" --descriptions "$DESCRIPTIONS" "${EVAL_FLAGS[@]}"
