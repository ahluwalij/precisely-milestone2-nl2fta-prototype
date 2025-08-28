#!/bin/bash
# NL2FTA Evaluation Runner
# Works with dummy AWS credentials - no cloud access required

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
EVALUATOR_DIR="$PROJECT_ROOT/evaluator"
BACKEND_PORT=8082
COMPOSE_PROJECT="nl2fta_eval"
VENV_DIR="$PROJECT_ROOT/.venv-nl2fta"
VENV_PY="$VENV_DIR/bin/python"
VENV_PIP="$VENV_DIR/bin/pip"
RUN_TS="$(date +%Y%m%d_%H%M%S)"
RUN_DIR="$EVALUATOR_DIR/logs/$RUN_TS"
mkdir -p "$RUN_DIR"
LOG_FILE="$RUN_DIR/run-eval.log"
export EVAL_RUN_TIMESTAMP="$RUN_TS"

# Stream all stdout/stderr to the per-run log as well as console
exec > >(tee -a "$LOG_FILE") 2>&1

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
DATASET="all"
# If empty, we'll auto-detect available descriptions per dataset from generated files
DESCRIPTIONS=""
TIMESTAMP=""
CLEANUP=true
VERBOSE=false
DEBUG_LOGGING=false
DEFAULT_MODE=false

# Global multi-file input (new normal) - now REQUIRED to specify inputs
FILES_CSV=""           # comma-separated list of files/dirs for any dataset
DATA_DIR_OVERRIDE=""   # data directory to scan (required if --files not provided)

# JVM settings for backend during eval (tunable via --heap / --jvm-opts)
EVAL_JAVA_OPTS="-XX:+UseContainerSupport -Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Data truncation control (default: true for reliability on large datasets)
TRUNCATE_DATA=true

# Per-dataset file enumeration cap when auto-selecting files (explicit --files not capped)
PER_DATASET_MAX_FILES=${PER_DATASET_MAX_FILES:-250}

# F1 sum mode configuration
SUM_F1_MODE=false
EXPECTED_DATASETS=("extension" "transactions" "banking" "insurance" "telco_5GTraffic" "telco_customer_churn" "semtab")
EXPECTED_DESCRIPTIONS=("1" "2" "3" "4" "5" "6")

# No global per-run file cap; evaluator itself handles row/column caps

# Determine Docker Compose command (prefer v2 plugin)
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
else
    COMPOSE="docker-compose"
fi

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --dataset)
            DATASET="$2"
            shift 2
            ;;
        --descriptions)
            DESCRIPTIONS="$2"
            shift 2
            ;;
        --timestamp)
            TIMESTAMP="$2"
            shift 2
            ;;
        --no-cleanup)
            CLEANUP=false
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --debug)
            DEBUG_LOGGING=true
            shift
            ;;
        --heap)
            # Shorthand to set max heap (e.g., 2g, 3g). Also set Xms to half of Xmx.
            HEAP_RAW="$2"
            shift 2
            # Derive Xms as 50% of Xmx when numeric given (best-effort)
            case "$HEAP_RAW" in
                *g)
                    HEAP_NUM=${HEAP_RAW%g}
                    XMS="$((HEAP_NUM/2))g"
                    ;;
                *m)
                    HEAP_NUM=${HEAP_RAW%m}
                    XMS="$((HEAP_NUM/2))m"
                    ;;
                *)
                    XMS="512m"
                    ;;
            esac
            EVAL_JAVA_OPTS="-XX:+UseContainerSupport -Xms${XMS} -Xmx${HEAP_RAW} -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
            ;;
        --jvm-opts)
            # Full override for JAVA_OPTS used by backend container
            EVAL_JAVA_OPTS="$2"
            shift 2
            ;;
        --full-data)
            # Disable truncation; send full dataset rows to backend
            TRUNCATE_DATA=false
            shift
            ;;
        --files)
            # Comma-separated list of files/dirs to evaluate (applies to all datasets)
            FILES_CSV="$2"
            shift 2
            ;;
        --data-dir)
            # Override base data directory used for dataset defaults
            DATA_DIR_OVERRIDE="$2"
            shift 2
            ;;
        --sum-f1)
            # Sum F1 scores across all datasets and descriptions (forces specific settings)
            SUM_F1_MODE=true
            DATASET="all"
            DESCRIPTIONS="1 2 3 4 5 6"
            EVAL_MODE="custom-only"
            shift
            ;;
        --default)
            DEFAULT_MODE=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Run evaluation on pre-generated semantic types without AWS dependencies."
            echo ""
            echo "Options:"
            echo "  --dataset NAME         Logical tag for outputs/description set selection (derived from data dir if omitted)"
            echo "  --descriptions LIST    Space-separated description numbers (default: auto-detect from generated files)"
            echo "  --timestamp TS        Specific generated-types timestamp (YYYYMMDD_HHMMSS) to use"
            echo "  --heap SIZE           JVM heap for backend (e.g., 2g, 3g). Shorthand for -Xmx; also sets -Xms to 50%"
            echo "  --jvm-opts STRING     Full JAVA_OPTS override for backend (advanced)"
            echo "  --no-cleanup          Don't stop backend after evaluation"
            echo "  --verbose             Show detailed output including AWS warnings"
            echo "  --debug               Enable debug-level logging in the evaluation script"
            echo "  --full-data           Use full dataset rows (disables default row-cap truncation)"
            echo "  --data-dir DIR        Directory containing CSVs to evaluate (required if --files omitted)"
            echo "  --files LIST          Comma-separated files/dirs to evaluate (required if --data-dir omitted)"
            echo "  --default             Use default Chainguard authentication (no org-specific config)"
            echo "  --sum-f1              Sum F1 scores across all datasets and descriptions (forces --dataset all --descriptions '1 2 3 4 5 6' --mode custom-only)"
            echo "  --help                Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  PER_DATASET_MAX_FILES  Cap auto-enumerated files per dataset (default: 250)."
            echo "                         Note: --full-data lifts this file cap. Explicit --files are never capped."
            echo ""
            echo "Examples:"
            echo "  $0                                    # Run all datasets (comparative analysis)"
            echo "  $0 --dataset extension --descriptions '6'                  # Extension dataset, desc 6"
            echo "  $0 --dataset telco_customer_churn --descriptions '1 2 3'   # Telco churn subset"
            echo "  $0 --descriptions \"1 3 5\"             # Run specific descriptions only"
            echo "  $0 --heap 2g                          # Increase backend heap to 2 GB for large runs"
            echo "  $0 --full-data                       # Disable truncation and use full rows"
            echo "  $0 --jvm-opts '-Xms1g -Xmx3g -XX:+UseG1GC'   # Advanced JVM tuning"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Run '$0 --help' for usage information"
            exit 1
            ;;
    esac
done

# Functions
check_prerequisites() {
    echo -e "${BLUE}Checking prerequisites...${NC}"
    
    # Check Docker
    if ! docker info >/dev/null 2>&1; then
        echo -e "${RED}❌ Docker is required but not running${NC}"
        echo ""
        echo "Please start Docker and try again."
        echo "To install Docker (Amazon Linux), follow:"
        echo "https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-docker.html#install-docker-instructions"
        exit 1
    fi
    
    # Check Python
    if ! command -v python3 &> /dev/null; then
        echo -e "${RED}❌ Python 3 is required but not installed${NC}"
        echo ""
        echo "Please install Python 3.8 or later."
        echo "Download from: https://www.python.org/downloads/"
        exit 1
    fi
    
    # Check Python version
    PYTHON_VERSION=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
    MAJOR=$(echo $PYTHON_VERSION | cut -d. -f1)
    MINOR=$(echo $PYTHON_VERSION | cut -d. -f2)
    
    if [ "$MAJOR" -lt 3 ] || ([ "$MAJOR" -eq 3 ] && [ "$MINOR" -lt 8 ]); then
        echo -e "${RED}❌ Python 3.8+ is required (found $PYTHON_VERSION)${NC}"
        exit 1
    fi
    
    # Ensure Java 17 is available for Gradle toolchain
    NEED_JAVA17=false
    if ! command -v java >/dev/null 2>&1; then
        NEED_JAVA17=true
        echo -e "${YELLOW}Java not found. Will install OpenJDK 17...${NC}"
    else
        # Parse major version from: 'openjdk version "17.0.11" ...' or 'java version "1.8.0_..."'
        JV=$(java -version 2>&1 | head -1)
        MAJ=$(echo "$JV" | sed -n 's/.*version \"\([0-9][0-9]*\)\..*/\1/p')
        if [ -z "$MAJ" ]; then
            MAJ=$(echo "$JV" | sed -n 's/.*version \"1\.\([0-9][0-9]*\).*\"/\1/p')
        fi
        if [ "$MAJ" != "17" ]; then
            NEED_JAVA17=true
            echo -e "${YELLOW}Java version is not 17 (detected: $JV). Will install OpenJDK 17 and prefer it for this run...${NC}"
        fi
    fi

    if [ "$NEED_JAVA17" = true ]; then
        echo -e "${YELLOW}Attempting to install OpenJDK 17...${NC}"

        add_corretto_repo() {
            if command -v sudo >/dev/null 2>&1; then
                # Import key (idempotent)
                sudo rpm --import https://yum.corretto.aws/corretto.key 2>/dev/null || true
                # Install repo file if missing
                if [ ! -f /etc/yum.repos.d/corretto.repo ]; then
                    sudo curl -fsSL -o /etc/yum.repos.d/corretto.repo https://yum.corretto.aws/corretto.repo || true
                fi
            fi
        }

        # Debian/Ubuntu (GitHub Actions runners, many Linux distros)
        if command -v sudo >/dev/null 2>&1 && command -v apt-get >/dev/null 2>&1; then
            sudo apt-get update -y >/dev/null 2>&1 || true
            sudo apt-get install -y openjdk-17-jdk >/dev/null 2>&1 || \
            sudo apt-get install -y temurin-17-jdk >/dev/null 2>&1 || \
            sudo apt-get install -y default-jdk >/dev/null 2>&1 || true
        fi

        # RHEL/CentOS/Amazon Linux
        if ! command -v java >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1 && command -v yum >/dev/null 2>&1; then
            add_corretto_repo
            sudo yum install -y java-17-amazon-corretto java-17-amazon-corretto-devel >/dev/null 2>&1 || \
            sudo yum install -y java-17-openjdk java-17-openjdk-devel >/dev/null 2>&1 || true
        fi
        if ! command -v java >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1 && command -v dnf >/dev/null 2>&1; then
            add_corretto_repo
            sudo dnf install -y java-17-amazon-corretto java-17-amazon-corretto-devel >/dev/null 2>&1 || \
            sudo dnf install -y java-17-openjdk java-17-openjdk-devel >/dev/null 2>&1 || true
        fi

        # Alpine
        if ! command -v java >/dev/null 2>&1 && command -v apk >/dev/null 2>&1; then
            apk add --no-cache openjdk17-jre-headless openjdk17 >/dev/null 2>&1 || true
        fi

        # macOS with Homebrew
        if ! command -v java >/dev/null 2>&1 && command -v brew >/dev/null 2>&1; then
            brew install openjdk@17 >/dev/null 2>&1 || true
            # On macOS, ensure JAVA_HOME and PATH are set for this shell
            if [ -d "/opt/homebrew/opt/openjdk@17" ]; then
                export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
                export PATH="$JAVA_HOME/bin:$PATH"
            elif [ -d "/usr/local/opt/openjdk@17" ]; then
                export JAVA_HOME="/usr/local/opt/openjdk@17"
                export PATH="$JAVA_HOME/bin:$PATH"
            fi
        fi

        # Try to locate a Java 17 home and export JAVA_HOME for this shell
        detect_java17_home() {
            # Probe common locations and globbed variants
            for d in \
                /usr/lib/jvm/java-17-amazon-corretto \
                /usr/lib/jvm/java-17-openjdk \
                /usr/lib/jvm/java-17-openjdk-amd64 \
                /Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home \
                /opt/homebrew/opt/openjdk@17 \
                /usr/local/opt/openjdk@17; do
                if [ -x "$d/bin/java" ]; then
                    echo "$d"; return 0; fi
            done
            # Glob any 17 JDKs under /usr/lib/jvm
            for d in $(ls -d /usr/lib/jvm/java-17* 2>/dev/null || true); do
                if [ -x "$d/bin/java" ]; then
                    echo "$d"; return 0; fi
            done
            return 1
        }
        JH=$(detect_java17_home || true)
        if [ -n "$JH" ]; then
            export JAVA_HOME="$JH"
            export ORG_GRADLE_JAVA_HOME="$JAVA_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
        fi

        # Final check
        if ! java -version >/dev/null 2>&1; then
            echo -e "${RED}❌ Java is required but could not be installed automatically${NC}"
            echo "Please install JDK 17 (e.g., Temurin or OpenJDK) and re-run."
            exit 1
        else
            echo -e "${GREEN}✔${NC} Java installed: $(java -version 2>&1 | head -1)"
            echo -e "Using JAVA_HOME=${JAVA_HOME}"
        fi
    fi
    
    # Check evaluator directory
    if [ ! -d "$EVALUATOR_DIR" ]; then
        echo -e "${RED}❌ Evaluator directory not found: $EVALUATOR_DIR${NC}"
        exit 1
    fi
    
    # If no inputs provided, decide behavior based on --dataset
    if [ -z "$FILES_CSV" ] && [ -z "$DATA_DIR_OVERRIDE" ]; then
        if [ "$DATASET" != "all" ]; then
            echo -e "${YELLOW}No --data-dir/--files provided; will use default directory: evaluator/datasets/data/${DATASET}${NC}"
        else
            echo -e "${YELLOW}No --data-dir/--files provided; will enumerate all dataset directories under data/.${NC}"
        fi
    fi
    
    # Setup virtual environment if needed
    if [ ! -d "$VENV_DIR" ]; then
        echo -e "${YELLOW}Creating Python virtual environment...${NC}"
        PYBIN=$(command -v python3.11 || command -v python3.10 || command -v python3)
        "$PYBIN" -m venv "$VENV_DIR"
        if [ $? -ne 0 ]; then
            echo -e "${RED}❌ Failed to create virtual environment${NC}"
            echo "Please ensure python3-venv is installed: apt-get install python3-venv (Linux) or brew install python3 (macOS)"
            exit 1
        fi
    fi
    
    # Use venv python/pip explicitly to avoid PATH/login shell issues
    PIP_DISABLE_PIP_VERSION_CHECK=1 "$VENV_PY" -m pip install --upgrade pip setuptools wheel >/dev/null 2>&1 || true
    
    # Check Python packages (prefer lock file in packaged client, then requirements.txt, else sensible defaults)
    REQUIREMENTS_LOCK_FILE="$EVALUATOR_DIR/requirements.lock.txt"
    REQUIREMENTS_FILE="$EVALUATOR_DIR/requirements.txt"
    if [ -f "$REQUIREMENTS_LOCK_FILE" ]; then
        echo -e "${YELLOW}Installing evaluator dependencies from requirements.lock.txt (this may take several minutes)...${NC}"
        "$VENV_PY" -m pip install --upgrade setuptools wheel >/dev/null 2>&1 || true
        # Show progress (no --quiet) so users see activity instead of thinking it stalled
        "$VENV_PY" -m pip install -r "$REQUIREMENTS_LOCK_FILE"
        if [ $? -ne 0 ]; then
            echo -e "${RED}❌ Failed to install Python packages from requirements.lock.txt${NC}"
            echo "Please run manually: pip3 install -r $REQUIREMENTS_LOCK_FILE"
            exit 1
        fi
    elif [ -f "$REQUIREMENTS_FILE" ]; then
        echo -e "${YELLOW}Installing evaluator dependencies from requirements.txt...${NC}"
        "$VENV_PY" -m pip install -r "$REQUIREMENTS_FILE"
        if [ $? -ne 0 ]; then
            echo -e "${RED}❌ Failed to install Python packages from requirements.txt${NC}"
            echo "Please run manually: pip3 install -r $REQUIREMENTS_FILE"
            exit 1
        fi
    else
        echo -e "${YELLOW}requirements.txt not found, installing default packages (pandas, numpy, requests, tqdm, PyYAML, rich, scikit-learn)...${NC}"
        "$VENV_PY" -m pip install pandas numpy requests tqdm PyYAML rich scikit-learn
        if [ $? -ne 0 ]; then
            echo -e "${RED}❌ Failed to install default Python packages${NC}"
            echo "Please run manually: pip3 install pandas numpy requests tqdm PyYAML rich scikit-learn"
            exit 1
        fi
    fi
    
    # Verify critical modules exist; attempt one retry if not
    "$VENV_PY" - << 'PY'
import importlib, sys
mods = ["tqdm", "requests", "pandas", "numpy", "yaml", "sklearn", "rich"]
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
        echo -e "${YELLOW}Some Python modules missing (${NC}tqdm/requests/pandas/numpy/yaml/sklearn/rich${YELLOW}). Re-installing...${NC}"
        if [ -f "$REQUIREMENTS_LOCK_FILE" ]; then
            "$VENV_PY" -m pip install -r "$REQUIREMENTS_LOCK_FILE" || true
        elif [ -f "$REQUIREMENTS_FILE" ]; then
            "$VENV_PY" -m pip install -r "$REQUIREMENTS_FILE" || true
        else
            "$VENV_PY" -m pip install pandas numpy requests tqdm PyYAML rich scikit-learn || true
        fi
    fi
    
    echo -e "${GREEN}✔${NC} Prerequisites satisfied"
}

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

start_backend() {
    echo -e "${BLUE}Starting evaluation backend on port $BACKEND_PORT...${NC}"
    # Non-interactive port handling: free the port if occupied
    for port in $BACKEND_PORT; do
    	echo -e "Checking backend on port $port"
        if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo -e "${YELLOW}  Port $port is in use; stopping existing containers...${NC}"
            COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
                $COMPOSE -f "$PROJECT_ROOT/docker-compose.dev.yml" down 2>/dev/null || true
            docker ps --format "table {{.Names}}\t{{.Ports}}" | grep $port | awk '{print $1}' | xargs -r docker stop 2>/dev/null || true
            sleep 2
        fi
    	echo -e "Done checking backend on port $port"
    done
    
    echo -e "Stop any existing evaluation backend with same project name"
    # Stop any existing evaluation backend with same project name
    COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
    AUTH_PASSWORD=${AUTH_PASSWORD:-eval-password-2024} \
    JWT_SECRET=${JWT_SECRET:-eval-secret-minimum-32-characters-long-2024} \
    AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-DUMMY_KEY_FOR_EVAL_ONLY} \
    AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-DUMMY_SECRET_FOR_EVAL_ONLY} \
        $COMPOSE -f "$PROJECT_ROOT/docker-compose.dev.yml" down 2>/dev/null || true
    
    echo -e "Done stopping any existing evaluation backend with same project name"
    echo -e "Starting evaluation backend ..."
    cd "$PROJECT_ROOT"
    
    COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
    BACKEND_PORT=$BACKEND_PORT \
    AUTH_PASSWORD=eval-password-2024 \
    JWT_SECRET=eval-secret-minimum-32-characters-long-2024 \
    AWS_ACCESS_KEY_ID=DUMMY_KEY_FOR_EVAL_ONLY \
    AWS_SECRET_ACCESS_KEY=DUMMY_SECRET_FOR_EVAL_ONLY \
    AWS_DEFAULT_REGION=us-east-1 \
    JAVA_OPTS="$EVAL_JAVA_OPTS" \
    VECTOR_INDEX_ENABLED=${VECTOR_INDEX_ENABLED:-false} \
    VECTOR_INDEX_REBUILD_ON_STARTUP=${VECTOR_INDEX_REBUILD_ON_STARTUP:-false} \
        $COMPOSE -f docker-compose.dev.yml up -d backend
    
    # Show JVM opts info
    echo -e "Using JVM options for backend: ${YELLOW}$EVAL_JAVA_OPTS${NC}"

    # Wait for backend to be ready
    echo -n "Waiting for backend to start"
    for i in {1..120}; do
        if curl -s http://localhost:$BACKEND_PORT/api/health > /dev/null 2>&1; then
            echo ""
            echo -e "${GREEN}✔ ${NC}Backend is ready"
            return 0
        fi
        echo -n "."
        sleep 2
    done
    
    echo ""
    echo -e "${RED}❌ Backend failed to start${NC}"
    echo "Showing recent logs:"
    COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
    AUTH_PASSWORD=${AUTH_PASSWORD:-eval-password-2024} \
    JWT_SECRET=${JWT_SECRET:-eval-secret-minimum-32-characters-long-2024} \
    AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-DUMMY_KEY_FOR_EVAL_ONLY} \
    AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-DUMMY_SECRET_FOR_EVAL_ONLY} \
        $COMPOSE -f docker-compose.dev.yml logs --tail=50 backend
    exit 1
}

run_evaluation() {
    # Prefer branch behavior: derive dataset tag from inputs; but also support 'all'
    # Use the pre-initialized run timestamp for the entire invocation
    echo -e "Using run timestamp: ${YELLOW}$EVAL_RUN_TIMESTAMP${NC}"
    if [ -n "$DATA_DIR_OVERRIDE" ] || [ -n "$FILES_CSV" ]; then
        # When explicit inputs provided, run a single logical dataset (tag derived later)
        DATASETS_TO_RUN="${DATASET:-custom}"
    else
        # No explicit inputs:
        # If a specific dataset tag was provided, use its default directory; otherwise enumerate all
        UAGI_DIR="$EVALUATOR_DIR/datasets/data"
        if [ ! -d "$UAGI_DIR" ]; then
            echo -e "${RED}❌ UAGI datasets directory not found: $UAGI_DIR${NC}"
            exit 1
        fi
        if [ "$DATASET" != "all" ]; then
            # Validate the single dataset directory exists and has *_data.csv
            SINGLE_DIR="$UAGI_DIR/$DATASET"
            if [ ! -d "$SINGLE_DIR" ]; then
                echo -e "${RED}❌ Dataset directory not found: $SINGLE_DIR${NC}"
                echo "Create it or pass --data-dir to point at your data."
                exit 1
            fi
            if ! ls -1 "$SINGLE_DIR"/*_data.csv >/dev/null 2>&1; then
                echo -e "${YELLOW}⚠ No '*_data.csv' found in $SINGLE_DIR. The evaluator will still scan CSVs under this directory.${NC}"
            fi
            DATASETS_TO_RUN="$DATASET"
        else
            DATASETS_TO_RUN=""
            for d in "$UAGI_DIR"/*; do
                [ -d "$d" ] || continue
                tag=$(basename "$d")
                # Skip example dataset (has incomplete description files)
                if [ "$tag" = "example" ]; then
                    continue
                fi
                # Prefer *_data.csv, but fall back to any .csv (e.g., semtab)
                if ls -1 "$d"/*_data.csv >/dev/null 2>&1 || ls -1 "$d"/*.csv >/dev/null 2>&1; then
                    DATASETS_TO_RUN="$DATASETS_TO_RUN $tag"
                fi
            done
            DATASETS_TO_RUN=$(echo "$DATASETS_TO_RUN" | xargs)
            if [ -z "$DATASETS_TO_RUN" ]; then
                echo -e "${RED}❌ No dataset directories with *_data.csv found under $UAGI_DIR${NC}"
                exit 1
            fi
        fi
    fi

    # No temp copy needed; pass API base via env/flag

    for d in $DATASETS_TO_RUN; do
        echo ""
        echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
        # Tag is authoritative from --dataset when provided; otherwise derived
        DERIVED_TAG="$d"
        if [ -n "$DATASET" ] && [ "$DATASET" != "all" ]; then
            DERIVED_TAG="$DATASET"
        elif [ -n "$DATA_DIR_OVERRIDE" ]; then
            DERIVED_TAG="$(basename "$DATA_DIR_OVERRIDE")"
        elif [ -n "$FILES_CSV" ]; then
            IFS=',' read -r -a _files_arr <<< "$FILES_CSV"
            first_item="${_files_arr[0]}"
            if [ -f "$first_item" ]; then
                DERIVED_TAG="$(basename "$first_item")"
                DERIVED_TAG="${DERIVED_TAG%.*}"
            elif [ -d "$first_item" ]; then
                DERIVED_TAG="$(basename "$first_item")"
            fi
        fi

        echo -e "${BLUE}Starting Evaluation for: ${DERIVED_TAG}${NC}"
        echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
        echo "Tag: ${DERIVED_TAG}"
        # Compute descriptions list if not provided
        if [ -n "$DESCRIPTIONS" ]; then
            DESCS_TO_USE="$DESCRIPTIONS"
        else
            # Detect from generated files: evaluator/generated_semantic_types/<tag>_descriptionN_*.json
            DETECTED=$(ls -1 "$EVALUATOR_DIR/generated_semantic_types/${DERIVED_TAG}_description"*.json 2>/dev/null \
                | sed -E 's/.*_description([0-9]+)_.*/\1/' \
                | sort -n | uniq | tr '\n' ' ' | sed 's/ *$//')
            if [ -n "$DETECTED" ]; then
                DESCS_TO_USE="$DETECTED"
            else
                DESCS_TO_USE="1 2 3 4 5 6"
            fi
        fi
        echo "Descriptions: $DESCS_TO_USE"
        echo "Mode: $EVAL_MODE"
        echo ""
        
        cd "$PROJECT_ROOT"
        
        # Build the Python command arguments
        PYTHON_ARGS="--dataset ${DERIVED_TAG} --descriptions $DESCS_TO_USE"
        
        if [ "$DEBUG_LOGGING" = true ]; then
            PYTHON_ARGS="$PYTHON_ARGS --log-level DEBUG"
        fi

        # Always run in comparative mode
        PYTHON_ARGS="$PYTHON_ARGS --comparative"
        
        # Run evaluation from the evaluator directory to maintain correct paths
        cd "$EVALUATOR_DIR"
        
        # Run evaluation
        # NEW UNIVERSAL INPUT FLOW: all datasets take file/dir lists.
        # Select evaluator and base dir per dataset
        INPUTS=()
        FILES_JOINED=""
        LIMIT_ARGS="" # handled inside evaluator now (row cap 1000 unless --full-data)

        EVAL_SCRIPT="src/profile_and_evaluate.py"
        BASE_DIR="$DATA_DIR_OVERRIDE"

        # Build inputs from --files (comma-separated). If none, rely on --data-dir directory scan.
        FILES_ARG=""
        if [ -n "$FILES_CSV" ]; then
            IFS=',' read -r -a _files_arr <<< "$FILES_CSV"
            for item in "${_files_arr[@]}"; do
                trimmed=$(echo "$item" | sed 's/^ *//;s/ *$//')
                [ -z "$trimmed" ] && continue
                if [ -f "$trimmed" ] || [ -d "$trimmed" ]; then
                    abs=$(cd "$(dirname "$trimmed")" && pwd)/"$(basename "$trimmed")"
                    INPUTS+=("$abs")
                else
                    # try relative to evaluator dir
                    if [ -f "$EVALUATOR_DIR/$trimmed" ] || [ -d "$EVALUATOR_DIR/$trimmed" ]; then
                        abs=$(cd "$EVALUATOR_DIR/$(dirname "$trimmed")" && pwd)/"$(basename "$trimmed")"
                        INPUTS+=("$abs")
                    fi
                fi
            done
            # No per-dataset file cap here; pass all resolved inputs
            if [ ${#INPUTS[@]} -gt 0 ]; then
                FILES_JOINED=$(IFS=','; echo "${INPUTS[*]}")
            fi
        fi
        # If no explicit inputs, construct data-dir for the dataset tag under data/
        if [ -z "$FILES_CSV" ] && [ -z "$DATA_DIR_OVERRIDE" ]; then
            BASE_DIR="$EVALUATOR_DIR/datasets/data/$DERIVED_TAG"
        fi

        # If we have a BASE_DIR and we haven't built a FILES list, enumerate CSVs under it
        if [ -z "$FILES_JOINED" ] && [ -n "$BASE_DIR" ]; then
            # Portable alternative to 'mapfile' for macOS bash 3.2
            found_files=()
            if [ "$TRUNCATE_DATA" = true ]; then
                while IFS= read -r f; do
                    [ -n "$f" ] && found_files+=("$f")
                done < <(find "$BASE_DIR" -type f -name '*.csv' 2>/dev/null | head -n "$PER_DATASET_MAX_FILES")
            else
                while IFS= read -r f; do
                    [ -n "$f" ] && found_files+=("$f")
                done < <(find "$BASE_DIR" -type f -name '*.csv' 2>/dev/null)
            fi
            if [ ${#found_files[@]} -gt 0 ]; then
                INPUTS=("${found_files[@]}")
                FILES_JOINED=$(IFS=','; echo "${INPUTS[*]}")
                # Clear BASE_DIR so we pass files explicitly and avoid scanning all
                BASE_DIR=""
                if [ "$TRUNCATE_DATA" = true ]; then
                    echo -e "Using ${#INPUTS[@]} file(s) for '${DERIVED_TAG}' (cap: ${PER_DATASET_MAX_FILES})."
                else
                    echo -e "Using ${#INPUTS[@]} file(s) for '${DERIVED_TAG}' (no cap due to --full-data)."
                fi
            else
                echo -e "${YELLOW}No CSV files found under ${BASE_DIR} for '${DERIVED_TAG}'.${NC}"
            fi
        fi

        if [ "$VERBOSE" = true ]; then
            [ ${#INPUTS[@]} -gt 0 ] && echo -e "${BLUE}Running evaluator with inputs:${NC} ${INPUTS[*]}"
            EVALUATOR_API_BASE_URL="http://localhost:$BACKEND_PORT/api" EVALUATOR_RUN_DIR="$RUN_DIR" EVAL_TRUNCATE_DATA=${TRUNCATE_DATA} EVAL_RUN_TIMESTAMP="$EVAL_RUN_TIMESTAMP" \
                bash -lc "\"$VENV_PY\" -u \"$EVAL_SCRIPT\" $PYTHON_ARGS --api-base-url \"http://localhost:$BACKEND_PORT/api\" ${BASE_DIR:+--data-dir \"$BASE_DIR\"} ${FILES_JOINED:+--files \"$FILES_JOINED\"} ${TIMESTAMP:+--timestamp \"$TIMESTAMP\"}"
        else
            EVALUATOR_API_BASE_URL="http://localhost:$BACKEND_PORT/api" EVALUATOR_RUN_DIR="$RUN_DIR" EVAL_TRUNCATE_DATA=${TRUNCATE_DATA} EVAL_RUN_TIMESTAMP="$EVAL_RUN_TIMESTAMP" \
                bash -lc "\"$VENV_PY\" -u \"$EVAL_SCRIPT\" $PYTHON_ARGS --api-base-url \"http://localhost:$BACKEND_PORT/api\" ${BASE_DIR:+--data-dir \"$BASE_DIR\"} ${FILES_JOINED:+--files \"$FILES_JOINED\"} ${TIMESTAMP:+--timestamp \"$TIMESTAMP\"}"
        fi

        # After python run, check for heap error and skip if found (inspect service logs)
        if COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT $COMPOSE -f docker-compose.dev.yml logs --no-color --tail=500 backend | grep -q "java.lang.OutOfMemoryError"; then
            echo -e "${RED}Java heap error detected during evaluation of $DERIVED_TAG. Skipping.${NC}"
            # Optionally remove partial results
            rm -f "$RUN_DIR/${DERIVED_TAG}_profile_results_"*.json 2>/dev/null
            continue
        fi
        
        # Check if results were created
        # Results are written into logs/<RUN_TS>
        LATEST_RESULT=$(ls -t "$RUN_DIR/${DERIVED_TAG}_profile_results_"*.json 2>/dev/null | head -1)
        if [ -n "$LATEST_RESULT" ]; then
            echo ""
            echo -e "${GREEN}✔${NC} Evaluation completed successfully: '${DERIVED_TAG}'${NC}"
            echo -e "Results saved to: ${BLUE}$LATEST_RESULT${NC}"
        fi

        # Check for heap errors after full run (inspect service logs)
        if COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT $COMPOSE -f docker-compose.dev.yml logs --no-color --tail=500 backend | grep -q "java.lang.OutOfMemoryError"; then
            echo -e "${RED}Java heap error detected in logs. Please increase JVM heap with --heap (e.g., --heap 4g).${NC}"
            # Mark the run as failed or partial
        fi
    done

    # Generate final cross-dataset summary (best description per dataset) and print/save it
    echo ""
    echo -e "${BLUE}Generating final summary across datasets...${NC}"
    "$VENV_PY" - << 'PY'
import os, sys, json, re, csv
from glob import glob

project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
evaluator_dir = os.path.join(project_root, 'evaluator')
run_ts = os.environ.get('EVAL_RUN_TIMESTAMP')
if not run_ts:
    print('No EVAL_RUN_TIMESTAMP set; skipping summary.', file=sys.stderr)
    sys.exit(0)

# Gather result JSONs from logs/<run_ts>
logs_run_dir = os.path.join(evaluator_dir, 'logs', run_ts)
files = []
files += glob(os.path.join(logs_run_dir, '*_profile_results_*.json')) if os.path.isdir(logs_run_dir) else []

if not files:
    print('No per-description results found; skipping summary.')
    sys.exit(0)

metric_priority = [
    'overall_score','accuracy','macro_f1','micro_f1','f1','f1_score',
    'precision','recall','map','mrr','ndcg'
]

def extract_dataset_and_desc(path):
    # Expecting <dataset>_profile_results_<desc>.json
    base = os.path.basename(path)
    m = re.match(r'(.+?)_profile_results_(\d+)\.json$', base)
    if m:
        return m.group(1), m.group(2)
    # Fallbacks
    name = os.path.splitext(base)[0]
    parts = name.split('_profile_results_')
    if len(parts) == 2:
        return parts[0], parts[1]
    return name, 'unknown'

def numeric_items(d):
    for k, v in d.items():
        if isinstance(v, (int, float)) and not isinstance(v, bool):
            yield k, float(v)

def flatten_metrics(obj):
    # Collect numeric metrics from top-level and common sub-containers
    out = {}
    if isinstance(obj, dict):
        for k, v in numeric_items(obj):
            out[k] = v
        for sub in ('metrics','summary','scores','statistics','eval','evaluation','results'):
            if sub in obj and isinstance(obj[sub], dict):
                for k, v in numeric_items(obj[sub]):
                    out.setdefault(k, v)
    return out

by_dataset = {}

for fp in files:
    try:
        with open(fp, 'r') as f:
            data = json.load(f)
    except Exception:
        continue
    results = data.get('results', {}) if isinstance(data, dict) else {}
    dataset = results.get('dataset') or data.get('dataset')
    if dataset is None:
        ds, _ = extract_dataset_and_desc(fp)
        dataset = ds
    if not dataset:
        continue
    entry = by_dataset.get(dataset) or {'dataset': dataset, 'baseline': None, 'best': None}
    # Capture baseline
    baseline = results.get('baseline')
    if isinstance(baseline, dict):
        base_metrics = {k: float(v) for k, v in baseline.items() if isinstance(v, (int, float)) and not isinstance(v, bool)}
        if base_metrics:
            entry['baseline'] = base_metrics
    # Consider candidates from custom evaluations (and baseline as candidate for best if desired)
    candidates = []
    custom = results.get('custom_evaluations')
    if isinstance(custom, dict):
        for desc_key, desc_val in custom.items():
            if isinstance(desc_val, dict) and isinstance(desc_val.get('metrics'), dict):
                m = {k: float(v) for k, v in desc_val['metrics'].items() if isinstance(v, (int, float)) and not isinstance(v, bool)}
                if m:
                    candidates.append((desc_key, m))
    # Evaluate candidates for best
    for desc_name, metrics in candidates:
        # Choose primary metric
        p_name, p_val = None, None
        for name in metric_priority:
            if name in metrics:
                p_name, p_val = name, metrics[name]
                break
        if p_name is None:
            items = sorted(metrics.items())
            if items:
                p_name, p_val = items[0]
        if p_name is None:
            continue
        best = entry['best']
        if best is None or p_val > best['primary_value']:
            entry['best'] = {
                'description': desc_name,
                'primary_metric': p_name,
                'primary_value': p_val,
                'metrics': metrics,
            }
    by_dataset[dataset] = entry

# Always require both baseline and best results for comparative analysis
finalized = {}
for ds, entry in by_dataset.items():
    best = entry.get('best')
    baseline = entry.get('baseline')
    finalized[ds] = {
        'dataset': ds,
        'baseline': baseline,
        'best': best,
    }

if not finalized:
    print('No usable metrics found; skipping summary.')
    sys.exit(0)

# Always use comparative mode: show custom best metrics with deltas vs baseline
friendly_columns = [
    'Dataset','Best Description',
    'Accuracy','Δ Accuracy','F1','Δ F1','Precision','Δ Precision','Recall','Δ Recall'
]

def metric_val(rec, key):
    return rec['metrics'].get(key) if rec and rec.get('metrics') else None

rows = []
# Always use comparative mode: show custom best metrics with deltas vs baseline
for ds, rec in sorted(finalized.items()):
    base = rec.get('baseline') or {}
    best = rec.get('best')

    # Require both baseline and best custom results for comparative analysis
    if not best or not base:
        continue

    best_metrics = best['metrics']
    acc = best_metrics.get('accuracy')
    f1v = best_metrics.get('f1_score', best_metrics.get('f1'))
    prec = best_metrics.get('precision')
    recv = best_metrics.get('recall')

    # Calculate deltas for comparative analysis
    acc_d = (acc - base.get('accuracy')) if (isinstance(acc, (int,float)) and isinstance(base.get('accuracy'), (int,float))) else None
    f1b = base.get('f1_score', base.get('f1'))
    f1_d = (f1v - f1b) if (isinstance(f1v, (int,float)) and isinstance(f1b, (int,float))) else None
    prec_d = (prec - base.get('precision')) if (isinstance(prec, (int,float)) and isinstance(base.get('precision'), (int,float))) else None
    rec_d = (recv - base.get('recall')) if (isinstance(recv, (int,float)) and isinstance(base.get('recall'), (int,float))) else None

    rows.append({
        'Dataset': rec['dataset'],
        'Best Description': best['description'],
        'Accuracy': acc,
        'Δ Accuracy': acc_d,
        'F1': f1v,
        'Δ F1': f1_d,
        'Precision': prec,
        'Δ Precision': prec_d,
        'Recall': recv,
        'Δ Recall': rec_d,
    })

def fmt(v):
    if isinstance(v, float):
        return f"{v:.6f}".rstrip('0').rstrip('.')
    return str(v) if v is not None else ''

# Console pretty table
print('\nFinal summary (best metrics per dataset):')
widths = [len(h) for h in friendly_columns]
pretty_rows = []
for r in rows:
    vals = [fmt(r.get(col)) for col in friendly_columns]
    pretty_rows.append(vals)
    for i, v in enumerate(vals):
        widths[i] = max(widths[i], len(str(v)))

def line(l, m, r, fill='─'):
    return l + m.join(fill * (w + 2) for w in widths) + r

print(line('┌','┬','┐'))
print('│ ' + ' │ '.join(friendly_columns[i].ljust(widths[i]) for i in range(len(widths))) + ' │')
print(line('├','┼','┤'))
for vals in pretty_rows:
    print('│ ' + ' │ '.join(str(vals[i]).ljust(widths[i]) for i in range(len(vals))) + ' │')
print(line('└','┴','┘'))

# Persist friendly CSV/MD into logs/<run_ts>
os.makedirs(logs_run_dir, exist_ok=True)
csv_path = os.path.join(logs_run_dir, 'final_summary.csv')
md_path = os.path.join(logs_run_dir, 'final_summary.md')
with open(csv_path, 'w', newline='') as f:
    w = csv.writer(f)
    w.writerow(friendly_columns)
    for vals in pretty_rows:
        w.writerow(vals)
with open(md_path, 'w') as f:
    f.write('| ' + ' | '.join(friendly_columns) + ' |\n')
    f.write('| ' + ' | '.join(['---']*len(friendly_columns)) + ' |\n')
    for vals in pretty_rows:
        f.write('| ' + ' | '.join(vals) + ' |\n')

print(f"\nSaved summary to:\n - {csv_path}\n - {md_path}")
PY

    # If running under GitHub Actions, append the Markdown table to the job step summary
    if [ -n "$GITHUB_STEP_SUMMARY" ] && [ -f "$EVALUATOR_DIR/logs/$EVAL_RUN_TIMESTAMP/final_summary.md" ]; then
        {
            echo "# NL2FTA Evaluation Summary"
            echo ""
            echo "Run: $EVAL_RUN_TIMESTAMP"
            echo ""
            cat "$EVALUATOR_DIR/logs/$EVAL_RUN_TIMESTAMP/final_summary.md"
        } >> "$GITHUB_STEP_SUMMARY"
        echo -e "${GREEN}✔${NC} Wrote summary table to GitHub Step Summary"
    fi

    # After evaluation, check for heap errors (inspect service logs)
    if COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT $COMPOSE -f docker-compose.dev.yml logs --no-color --tail=500 backend | grep -q "java.lang.OutOfMemoryError"; then
        echo -e "${RED}Java heap error detected. Skipping this eval.${NC}"
        # Skip logic here
    fi

    # No temp file to clean up
}

# Print the final summary table again at the very end so it is the last thing shown
print_final_summary_table() {
    "$VENV_PY" - << 'PY'
import os, sys, json, re
from glob import glob

project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
evaluator_dir = os.path.join(project_root, 'evaluator')
run_ts = os.environ.get('EVAL_RUN_TIMESTAMP')
if not run_ts:
    sys.exit(0)

logs_run_dir = os.path.join(evaluator_dir, 'logs', run_ts)
files = []
files += glob(os.path.join(logs_run_dir, '*_profile_results_*.json')) if os.path.isdir(logs_run_dir) else []
if not files:
    sys.exit(0)

def extract_dataset_and_desc(path):
    base = os.path.basename(path)
    m = re.match(r'(.+?)_profile_results_(\d+)\.json$', base)
    if m:
        return m.group(1), m.group(2)
    name = os.path.splitext(base)[0]
    parts = name.split('_profile_results_')
    if len(parts) == 2:
        return parts[0], parts[1]
    return name, 'unknown'

def numeric_items(d):
    for k, v in d.items():
        if isinstance(v, (int, float)) and not isinstance(v, bool):
            yield k, float(v)

def flatten_metrics(obj):
    out = {}
    if isinstance(obj, dict):
        for k, v in numeric_items(obj):
            out[k] = v
        for sub in ('metrics','summary','scores','statistics','eval','evaluation','results'):
            if sub in obj and isinstance(obj[sub], dict):
                for k, v in numeric_items(obj[sub]):
                    out.setdefault(k, v)
    return out

metric_priority = ['overall_score','accuracy','macro_f1','micro_f1','f1','f1_score','precision','recall','map','mrr','ndcg']

by_dataset = {}
for fp in files:
    try:
        with open(fp, 'r') as f:
            data = json.load(f)
    except Exception:
        continue
    ds, desc = extract_dataset_and_desc(fp)
    metrics = flatten_metrics(data)
    if not metrics:
        continue
    primary_name = None
    primary_value = None
    for name in metric_priority:
        if name in metrics:
            primary_name = name
            primary_value = metrics[name]
            break
    if primary_name is None:
        items = sorted(metrics.items())
        if items:
            primary_name, primary_value = items[0]
    if primary_name is None:
        continue
    prev = by_dataset.get(ds)
    if not prev or primary_value > prev['primary_value']:
        by_dataset[ds] = {'dataset': ds, 'description': desc, 'primary_metric': primary_name, 'primary_value': primary_value, 'metrics': metrics}

if not by_dataset:
    sys.exit(0)

def fmt(v):
    if isinstance(v, float):
        return f"{v:.6f}".rstrip('0').rstrip('.')
    return str(v) if v is not None else ''

cols = ['Dataset','Best Description','Primary Metric','Primary Value','Accuracy','Δ Accuracy','F1','Δ F1','Precision','Δ Precision','Recall','Δ Recall']
rows = []

# Try to load per-dataset summary if available to compute deltas; otherwise show primary only
# This fallback prints primary metrics when baseline is not easily accessible here
for ds, rec in sorted(by_dataset.items()):
    m = rec['metrics']
    f1 = m.get('f1_score', m.get('f1'))
    rows.append([
        rec['dataset'], rec['description'], rec['primary_metric'], fmt(rec['primary_value']),
        fmt(m.get('accuracy')), '', fmt(f1), '', fmt(m.get('precision')), '', fmt(m.get('recall')), ''
    ])

widths = [len(h) for h in cols]
for r in rows:
    for i, v in enumerate(r):
        widths[i] = max(widths[i], len(str(v)))

def line(l, m, r, fill='─'):
    return l + m.join(fill * (w + 2) for w in widths) + r

print('\nFinal summary (best metrics per dataset):')
print(line('┌','┬','┐'))
print('│ ' + ' │ '.join(cols[i].ljust(widths[i]) for i in range(len(widths))) + ' │')
print(line('├','┼','┤'))
for r in rows:
    print('│ ' + ' │ '.join(str(r[i]).ljust(widths[i]) for i in range(len(r))) + ' │')
print(line('└','┴','┘'))
PY
}

calculate_f1_sum() {
    if [ "$SUM_F1_MODE" != true ]; then
        return 0
    fi
    
    echo ""
    echo -e "${BLUE}Calculating F1 Score Sum...${NC}"
    
    "$VENV_PY" - << 'PY'
import os, sys, json, re
from glob import glob

# Configuration - derive evaluator dir from run dir or fallback
run_dir = os.environ.get('EVALUATOR_RUN_DIR')
if run_dir and os.path.exists(run_dir):
    # EVALUATOR_RUN_DIR points to logs/timestamp, go up two levels to get evaluator dir
    evaluator_dir = os.path.dirname(os.path.dirname(run_dir))
else:
    # Fallback: assume we're running from project root
    cwd = os.getcwd()
    if cwd.endswith('scripts'):
        project_root = os.path.dirname(cwd)
    else:
        project_root = cwd
    evaluator_dir = os.path.join(project_root, 'evaluator')
run_ts = os.environ.get('EVAL_RUN_TIMESTAMP')

if not run_ts:
    print('No EVAL_RUN_TIMESTAMP set; cannot calculate F1 sum.', file=sys.stderr)
    sys.exit(1)

# Expected datasets and descriptions  
expected_datasets = ["extension", "transactions", "banking", "insurance", "telco_5GTraffic", "telco_customer_churn", "semtab"]
expected_descriptions = ["1", "2", "3", "4", "5", "6"]

# Gather result JSONs from logs/<run_ts>
logs_run_dir = os.path.join(evaluator_dir, 'logs', run_ts)
if not os.path.isdir(logs_run_dir):
    print(f'Run directory not found: {logs_run_dir}', file=sys.stderr)
    sys.exit(1)

result_files = glob(os.path.join(logs_run_dir, '*_profile_results_*.json'))

def extract_dataset_from_filename(filename):
    """Extract dataset from filename"""
    base = os.path.basename(filename)
    # Pattern: <dataset>_profile_results_<timestamp>.json
    match = re.match(r'(.+?)_profile_results_.+\.json$', base)
    if match:
        return match.group(1)
    return None

# Build matrix: dataset -> description -> f1_score
f1_matrix = {}
for dataset in expected_datasets:
    f1_matrix[dataset] = {}
    for desc in expected_descriptions:
        f1_matrix[dataset][desc] = None

# Process each result file
processed_count = 0
for filepath in result_files:
    dataset = extract_dataset_from_filename(filepath)
    
    if not dataset or dataset not in expected_datasets:
        continue
    
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)
        
        # Navigate to results -> results and process all descriptions in this file
        results = data.get('results', {})
        inner_results = results.get('results', {})
        
        # Process each description (1-6) in this dataset file
        for desc in expected_descriptions:
            desc_key = f"description_{desc}_custom_only"
            
            if desc_key in inner_results:
                desc_data = inner_results[desc_key]
                f1_score = desc_data.get('f1_score')
                
                if isinstance(f1_score, (int, float)):
                    f1_matrix[dataset][desc] = float(f1_score)
                    processed_count += 1
    
    except Exception as e:
        print(f'Warning: Failed to process {filepath}: {e}', file=sys.stderr)
        continue

# Calculate statistics
total_combinations = len(expected_datasets) * len(expected_descriptions)
found_combinations = sum(1 for dataset_scores in f1_matrix.values() 
                        for score in dataset_scores.values() 
                        if score is not None)
f1_sum = sum(score for dataset_scores in f1_matrix.values() 
             for score in dataset_scores.values() 
             if score is not None)
avg_f1 = f1_sum / found_combinations if found_combinations > 0 else 0

# Print results
print()
print("╔═══════════════════════════════════════╗")
print("║           F1 Score Summary            ║") 
print("╚═══════════════════════════════════════╝")
print()
print(f"Total F1 Sum: {f1_sum:.6f} ({found_combinations}/{total_combinations} combinations)")
print(f"Average F1:   {avg_f1:.6f}")
print(f"Max possible: {total_combinations:.1f}") 
print(f"Note: Now includes semtab dataset (7 datasets × 6 descriptions = 42 combinations)")
print()

# Dataset breakdown
print("Dataset breakdown:")
for dataset in expected_datasets:
    dataset_scores = [f1_matrix[dataset][desc] for desc in expected_descriptions 
                     if f1_matrix[dataset][desc] is not None]
    dataset_sum = sum(dataset_scores) if dataset_scores else 0
    found_count = len(dataset_scores)
    print(f"• {dataset:<20} {dataset_sum:.6f} ({found_count}/6)")

# Missing combinations warning
missing = []
for dataset in expected_datasets:
    for desc in expected_descriptions:
        if f1_matrix[dataset][desc] is None:
            missing.append(f"{dataset}/description_{desc}")

if missing:
    print()
    print(f"⚠️  Missing combinations ({len(missing)}):")
    for combo in missing[:10]:  # Show first 10
        print(f"   - {combo}")
    if len(missing) > 10:
        print(f"   ... and {len(missing) - 10} more")

# Exit with appropriate code
sys.exit(0 if found_combinations == total_combinations else 1)
PY
}

cleanup_backend() {
    if [ "$CLEANUP" = true ]; then
        echo ""
        echo -e "${YELLOW}Stopping evaluation backend...${NC}"
        cd "$PROJECT_ROOT"
        COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT \
        AUTH_PASSWORD=${AUTH_PASSWORD:-eval-password-2024} \
        JWT_SECRET=${JWT_SECRET:-eval-secret-minimum-32-characters-long-2024} \
        AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID:-DUMMY_KEY_FOR_EVAL_ONLY} \
        AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY:-DUMMY_SECRET_FOR_EVAL_ONLY} \
            $COMPOSE -f docker-compose.dev.yml down
        echo -e "${GREEN}✔${NC} Cleanup complete"
    else
        echo ""
        echo -e "${YELLOW}ℹ️  Backend left running on port $BACKEND_PORT${NC}"
        echo "To stop it manually, run:"
        echo "  COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT $COMPOSE -f docker-compose.dev.yml down"
    fi
}

# Error handler
trap 'cleanup_backend' ERR

# Main execution
echo -e "${BLUE}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║               NL2FTA Evaluation Suite                            ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""

if [ "$DEFAULT_MODE" = true ]; then
    echo -e "${YELLOW}📝 Using default Chainguard authentication (no org-specific config)${NC}"
fi

check_prerequisites
setup_chainguard "$DEFAULT_MODE"

# Rebuild backend before starting
echo -e "${YELLOW}Skipping host Gradle build; using Dockerized backend (Dockerfile.dev) for evaluation...${NC}"
cd "$PROJECT_ROOT"

start_backend
run_evaluation
cleanup_backend

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Evaluation complete!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"

# Re-print the final summary so it is the very last thing in the output
if [ "$SUM_F1_MODE" = true ]; then
    calculate_f1_sum
else
    print_final_summary_table
fi
