#!/bin/bash

echo "🔐 Setting up Submodule Access Token"
echo "===================================="
echo ""
echo "This script will help you create a fine-grained GitHub token for submodule access."
echo ""

# Check if gh CLI is available
if ! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI (gh) is not installed."
    echo "Install from: https://cli.github.com/"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "🔐 Please authenticate with GitHub:"
    gh auth login
fi

# Get repo info
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)

echo "Repository: $REPO"
echo ""
echo "Creating a fine-grained Personal Access Token..."
echo ""
echo "Please follow these steps:"
echo ""
echo "1. This will open your browser to create a token"
echo "2. Token name: nl2fta-submodules"
echo "3. Expiration: 90 days (or your preference)"
echo "4. Repository access: Select only 'UniversalAGI/NL2FTA-UIUX'"
echo "5. Permissions: Contents (Read)"
echo "6. Click 'Generate token' and copy it"
echo ""
echo "Press Enter to open GitHub settings..."
read

# Open browser to create token
open "https://github.com/settings/personal-access-tokens/new"

echo ""
echo "Paste the token below:"
read -s SUBMODULE_TOKEN
echo ""

if [ -z "$SUBMODULE_TOKEN" ]; then
    echo "❌ No token provided. Exiting."
    exit 1
fi

# Set the secret
echo "Setting SUBMODULE_TOKEN secret..."
echo "$SUBMODULE_TOKEN" | gh secret set SUBMODULE_TOKEN --repo "$REPO"

echo "✅ SUBMODULE_TOKEN secret has been set!"
echo ""
echo "The deployment will now be able to access the private frontend submodule."