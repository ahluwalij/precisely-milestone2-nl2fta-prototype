#!/usr/bin/env bash

set -euo pipefail

# Usage:
#   scripts/spawn-agents.sh --count 5 --base-url http://localhost:8081

COUNT=3
BASE_URL="http://localhost:8081"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --count) COUNT="$2"; shift 2;;
    --base-url) BASE_URL="$2"; shift 2;;
    *) echo "Unknown arg: $1" >&2; exit 1;;
  esac
done

echo "Registering $COUNT agents..."
AGENTS=()
for i in $(seq 1 "$COUNT"); do
  id=$(curl -sS -X POST "$BASE_URL/api/agents/register?name=agent_$i" -H 'Content-Type: application/json' -d '[]' | jq -r .agentId)
  echo "agent_$i -> $id"
  AGENTS+=("$id")
done

echo "Polling queue and executing tasks... (Ctrl+C to stop)"
while true; do
  progressed=false
  for id in "${AGENTS[@]}"; do
    task=$(curl -sS "$BASE_URL/api/agents/next-task?agentId=$id")
    if [[ "$task" != "null" ]]; then
      progressed=true
      echo "[$(date +%H:%M:%S)] $id got task"
      curl -sS -X POST "$BASE_URL/api/agents/execute" -H 'Content-Type: application/json' -d "$task" | jq '.taskId,.deltaF1,.persisted'
    fi
  done
  curl -sS "$BASE_URL/api/agents/scoreboard" | jq '.deltaF1Sum,.queueDepth,.tasks' | xargs echo "scoreboard:" || true
  if [[ "$progressed" = false ]]; then sleep 2; fi
done


