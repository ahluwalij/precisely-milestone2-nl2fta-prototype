#!/bin/bash

echo "📦 Creating NL2FTA Client Package"
echo "================================="
echo ""

# Check if we're in the right directory (backend must exist at repo root)
if [ ! -d "backend" ]; then
    echo "❌ Error: Please run this script from the root of the NL2FTA repository"
    exit 1
fi

# Check if frontend submodule is initialized
if [ ! -f "frontend/package.json" ]; then
    echo "⚠️  Frontend submodule not initialized. Initializing now..."
    git submodule update --init --recursive
    if [ $? -ne 0 ]; then
        echo "❌ Error: Failed to initialize frontend submodule"
        echo "Make sure you have access to the frontend repository"
        exit 1
    fi
fi

# Create temp directory
TEMP_DIR="nl2fta-milestone3"
rm -rf $TEMP_DIR
mkdir -p $TEMP_DIR

echo "📋 Copying repository files..."

# Copy all files except .git directories and deployment infrastructure
rsync -av --progress \
    --exclude='.git' \
    --exclude='.gitignore' \
    --exclude='.cursor/' \
    --exclude='.vscode/' \
    --exclude='.gitmodules' \
    --exclude='.gitattributes' \
    --exclude='*.log' \
    --exclude='node_modules' \
    --exclude='dist' \
    --exclude='build' \
    --exclude='.angular' \
    --exclude='.env' \
    --exclude='.venv-nl2fta' \
    --exclude='*.env' \
    --exclude='.env.*' \
    --exclude='.aws-credentials' \
    --exclude='*.credentials' \
    --exclude='credentials.*' \
    --exclude='*secret*' \
    --exclude='*.pem' \
    --exclude='*key*' \
    --exclude='test_products.csv' \
    --exclude='text.csv' \
    --exclude='test.csv' \
    --exclude='coverage' \
    --exclude='nl2fta-milestone3' \
    --exclude='nl2fta-deployment-*.zip' \
    --exclude='nl2fta-milestone3.zip' \
    --exclude='*/nl2fta-milestone3.zip' \
    --exclude='test_logs/' \
    --exclude='deployment/' \
    --exclude='.github/' \
    --exclude='infrastructure/' \
    --exclude='backend/.gradle/' \
    --exclude='backend/build/' \
    --exclude='backend/.aws-credentials' \
    --exclude='backend/config/custom-semantic-types.json' \
    --exclude='custom-semantic-types.json' \
    --exclude='backend/sbom.spdx.json' \
    --exclude='frontend/sbom.spdx.json' \
    --exclude='.codecov.yml' \
    --exclude='evaluator/.env' \
    --exclude='evaluator/venv/' \
    --exclude='evaluator/venv310/' \
    --exclude='evaluator/__pycache__' \
    --exclude='evaluator/results/' \
    --exclude='evaluator/results_x/' \
    --exclude='evaluator/archive/' \
    --exclude='evaluator/*.log' \
    --exclude='evaluator/.claude/' \
    --exclude='fta/' \
    --exclude='nl2fta-key.pem' \
    --exclude='.claude/' \
    --exclude='FIX_IMPLEMENTATION_PLAN.md' \
    --exclude='FTA_LIMITATIONS_AND_ISSUES.md' \
    --exclude='FTA_LIST_TYPE_REQUIREMENTS.md' \
    . $TEMP_DIR/

# Ensure frontend is fully copied
if [ -d "frontend" ]; then
    echo ""
    echo "📋 Ensuring frontend files are included..."
    # Keep .git for the main repo but remove frontend submodule .git
    # This prevents submodule issues while maintaining Git LFS support
    rm -rf $TEMP_DIR/frontend/.git
    rm -f $TEMP_DIR/frontend/.gitmodules
fi


# Remove setup-submodule-access.sh as it's not needed for clients
rm -f $TEMP_DIR/setup-submodule-access.sh

# Create safe .env.example files
echo ""
echo "📋 Creating safe environment templates..."

# Create root .env.example without credentials
cat > $TEMP_DIR/.env.example << 'EOF'
# Environment Variables for NL2FTA Application
# Copy this to .env and fill in your values

# Set AUTH_PASSWORD to your secure password (MUST BE 8 CHARACTERS OR MORE)
AUTH_PASSWORD=your-secure-password-for-the-password-page

JWT_SECRET=your-secure-jwt-secret-min-32-chars

# User AWS Credentials - Add your own AWS credentials here
AWS_ACCESS_KEY_ID=your-aws-access-key-id
AWS_SECRET_ACCESS_KEY=your-aws-secret-access-key

# Admin AWS Configuration for CloudWatch Logging (optional)
AWS_ADMIN_ACCESS_KEY_ID=your-admin-access-key-id
AWS_ADMIN_SECRET_ACCESS_KEY=your-admin-secret-access-key
AWS_ADMIN_REGION=us-east-1
AWS_CLOUDWATCH_LOG_GROUP=/aws/nl2fta
AWS_CLOUDWATCH_LOG_STREAM=application-logs

# Logging Configuration
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_NL2FTA=INFO

# JVM Options
JAVA_OPTS="-Xmx2g -Xms512m"

# Local Development URLs
CORS_ALLOWED_ORIGINS="http://localhost:4200,http://localhost:4000,http://localhost:3000,http://localhost:80,http://localhost"
VITE_API_BASE_URL=http://localhost:8081
EOF

# Create evaluator .env.example without credentials
cat > $TEMP_DIR/evaluator/.env.example << 'EOF'
# AWS Credentials for Bedrock access (optional)
AWS_ACCESS_KEY_ID=your-aws-access-key-id
AWS_SECRET_ACCESS_KEY=your-aws-secret-access-key
AWS_REGION=us-east-1

# Backend URL (optional, defaults to http://localhost:8080)
BACKEND_URL=http://localhost:8081
EOF

# Ensure evaluator results directory is present in the package
# Results folder removed; logs carry per-run artifacts.
mkdir -p $TEMP_DIR/evaluator/logs
touch $TEMP_DIR/evaluator/logs/.gitkeep

# Ensure evaluator/generated_semantic_types is included
if [ ! -d "$TEMP_DIR/evaluator/generated_semantic_types" ]; then
    echo "⚠️  Warning: evaluator/generated_semantic_types directory not found in package"
else
    echo "✅ evaluator/generated_semantic_types included"
fi

# Ensure evaluator/datasets is included
if [ ! -d "$TEMP_DIR/evaluator/datasets" ]; then
    echo "⚠️  Warning: evaluator/datasets directory not found in package"
else
    echo "✅ evaluator/datasets included"
fi

# No Caddy proxy is included anymore; frontend talks directly to backend over HTTP

# Create the zip file
ZIP_NAME="nl2fta-milestone3.zip"
echo ""
echo "📦 Creating client package: $ZIP_NAME"
cd $TEMP_DIR \
  && rm -f ../$ZIP_NAME \
  && zip -r -FS ../$ZIP_NAME . -x \
    "*.DS_Store" \
    ".cursor" ".cursor/" ".cursor/*" \
    ".vscode" ".vscode/" ".vscode/*" \
    "*/.cursor/*" "*/.vscode/*" \
    "test_products.csv" "*/test_products.csv" \
    "text.csv" "*/text.csv" \
    "test.csv" "*/test.csv" \
    "FTA_LIST_TYPE_REQUIREMENTS.md" "*/FTA_LIST_TYPE_REQUIREMENTS.md" \
    "test_logs/*" \
  && cd ..

# Cleanup
rm -rf $TEMP_DIR

# Final report
if [ -f "$ZIP_NAME" ]; then
    SIZE=$(du -h "$ZIP_NAME" | cut -f1)
    echo ""
    echo "✅ Client package created successfully!"
    echo "📦 File: $ZIP_NAME"
    echo "📏 Size: $SIZE"
    echo ""
    echo "This package includes:"
    echo "- Complete backend application (without credentials)"
    echo "- Complete frontend application (no submodule dependency)"
    echo "- Evaluator application with datasets and generated semantic types"
    echo "- Local development scripts"
    echo "- Docker Compose configurations"
    echo "- Simplified Docker Compose (backend + frontend only; no HTTPS proxy)"
    echo "- Full documentation in README.md"
    echo "- Safe .env.example templates (no credentials)"
    echo ""
    echo "This package EXCLUDES:"
    echo "- All AWS credentials and secrets"
    echo "- All .env files with actual credentials"
    echo "- Evaluator logs and results directories"
    echo "- Virtual environments (venv, venv310)"
    echo "- Build artifacts and caches"
    echo "- Deployment infrastructure files"
    echo ""
    echo "Share this zip file with your client for local development and testing."
    echo ""
    echo "Run in production:"
    echo "  docker compose -f docker-compose.prod.yml up -d"
else
    echo "❌ Error: Failed to create deployment package"
    exit 1
fi

# Note: HTTPS proxy has been removed from the package; run backend and frontend directly
