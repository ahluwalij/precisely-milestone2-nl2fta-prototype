#!/bin/bash
# NL2FTA Semantic Type Generator (LLM-backed)
# Requires valid AWS credentials. Starts a separate backend on a non-conflicting port
# and generates semantic types via the backend API, producing timestamped JSON files under
# evaluator/generated_semantic_types/ in the same format used by the evaluator.

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
EVALUATOR_DIR="$PROJECT_ROOT/evaluator"
GEN_DIR="$EVALUATOR_DIR/generated_semantic_types"
VENV_DIR="$PROJECT_ROOT/.venv-nl2fta"
VENV_PY="$VENV_DIR/bin/python"
VENV_PIP="$VENV_DIR/bin/pip"

# Dedicated backend for generator to avoid conflicts with dev (8081) and eval (8082)
GEN_BACKEND_PORT=${GEN_BACKEND_PORT:-8083}
COMPOSE_PROJECT=${COMPOSE_PROJECT:-nl2fta_generator}

RUN_TS="$(date +%Y%m%d_%H%M%S)"
RUN_DIR="$EVALUATOR_DIR/logs/$RUN_TS"
mkdir -p "$RUN_DIR" "$GEN_DIR"
LOG_FILE="$RUN_DIR/generate-types.log"

# Stream all stdout/stderr to the per-run log as well as console
exec > >(tee -a "$LOG_FILE") 2>&1

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Defaults
DATASET="all"
DESCRIPTIONS=""  # if empty, Python will auto-detect from inputs header
DATA_DIR_OVERRIDE=""
FILES_CSV=""
AWS_REGION="${AWS_REGION:-us-east-1}"

# JAVA tuning for the short-lived backend
GEN_JAVA_OPTS="-XX:+UseContainerSupport -Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Determine Docker Compose command (prefer v2)

# Function to install/update chainctl
install_chainctl() {
    echo -e "${YELLOW}Installing chainctl...${NC}"

    # Platform-agnostic curl download per Chainguard docs
    if ! command -v curl >/dev/null 2>&1; then
        echo -e "${RED}❌ curl is required to install chainctl${NC}"; exit 1
    fi
    BIN_OS=$(uname -s | tr '[:upper:]' '[:lower:]')
    BIN_ARCH=$(uname -m | sed 's/aarch64/arm64/')
    TMP_CHAINCTL=$(mktemp -t chainctl.XXXXXX)
    if ! curl -fsSL -o "$TMP_CHAINCTL" "https://dl.enforce.dev/chainctl/latest/chainctl_${BIN_OS}_${BIN_ARCH}"; then
        echo -e "${RED}❌ Failed to download chainctl binary${NC}"
        rm -f "$TMP_CHAINCTL"
        exit 1
    fi
    chmod 0755 "$TMP_CHAINCTL"
    if command -v sudo >/dev/null 2>&1; then
        sudo install -o $UID -g $(id -g) -m 0755 "$TMP_CHAINCTL" /usr/local/bin/chainctl >/dev/null 2>&1 || true
    fi
    if ! command -v chainctl >/dev/null 2>&1; then
        mkdir -p "$HOME/.local/bin"
        install -m 0755 "$TMP_CHAINCTL" "$HOME/.local/bin/chainctl"
        if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
            export PATH="$HOME/.local/bin:$PATH"
            echo -e "${YELLOW}Added ~/.local/bin to PATH for this session${NC}"
        fi
    fi
    rm -f "$TMP_CHAINCTL"
    echo -e "${GREEN}✅ chainctl installed${NC}"
}

# Function to check and setup Chainguard authentication (headless/non-interactive)
setup_chainguard() {
    local use_default="${1:-false}"
    echo -e "${BLUE}Setting up Chainguard authentication...${NC}"

    # Check if we're in CI environment
    if [[ "${CI:-false}" == "true" ]] || [[ -n "${GITHUB_ACTIONS:-}" ]]; then
        echo -e "${YELLOW}CI environment detected. Skipping Chainguard authentication setup.${NC}"
        echo -e "${YELLOW}Docker images will be pulled without Chainguard authentication.${NC}"

        # In CI, just ensure chainctl is available but don't try to authenticate
        if ! command -v chainctl &> /dev/null; then
            install_chainctl
        fi

        # Try to create a basic Docker config for Chainguard images
        mkdir -p ~/.docker
        if [ ! -f ~/.docker/config.json ]; then
            cat > ~/.docker/config.json << 'EOF'
{
  "auths": {},
  "credHelpers": {}
}
EOF
        fi

        echo -e "${GREEN}✅ Chainguard setup skipped for CI environment${NC}"
        return 0
    fi

    # Enforce headless mode universally
    export CHAINCTL_HEADLESS=1
    export NO_BROWSER=1
    export BROWSER=""

    # Ensure chainctl is present
    if ! command -v chainctl &> /dev/null; then
        install_chainctl
    else
        echo -e "${GREEN}✅ chainctl is already installed${NC}"
    fi

    # Always run headless device login and (re)configure docker helper to avoid hangs in status
    echo -e "${BLUE}Non-interactive Chainguard setup (headless)...${NC}"
    mkdir -p ~/.docker
    echo "If authentication is required, a device login URL and code will print below."

    # Use appropriate Chainguard authentication based on default mode
    if [ "$use_default" = true ]; then
        chainctl auth login --headless || true
        # Configure Docker credential helper (retry with sudo on permission errors)
        CFG_STATUS=0
        chainctl auth configure-docker --headless || CFG_STATUS=$?
        if [ $CFG_STATUS -ne 0 ] || ! command -v docker-credential-cgr >/dev/null 2>&1; then
            if command -v sudo >/dev/null 2>&1; then
                echo -e "${YELLOW}Attempting to fix credential helper with elevated privileges...${NC}"
                sudo chainctl auth configure-docker --headless || true
            fi
        fi
    else
        chainctl auth login --headless --org-name precisely.com || true
        # Configure Docker credential helper (retry with sudo on permission errors)
        CFG_STATUS=0
        chainctl auth configure-docker --headless --org-name precisely.com || CFG_STATUS=$?
        if [ $CFG_STATUS -ne 0 ] || ! command -v docker-credential-cgr >/dev/null 2>&1; then
            if command -v sudo >/dev/null 2>&1; then
                echo -e "${YELLOW}Attempting to fix credential helper with elevated privileges...${NC}"
                sudo chainctl auth configure-docker --headless --org-name precisely.com || true
            fi
        fi
    fi

    # Fix credential helper symlink with proper permissions (system-wide)
    if ! command -v docker-credential-cgr >/dev/null 2>&1; then
        CHAINCTL_PATH="$(command -v chainctl || true)"
        if [ -n "$CHAINCTL_PATH" ]; then
            if command -v sudo >/dev/null 2>&1; then
                sudo ln -sf "$CHAINCTL_PATH" /usr/local/bin/docker-credential-cgr 2>/dev/null || \
                sudo install -o root -g root -m 0755 "$CHAINCTL_PATH" /usr/local/bin/docker-credential-cgr 2>/dev/null || \
                sudo ln -sf "$CHAINCTL_PATH" /usr/bin/docker-credential-cgr 2>/dev/null || \
                sudo install -o root -g root -m 0755 "$CHAINCTL_PATH" /usr/bin/docker-credential-cgr 2>/dev/null || true
            else
                echo -e "${YELLOW}⚠️  sudo not available to fix docker-credential-cgr in /usr/local/bin. Run as root:${NC}"
                echo "ln -sf $(command -v chainctl) /usr/local/bin/docker-credential-cgr || install -o root -g root -m 0755 $(command -v chainctl) /usr/local/bin/docker-credential-cgr"
            fi
        fi
    fi

    if chainctl auth status 2>/dev/null | grep -q "Valid.*True"; then
        echo -e "${GREEN}✅ Chainguard authentication is ready${NC}"
    else
        echo -e "${YELLOW}⚠️  Chainguard headless auth may require completing device flow in another terminal. Continuing; image pulls may fail if not completed.${NC}"
    fi

    # Final check for docker-credential-cgr
    if ! command -v docker-credential-cgr >/dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  docker-credential-cgr not found. Chainguard image pulls may fail.${NC}"
        echo -e "${YELLOW}   Run: sudo ln -sf \$(command -v chainctl) /usr/local/bin/docker-credential-cgr${NC}"
    fi
}

usage() {
  cat <<EOF
Usage: $0 [OPTIONS]

Generate semantic types via the backend LLM API. This script is interactive by default:
it will prompt for AWS credentials (saved securely in backend/.aws-credentials) and
offer to free the target port if already in use.

Options:
  --dataset NAME         Dataset tag for outputs/description selection (default: all)
  --descriptions LIST    Space-separated description numbers (default: "1 2 3 4 5 6")
  --data-dir DIR         Directory containing CSVs to base generation on (required if --files omitted)
  --files LIST           Comma-separated files/dirs to base generation on (required if --data-dir omitted)
  --region REGION        AWS region for Bedrock (default: us-east-1)
  --heap SIZE            JVM -Xmx size for backend (e.g., 2g). -Xms is set to 50% of this value
  --jvm-opts STRING      Full JAVA_OPTS override for backend
  --no-cleanup           Do not stop the generator backend after completion
  --default              Use default Chainguard authentication (no org-specific config)
  --use-saved-credentials Use saved AWS credentials without prompting
  --help                 Show this help

Environment variables optionally used:
  GEN_BACKEND_PORT (default 8083)

EOF
}

CLEANUP=true
USE_SAVED_CREDENTIALS=false
DEFAULT_MODE=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --dataset) DATASET="$2"; shift 2;;
    --descriptions) DESCRIPTIONS="$2"; shift 2;;
    --data-dir) DATA_DIR_OVERRIDE="$2"; shift 2;;
    --files) FILES_CSV="$2"; shift 2;;
    --region) AWS_REGION="$2"; shift 2;;
    --no-cleanup) CLEANUP=false; shift;;
    --default)
        DEFAULT_MODE=true
        shift
        ;;
    --heap)
      HEAP_RAW="$2"; shift 2
      case "$HEAP_RAW" in
        *g) HEAP_NUM=${HEAP_RAW%g}; XMS="$((HEAP_NUM/2))g";;
        *m) HEAP_NUM=${HEAP_RAW%m}; XMS="$((HEAP_NUM/2))m";;
        *) XMS="512m";;
      esac
      GEN_JAVA_OPTS="-XX:+UseContainerSupport -Xms${XMS} -Xmx${HEAP_RAW} -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
      ;;
    --jvm-opts) GEN_JAVA_OPTS="$2"; shift 2;;
    --use-saved-credentials) USE_SAVED_CREDENTIALS=true; shift;;
    --help) usage; exit 0;;
    *) echo "Unknown option: $1"; usage; exit 1;;
  esac
done

check_prereqs() {
  echo -e "${BLUE}Checking prerequisites...${NC}"
  if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}❌ Docker is required but not running${NC}"
    echo -e "${YELLOW}Try: sudo systemctl start docker${NC}"
    exit 1
  fi
  # Check Docker Compose availability
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose"
  else
    echo -e "${RED}❌ Docker Compose is required${NC}"
    echo -e "${YELLOW}Install with: sudo curl -L \"https://github.com/docker/compose/releases/latest/download/docker-compose-\$(uname -s)-\$(uname -m)\" -o /usr/bin/docker-compose && sudo chmod +x /usr/bin/docker-compose${NC}"
    exit 1
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    echo -e "${RED}❌ Python 3 is required${NC}"; exit 1
  fi
  # venv
  if [ ! -d "$VENV_DIR" ]; then
    echo -e "${YELLOW}Creating Python virtual environment...${NC}"
    python3 -m venv "$VENV_DIR"
  fi
  # Install dependencies in venv explicitly (no reliance on activation)
  PIP_DISABLE_PIP_VERSION_CHECK=1 "$VENV_PY" -m pip install --upgrade pip >/dev/null 2>&1 || true
  # Ensure baseline deps for generator scripts
  "$VENV_PY" -m pip install --quiet requests pandas tqdm PyYAML
  # Also install evaluator requirements if present, so both scripts always have deps
  if [ -f "$EVALUATOR_DIR/requirements.txt" ]; then
    echo -e "${YELLOW}Installing evaluator Python packages from requirements.txt...${NC}"
    "$VENV_PY" -m pip install -r "$EVALUATOR_DIR/requirements.txt" --quiet || true
  fi

  # Verify critical modules exist; attempt one retry if not
  "$VENV_PY" - << 'PY'
import importlib, sys
mods = ["requests", "pandas", "tqdm", "yaml"]
missing = []
for m in mods:
    try:
        importlib.import_module(m)
    except Exception:
        missing.append(m)
if missing:
    sys.exit(2)
PY
  if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Some Python modules missing (${NC}requests/pandas/tqdm/yaml${YELLOW}). Re-installing...${NC}"
    "$VENV_PY" -m pip install --quiet requests pandas tqdm PyYAML || true
  fi
  echo -e "${GREEN}✔${NC} Prerequisites satisfied"
}

# --- Interactive AWS Credentials Handling (similar to run-integration-tests.sh) ---
CREDENTIALS_FILE="$PROJECT_ROOT/backend/.aws-credentials"

load_credentials() {
  if [[ -f "$CREDENTIALS_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$CREDENTIALS_FILE"
    return 0
  fi
  return 1
}

save_credentials() {
  cat > "$CREDENTIALS_FILE" << EOF
# AWS Credentials for Generator (interactive)
# This file is automatically generated and gitignored
ACCESS_KEY_ID="$ACCESS_KEY_ID"
SECRET_ACCESS_KEY="$SECRET_ACCESS_KEY"
AWS_REGION="$AWS_REGION"
EOF
  chmod 600 "$CREDENTIALS_FILE"
}

validate_aws_credentials() {
  echo -e "${BLUE}🔒 Validating AWS credentials (if AWS CLI is available)...${NC}"
  if ! command -v aws >/dev/null 2>&1; then
    echo -e "${YELLOW}AWS CLI not found; skipping validation.${NC}"
    return 0
  fi

  local temp_config_dir
  temp_config_dir=$(mktemp -d)
  local temp_credentials_file="$temp_config_dir/credentials"
  local temp_config_file="$temp_config_dir/config"

  cat > "$temp_credentials_file" << EOF
[default]
aws_access_key_id = $ACCESS_KEY_ID
aws_secret_access_key = $SECRET_ACCESS_KEY
EOF

  cat > "$temp_config_file" << EOF
[default]
region = $AWS_REGION
output = json
EOF

  export AWS_CONFIG_FILE="$temp_config_file"
  export AWS_SHARED_CREDENTIALS_FILE="$temp_credentials_file"

  local failed=false
  if aws sts get-caller-identity >/dev/null 2>&1; then
    echo -e "${GREEN}  ✅ Credentials are valid${NC}"
  else
    echo -e "${RED}  ❌ Invalid AWS credentials${NC}"
    failed=true
  fi

  if [[ "$failed" == false ]]; then
    if aws bedrock list-foundation-models --region "$AWS_REGION" >/dev/null 2>&1; then
      echo -e "${GREEN}  ✅ Bedrock accessible in $AWS_REGION${NC}"
    else
      echo -e "${YELLOW}  ⚠️  Bedrock may not be accessible in $AWS_REGION (ensure permissions)${NC}"
    fi
  fi

  rm -rf "$temp_config_dir"
  unset AWS_CONFIG_FILE
  unset AWS_SHARED_CREDENTIALS_FILE

  if [[ "$failed" == true ]]; then
    return 1
  fi
  return 0
}

prompt_for_credentials() {
  # Check if we're in CI environment
  if [[ "${CI:-false}" == "true" ]] || [[ -n "${GITHUB_ACTIONS:-}" ]]; then
    echo -e "${BLUE}AWS Credentials Setup (CI mode)${NC}"

    # In CI, only use environment variables or saved credentials
    if [[ -n "${AWS_ACCESS_KEY_ID}" && -n "${AWS_SECRET_ACCESS_KEY}" ]]; then
      ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}"
      SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}"
      if [[ -n "${AWS_REGION}" ]]; then
        AWS_REGION="${AWS_REGION}"
      else
        AWS_REGION="us-east-1"
      fi
      echo -e "${GREEN}Using AWS credentials from environment (non-interactive)${NC}"
      export AWS_ACCESS_KEY_ID="$ACCESS_KEY_ID"
      export AWS_SECRET_ACCESS_KEY="$SECRET_ACCESS_KEY"
      export AWS_REGION="$AWS_REGION"
      return 0
    elif [[ "$USE_SAVED_CREDENTIALS" == true ]] && load_credentials; then
      echo -e "${GREEN}Using saved credentials (non-interactive)${NC}"
      echo -e "  Access Key ID: ${ACCESS_KEY_ID:0:4}****${ACCESS_KEY_ID: -4}"
      echo -e "  Region: ${AWS_REGION:-us-east-1}"
      export AWS_ACCESS_KEY_ID="$ACCESS_KEY_ID"
      export AWS_SECRET_ACCESS_KEY="$SECRET_ACCESS_KEY"
      export AWS_REGION="$AWS_REGION"
      return 0
    else
      echo -e "${YELLOW}⚠️  No AWS credentials provided in CI environment${NC}"
      echo -e "${YELLOW}   Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables${NC}"
      echo -e "${YELLOW}   Or use --use-saved-credentials if credentials are saved${NC}"
      echo -e "${YELLOW}   Skipping AWS credential validation for evaluation mode${NC}"
      return 0
    fi
  fi

  # Interactive mode for local development
  echo -e "${BLUE}AWS Credentials Setup (interactive)${NC}"
  # Non-interactive fast-path: if env vars are provided, use them and skip prompts
  if [[ -n "${AWS_ACCESS_KEY_ID}" && -n "${AWS_SECRET_ACCESS_KEY}" ]]; then
    ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}"
    SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}"
    if [[ -n "${AWS_REGION}" ]]; then
      AWS_REGION="${AWS_REGION}"
    else
      AWS_REGION="us-east-1"
    fi
    echo -e "${GREEN}Using AWS credentials from environment (non-interactive)${NC}"
    # Do not save credentials in CI automatically
    export AWS_ACCESS_KEY_ID="$ACCESS_KEY_ID"
    export AWS_SECRET_ACCESS_KEY="$SECRET_ACCESS_KEY"
    export AWS_REGION="$AWS_REGION"
    return 0
  elif [[ "$USE_SAVED_CREDENTIALS" == true ]]; then
    if load_credentials; then
      echo -e "${GREEN}Using saved credentials (non-interactive)${NC}"
      echo -e "  Access Key ID: ${ACCESS_KEY_ID:0:4}****${ACCESS_KEY_ID: -4}"
      echo -e "  Region: ${AWS_REGION:-us-east-1}"
    else
      echo -e "${RED}❌ No saved credentials found and --use-saved-credentials specified${NC}"
      echo "Please run the script without --use-saved-credentials first to save credentials, or set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables."
      exit 1
    fi
  else
    if load_credentials; then
      echo -e "${GREEN}Found saved credentials${NC}"
      echo -e "  Access Key ID: ${ACCESS_KEY_ID:0:4}****${ACCESS_KEY_ID: -4}"
      echo -e "  Region: ${AWS_REGION:-us-east-1}"
      echo -n "Use saved credentials? (Y/n): "
      read -r USE_SAVED
      if [[ "$USE_SAVED" =~ ^[Nn]$ ]]; then
        ACCESS_KEY_ID=""; SECRET_ACCESS_KEY=""; AWS_REGION="${AWS_REGION:-us-east-1}"
      fi
    fi

  if [[ -z "$ACCESS_KEY_ID" || -z "$SECRET_ACCESS_KEY" ]]; then
    echo -n "AWS Access Key ID: "
    read -r ACCESS_KEY_ID
    echo -n "AWS Secret Access Key: "
    read -r -s SECRET_ACCESS_KEY; echo ""
    echo -n "AWS Region [us-east-1]: "
    read -r INPUT_REGION
    if [[ -n "$INPUT_REGION" ]]; then
      AWS_REGION="$INPUT_REGION"
    else
      AWS_REGION="us-east-1"
    fi
    save_credentials
    echo -e "${GREEN}✅ Credentials saved${NC}"
  fi
  fi

  # Export to environment for docker compose and Python
  export AWS_ACCESS_KEY_ID="$ACCESS_KEY_ID"
  export AWS_SECRET_ACCESS_KEY="$SECRET_ACCESS_KEY"
  export AWS_REGION="$AWS_REGION"

  if ! validate_aws_credentials; then
    echo -e "${RED}Credential validation failed. Aborting.${NC}"
    exit 1
  fi
}

start_backend() {
  echo -e "${BLUE}Starting generator backend on port $GEN_BACKEND_PORT...${NC}"
  # Check if port is already in use and offer cleanup similar to run-eval.sh
  if lsof -Pi :$GEN_BACKEND_PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${YELLOW}  Port $GEN_BACKEND_PORT is already in use${NC}"
    if [[ "${CI}" == "true" ]]; then
      COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
        $COMPOSE -f "$PROJECT_ROOT/docker-compose.dev.yml" down 2>/dev/null || true
      docker ps --format "table {{.Names}}\t{{.Ports}}" | grep $GEN_BACKEND_PORT | awk '{print $1}' | xargs -r docker stop 2>/dev/null || true
      sleep 2
    else
      echo -n "Stop the existing process and continue? (y/N): "
      read -r CONTINUE
      if [[ "$CONTINUE" =~ ^[Yy]$ ]]; then
        COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
          $COMPOSE -f "$PROJECT_ROOT/docker-compose.dev.yml" down 2>/dev/null || true
        docker ps --format "table {{.Names}}\t{{.Ports}}" | grep $GEN_BACKEND_PORT | awk '{print $1}' | xargs -r docker stop 2>/dev/null || true
        sleep 2
      else
        echo "Exiting..."; exit 1
      fi
    fi
  fi
  cd "$PROJECT_ROOT"
  # Bring down any old generator backend first
  COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
    $COMPOSE -f docker-compose.dev.yml down 2>/dev/null || true

  COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
  BACKEND_PORT=$GEN_BACKEND_PORT \
  AUTH_PASSWORD=gen-password-2024 \
  JWT_SECRET=gen-secret-minimum-32-characters-long-2024 \
  LOGGING_LEVEL_COM_NL2FTA=DEBUG \
  LOGGING_LEVEL_COM_NL2FTA_CLASSIFIER=DEBUG \
  LOGGING_LEVEL_ROOT=INFO \
  AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID} \
  AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY} \
  AWS_DEFAULT_REGION=${AWS_REGION} \
  JAVA_OPTS="$GEN_JAVA_OPTS" \
  VECTOR_INDEX_ENABLED=${VECTOR_INDEX_ENABLED:-false} \
  VECTOR_INDEX_REBUILD_ON_STARTUP=${VECTOR_INDEX_REBUILD_ON_STARTUP:-false} \
    $COMPOSE -f docker-compose.dev.yml up -d backend

  echo -e "Using JVM options: ${YELLOW}$GEN_JAVA_OPTS${NC}"

  echo -n "Waiting for backend to start"
  for i in {1..160}; do
    if curl -s "http://localhost:$GEN_BACKEND_PORT/api/health" >/dev/null 2>&1; then
      echo ""; echo -e "${GREEN}✔${NC} Backend is ready"
      return 0
    fi
    echo -n "."; sleep 2
  done
  echo ""; echo -e "${RED}❌ Backend failed to start${NC}"; exit 1
}

stop_backend() {
  if [ "$CLEANUP" = true ]; then
    echo -e "${YELLOW}Stopping generator backend...${NC}"
    cd "$PROJECT_ROOT"
    COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
      $COMPOSE -f docker-compose.dev.yml down || true
    echo -e "${GREEN}✔${NC} Cleanup complete"
  else
    echo -e "${YELLOW}ℹ️  Backend left running on port $GEN_BACKEND_PORT${NC}"
  fi
}

run_generation() {
  echo -e "${BLUE}Running generator...${NC}"

  PY="${EVALUATOR_DIR}/src/generate_semantic_types.py"
  API_BASE="http://localhost:$GEN_BACKEND_PORT/api"

  # Inputs are authoritative in evaluator/datasets/generation-inputs. No copying from data/.
  GEN_INPUTS_DIR="$EVALUATOR_DIR/datasets/generation-inputs"
  mkdir -p "$GEN_INPUTS_DIR"

  FILES_ARG=""
  if [ -n "$FILES_CSV" ]; then
    FILES_ARG="--files \"$FILES_CSV\""
  fi

  # No short names: generation uses data file names for output naming
  [ -n "$DESCRIPTIONS" ] && echo "Descriptions: $DESCRIPTIONS"

  EVALUATOR_API_BASE_URL="$API_BASE" \
  EVALUATOR_RUN_DIR="$RUN_DIR" \
  EVAL_RUN_TIMESTAMP="$RUN_TS" \
    bash -lc "\"$VENV_PY\" -u \"$PY\" \
      --dataset \"$DATASET\" \
      ${DESCRIPTIONS:+--descriptions \"$DESCRIPTIONS\"} \
      --api-base-url \"$API_BASE\" \
      ${DATA_DIR_OVERRIDE:+--data-dir \"$DATA_DIR_OVERRIDE\"} \
      $FILES_ARG \
      --region \"$AWS_REGION\" \
      --run-timestamp \"$RUN_TS\""
}

print_backend_logs() {
  echo -e "${BLUE}Backend logs (tail)${NC}"
  # Attempt to show recent backend logs to surface generation/parsing issues
  COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
    $COMPOSE -f "$PROJECT_ROOT/docker-compose.dev.yml" logs --no-color --tail=400 backend || true
}

trap 'stop_backend' EXIT

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║               NL2FTA Semantic Type Generator                     ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════╝${NC}"

if [ "$DEFAULT_MODE" = true ]; then
    echo -e "${YELLOW}📝 Using default Chainguard authentication (no org-specific config)${NC}"
fi

check_prereqs
setup_chainguard "$DEFAULT_MODE"
prompt_for_credentials
start_backend
run_generation

echo ""
echo -e "${GREEN}Generation complete. Outputs in ${GEN_DIR}${NC}"

# Always print backend logs in CI to expose generation/parsing problems
if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
  print_backend_logs
fi

# Print an eval-style summary table (with deltas vs baseline) and append to GitHub Step Summary if present
echo ""
echo -e "${BLUE}Final summary (best metrics per dataset)${NC}"
RUN_TS_EXPORT="$RUN_TS" EVALUATOR_DIR_EXPORT="$EVALUATOR_DIR" VENV_PY_EXPORT="$VENV_PY" EVAL_SUMMARY_MODE_EXPORT="${EVAL_SUMMARY_MODE:-custom-only}" bash -c '
  EVAL_RUN_TIMESTAMP="$RUN_TS_EXPORT" "$VENV_PY_EXPORT" - << PY
import os, sys, json, csv
from glob import glob

project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
evaluator_dir = os.environ.get("EVALUATOR_DIR_EXPORT") or os.path.join(project_root, "evaluator")
run_ts = os.environ.get("EVAL_RUN_TIMESTAMP")
if not run_ts:
    sys.exit(0)

logs_run_dir = os.path.join(evaluator_dir, "logs", run_ts)
files = []
files += glob(os.path.join(logs_run_dir, "*_profile_results_*.json")) if os.path.isdir(logs_run_dir) else []

if not files:
    print("No per-description results found for run", run_ts)
    sys.exit(0)

metric_priority = ["overall_score","accuracy","macro_f1","micro_f1","f1","f1_score","precision","recall","map","mrr","ndcg"]

def choose_primary_metric(metrics: dict):
    for name in metric_priority:
        if name in metrics:
            return name, metrics[name]
    if metrics:
        name, value = sorted(metrics.items())[0]
        return name, value
    return None, None

def best_from_file(path: str):
    try:
        with open(path, "r") as f:
            data = json.load(f)
    except Exception:
        return None
    results = data.get("results", {}) if isinstance(data, dict) else {}
    dataset = results.get("dataset") or data.get("dataset")
    baseline = None
    if isinstance(results.get("baseline"), dict):
        baseline = {k: float(v) for k, v in results["baseline"].items() if isinstance(v, (int,float)) and not isinstance(v, bool)}
    candidates = []
    custom = results.get("custom_evaluations")
    if isinstance(custom, dict):
        for desc, val in custom.items():
            if isinstance(val, dict) and isinstance(val.get("metrics"), dict):
                m = {k: float(v) for k, v in val["metrics"].items() if isinstance(v, (int,float)) and not isinstance(v, bool)}
                if m:
                    candidates.append((desc, m))
    best = None
    best_pn, best_pv = None, None
    for desc, metrics in candidates:
        pn, pv = choose_primary_metric(metrics)
        if pn is None:
            continue
        if best is None or pv > best_pv:
            best = (desc, metrics)
            best_pn, best_pv = pn, pv
    if dataset is None or best is None:
        return None
    return {"dataset": dataset, "baseline": baseline, "best": {"description": best[0], "metrics": best[1], "primary_metric": best_pn, "primary_value": best_pv}}

by_dataset = {}
for fp in files:
    rec = best_from_file(fp)
    if not rec: 
        continue
    ds = rec["dataset"]
    prev = by_dataset.get(ds)
    # Prefer higher primary_value when comparing bests
    if not prev:
        by_dataset[ds] = rec
    else:
        prev_best = prev.get("best") or {}
        if rec["best"]["primary_value"] > (prev_best.get("primary_value") or float("-inf")):
            by_dataset[ds] = rec

if not by_dataset:
    print("No usable metrics found for run", run_ts)
    sys.exit(0)

def fmt(v):
    if isinstance(v, float):
        return f"{v:.6f}".rstrip("0").rstrip(".")
    return str(v) if v is not None else ""

mode = os.environ.get("EVAL_SUMMARY_MODE_EXPORT", "custom-only").strip().lower()

if mode == "no-custom":
    cols = ["Dataset","Accuracy","F1","Precision","Recall"]
elif mode == "custom-only":
    cols = ["Dataset","Best Description","Accuracy","F1","Precision","Recall"]
else:
    cols = [
        "Dataset","Best Description",
        "Accuracy","Δ Accuracy","F1","Δ F1","Precision","Δ Precision","Recall","Δ Recall"
    ]

rows = []
for ds, rec in sorted(by_dataset.items()):
    base = rec.get("baseline") or {}
    best = rec.get("best")
    if mode == "no-custom":
        if not base: 
            continue
        f1b = base.get("f1_score", base.get("f1"))
        rows.append([ds, fmt(base.get("accuracy")), fmt(f1b), fmt(base.get("precision")), fmt(base.get("recall"))])
        continue

    if not best:
        continue
    m = best["metrics"]
    acc = m.get("accuracy"); f1 = m.get("f1_score", m.get("f1")); prec = m.get("precision"); rec_v = m.get("recall")
    if mode == "custom-only":
        rows.append([ds, best["description"], fmt(acc), fmt(f1), fmt(prec), fmt(rec_v)])
    else:
        acc_b = base.get("accuracy"); d_acc = (acc - acc_b) if (isinstance(acc,(int,float)) and isinstance(acc_b,(int,float))) else None
        f1_b = base.get("f1_score", base.get("f1")); d_f1 = (f1 - f1_b) if (isinstance(f1,(int,float)) and isinstance(f1_b,(int,float))) else None
        prec_b = base.get("precision"); d_prec = (prec - prec_b) if (isinstance(prec,(int,float)) and isinstance(prec_b,(int,float))) else None
        rec_b = base.get("recall"); d_rec = (rec_v - rec_b) if (isinstance(rec_v,(int,float)) and isinstance(rec_b,(int,float))) else None
        rows.append([ds, best["description"], fmt(acc), fmt(d_acc), fmt(f1), fmt(d_f1), fmt(prec), fmt(d_prec), fmt(rec_v), fmt(d_rec)])

widths = [len(h) for h in cols]
for r in rows:
    for i, v in enumerate(r):
        widths[i] = max(widths[i], len(str(v)))

def line(l, m, r, fill="─"):
    return l + m.join(fill * (w + 2) for w in widths) + r

print("\nFinal summary (best metrics per dataset):")
print(line("┌","┬","┐"))
print("│ " + " │ ".join(cols[i].ljust(widths[i]) for i in range(len(widths))) + " │")
print(line("├","┼","┤"))
for r in rows:
    print("│ " + " │ ".join(str(r[i]).ljust(widths[i]) for i in range(len(r))) + " │")
print(line("└","┴","┘"))

# Persist Markdown/CSV into logs/<run_ts>
logs_run_dir = os.path.join(evaluator_dir, 'logs', run_ts)
os.makedirs(logs_run_dir, exist_ok=True)
csv_path = os.path.join(logs_run_dir, "final_summary.csv")
md_path = os.path.join(logs_run_dir, "final_summary.md")
with open(csv_path, "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(cols)
    for r in rows:
        w.writerow(r)
with open(md_path, "w") as f:
    f.write("| " + " | ".join(cols) + " |\n")
    f.write("| " + " | ".join(["---"]*len(cols)) + " |\n")
    for r in rows:
        f.write("| " + " | ".join(str(x) for x in r) + " |\n")

print(f"\nSaved summary to:\n - {csv_path}\n - {md_path}")
PY

  # Append to GH Step Summary if present
  if [ -n "$GITHUB_STEP_SUMMARY" ] && [ -f "$EVALUATOR_DIR/logs/$RUN_TS/final_summary.md" ]; then
    {
      echo "# NL2FTA Evaluation Summary"
      echo ""
      echo "Run: $RUN_TS"
      echo ""
      cat "$EVALUATOR_DIR/logs/$RUN_TS/final_summary.md"
    } >> "$GITHUB_STEP_SUMMARY"
  fi
'

exit 0


