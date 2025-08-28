#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "Stopping FTA Classifier Services..."

# Function to run docker compose with fallback
docker_compose() {
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

# Stop Docker containers
docker_compose -f "$SCRIPT_DIR/../docker-compose.dev.yml" down 2>/dev/null
docker_compose -f "$SCRIPT_DIR/../docker-compose.prod.yml" down 2>/dev/null

# Clean up orphaned containers
# Use a cross-platform approach (xargs -r is Linux-only)
containers=$(docker ps -a | grep -E "(frontend|backend)" | awk '{print $1}')
if [ ! -z "$containers" ]; then
    echo "$containers" | xargs docker rm -f 2>/dev/null || true
fi

echo "✅ All services stopped" 