#!/bin/bash

# Parse command line arguments
DEFAULT_MODE=false
for arg in "$@"; do
    case $arg in
        --default)
            DEFAULT_MODE=true
            shift
            ;;
        *)
            # Unknown argument, pass through
            ;;
    esac
done

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "🚀 Starting FTA Classifier Services in development mode..."
if [ "$DEFAULT_MODE" = true ]; then
    echo "📝 Using default Chainguard authentication (no org-specific config)"
fi

# Load environment variables from .env file
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Load .env file if it exists (check both locations for backward compatibility)
if [ -f "$PROJECT_ROOT/backend/.env" ]; then
    echo "📄 Loading environment variables from backend/.env file..."
    set -a
    source "$PROJECT_ROOT/backend/.env"
    set +a
elif [ -f "$PROJECT_ROOT/.env" ]; then
    echo "📄 Loading environment variables from .env file..."
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
    
    # Map AWS credentials if they exist with the old names
    if [ ! -z "$AWS_CREDENTIALS_ACCESS_KEY_ID" ] && [ -z "$AWS_ACCESS_KEY_ID" ]; then
        export AWS_ACCESS_KEY_ID="$AWS_CREDENTIALS_ACCESS_KEY_ID"
    fi
    if [ ! -z "$AWS_CREDENTIALS_SECRET_ACCESS_KEY" ] && [ -z "$AWS_SECRET_ACCESS_KEY" ]; then
        export AWS_SECRET_ACCESS_KEY="$AWS_CREDENTIALS_SECRET_ACCESS_KEY"
    fi
fi

# Check environment variables
echo "🔍 Checking environment variables..."

MISSING_VARS=()

# Check required variables
if [ -z "$AUTH_PASSWORD" ]; then
    MISSING_VARS+=("AUTH_PASSWORD")
fi

if [ -z "$JWT_SECRET" ]; then
    MISSING_VARS+=("JWT_SECRET")
fi

# Check JWT_SECRET length
if [ ! -z "$JWT_SECRET" ] && [ ${#JWT_SECRET} -lt 32 ]; then
    echo -e "\033[1;33m⚠️  WARNING: JWT_SECRET should be at least 32 characters for security\033[0m"
fi

# Check optional AWS variables (for full functionality)
if [ ! -z "$AWS_ACCESS_KEY_ID" ] && [ ! -z "$AWS_SECRET_ACCESS_KEY" ]; then
    # Check if they're still placeholder values
    if [ "$AWS_ACCESS_KEY_ID" = "your-aws-access-key-here" ] || [ "$AWS_SECRET_ACCESS_KEY" = "your-aws-secret-key-here" ]; then
        echo -e "\033[1;33m⚠️  AWS credentials have placeholder values - update for full functionality\033[0m"
    else
        AWS_CONFIGURED=true
    fi
else
    AWS_CONFIGURED=false
fi

# Report results
if [ ${#MISSING_VARS[@]} -eq 0 ]; then
    echo -e "\033[0;32m✅ All required environment variables are set\033[0m"
    
    if [ "$AWS_CONFIGURED" = true ]; then
        echo -e "\033[0;32m✅ AWS credentials are configured - full functionality available\033[0m"
    else
        echo -e "\033[1;33mℹ️  AWS credentials not configured - credentials will be autofilled with placeholder values in the frontend\033[0m"
    fi
else
    echo -e "\033[0;31m❌ Missing required environment variables:\033[0m"
    for var in "${MISSING_VARS[@]}"; do
        echo -e "\033[0;31m   - $var\033[0m"
    done
    echo ""
    echo "Please set these in your .env file:"
    echo "  export AUTH_PASSWORD='your-secure-password-for-the-password-page'"
    echo "  export JWT_SECRET='your-secure-jwt-secret-min-32-chars'"
    echo "  export AWS_ACCESS_KEY_ID='your-aws-access-key'"
    echo "  export AWS_SECRET_ACCESS_KEY='your-aws-secret-key'"
    exit 1
fi

# Export AWS credentials for Docker Compose
export AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY
export AWS_DEFAULT_REGION

# Export AUTH_PASSWORD and JWT_SECRET for docker-compose
export AUTH_PASSWORD
export JWT_SECRET

echo "Updating submodules..."
if command -v git >/dev/null 2>&1; then
    git submodule update --init --recursive || true
else
    echo "git not found; skipping submodule update"
fi

# Function to check if Docker is running
check_docker() {
    if ! docker info >/dev/null 2>&1; then
        echo ""
        echo "❌ Docker daemon is not running!"
        echo ""
        echo "Please start Docker and try again."
        echo "To install Docker (Amazon Linux), follow:"
        echo "https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-docker.html#install-docker-instructions"
        echo ""
        exit 1
    fi
    echo "✅ Docker daemon is running"
}

# Function to run docker compose with fallback
docker_compose() {
    # Try docker compose (v2) first as it's the current standard
    if docker compose version &> /dev/null; then
        docker compose "$@"
    elif command -v docker-compose &> /dev/null; then
        docker-compose "$@"
    else
        echo "Error: Neither 'docker compose' nor 'docker-compose' found"
        echo "To install docker-compose (Linux), run:"
        echo "sudo curl -L https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m) -o /usr/bin/docker-compose && sudo chmod 755 /usr/bin/docker-compose && docker-compose --version"
        exit 1
    fi
}

# Function to install/update chainctl
install_chainctl() {
    echo ""
    echo "Installing chainctl..."
    
    # Ensure user bin directory exists
    mkdir -p ~/.local/bin
    
    # Add ~/.local/bin to PATH if not already there
    if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
        export PATH="$HOME/.local/bin:$PATH"
        echo "Added ~/.local/bin to PATH for this session"
    fi
    
    # Platform-agnostic curl download per Chainguard docs
    if ! command -v curl >/dev/null 2>&1; then
        echo "❌ curl is required to install chainctl"
        exit 1
    fi
    BIN_OS=$(uname -s | tr '[:upper:]' '[:lower:]')
    BIN_ARCH=$(uname -m | sed 's/aarch64/arm64/')
    TMP_CHAINCTL=$(mktemp -t chainctl.XXXXXX)
    if ! curl -fsSL -o "$TMP_CHAINCTL" "https://dl.enforce.dev/chainctl/latest/chainctl_${BIN_OS}_${BIN_ARCH}"; then
        echo "❌ Failed to download chainctl binary"
        rm -f "$TMP_CHAINCTL"
        exit 1
    fi
    chmod 0755 "$TMP_CHAINCTL"
    if command -v sudo >/dev/null 2>&1; then
        sudo install -o $UID -g $(id -g) -m 0755 "$TMP_CHAINCTL" /usr/local/bin/chainctl >/dev/null 2>&1 || true
    fi
    if ! command -v chainctl >/dev/null 2>&1; then
        install -m 0755 "$TMP_CHAINCTL" "$HOME/.local/bin/chainctl"
        if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
            export PATH="$HOME/.local/bin:$PATH"
            echo "Added ~/.local/bin to PATH for this session"
        fi
    fi
    rm -f "$TMP_CHAINCTL"
    echo "✅ chainctl installed"
}

# Function to check and setup Chainguard authentication
setup_chainguard() {
    local use_default="${1:-false}"
    echo ""
    echo "Setting up Chainguard authentication..."
    # Enforce headless behavior and disable browser
    export CHAINCTL_HEADLESS=1
    export NO_BROWSER=1
    export BROWSER=""
    
    # Check if chainctl exists, install if not
    if ! command -v chainctl &> /dev/null; then
        install_chainctl
    else
        echo "✅ chainctl is already installed"
    fi
    
    # Always run headless device login and (re)configure docker helper to avoid hangs in status
    echo ""
    echo "Non-interactive Chainguard setup (headless)..."
    mkdir -p ~/.docker
    echo "If authentication is required, a device login URL and code will print below."
    # Headless login prints a one-time URL; do not suppress output
    if [ "$use_default" = true ]; then
        chainctl auth login --headless || true
    else
        chainctl auth login --headless --org-name precisely.com || true
    fi
    # Configure Docker credential helper (retry with sudo on permission errors)
    CFG_STATUS=0
    if [ "$use_default" = true ]; then
        chainctl auth configure-docker --headless || CFG_STATUS=$?
    else
        chainctl auth configure-docker --headless --org-name precisely.com || CFG_STATUS=$?
    fi
    if [ $CFG_STATUS -ne 0 ] || ! command -v docker-credential-cgr >/dev/null 2>&1; then
        if command -v sudo >/dev/null 2>&1; then
            echo "Attempting to fix credential helper with elevated privileges..."
            if [ "$use_default" = true ]; then
                sudo chainctl auth configure-docker --headless || true
            else
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
                echo "⚠️  sudo not available to fix docker-credential-cgr in /usr/local/bin. Run as root:"
                echo "ln -sf $(command -v chainctl) /usr/local/bin/docker-credential-cgr || install -o root -g root -m 0755 $(command -v chainctl) /usr/local/bin/docker-credential-cgr"
            fi
        fi
    fi
    if chainctl auth status 2>/dev/null | grep -q "Valid.*True"; then
        echo "✅ Chainguard authentication is ready"
    else
        echo "⚠️  Chainguard headless auth may need completion in another terminal (device code flow). Continuing; image pulls may fail if not completed."
    fi
}

# Function to cleanup existing services
cleanup() {
    echo ""
    echo "Stopping any existing services..."
    
    # Stop containers from both compose files
    if [ -f "$SCRIPT_DIR/../docker-compose.dev.yml" ]; then
        docker_compose -f "$SCRIPT_DIR/../docker-compose.dev.yml" down --remove-orphans 2>/dev/null || true
    fi
    
    if [ -f "$SCRIPT_DIR/../docker-compose.prod.yml" ]; then
        docker_compose -f "$SCRIPT_DIR/../docker-compose.prod.yml" down --remove-orphans 2>/dev/null || true
    fi
    
    echo "✅ Cleanup completed"
}

# Check Docker
check_docker

# Stop any existing services
cleanup

# Setup Chainguard (interactive); allow CI to bypass and rely on preconfigured server creds
if [ "$CI" = "true" ]; then
  echo "CI environment detected; skipping chainctl interactive setup. Ensure server has Docker creds for cgr.dev."
else
  setup_chainguard "$DEFAULT_MODE"
fi

# Start services
echo ""
echo "🚀 Starting services in development mode..."
echo "🔨 Building fresh images (no cache)..."
docker_compose -f "$SCRIPT_DIR/../docker-compose.dev.yml" build --no-cache
echo "✅ Build complete"
echo ""
echo "🚀 Starting containers..."
docker_compose -f "$SCRIPT_DIR/../docker-compose.dev.yml" up -d

# Wait for services
echo ""
echo "⏳ Waiting for services to start..."
sleep 30

# Check if services are running
if docker_compose -f "$SCRIPT_DIR/../docker-compose.dev.yml" ps | grep -q "Up"; then
    echo ""
    echo "✅ Services started successfully!"
    echo ""
    echo "🌐 Frontend: http://localhost:4200"
    echo "🔧 Backend API: http://localhost:8081"
    echo "📚 API Documentation: http://localhost:8081/swagger-ui/index.html"
    echo "🔄 Hot reload is enabled for both frontend and backend"
    echo ""
    echo "To view logs: docker_compose -f $SCRIPT_DIR/../docker-compose.dev.yml logs -f"
    echo "To stop services: $SCRIPT_DIR/stop-services.sh"
else
    echo "❌ Failed to start services. Check logs with: docker_compose -f $SCRIPT_DIR/../docker-compose.dev.yml logs"
    exit 1
fi 