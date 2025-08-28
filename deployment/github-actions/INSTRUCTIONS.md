# GitHub Actions Setup Instructions

## Purpose

Configures GitHub Actions to access private submodules during automated deployments.

## When to Use

- When you have private frontend submodules
- When GitHub Actions fails with submodule access errors
- For initial repository setup

## Usage

```bash
# Navigate to GitHub Actions setup directory
cd deployment/github-actions

# Run setup script
./setup-submodule-access.sh
```

## What the Script Does

1. **Opens Browser**
   - Navigates to GitHub token creation page
   - Guides through fine-grained token setup

2. **Token Configuration**
   - Repository access: `UniversalAGI/NL2FTA-UIUX` (frontend repo)
   - Permissions: Contents (Read-only)
   - Expiration: 90 days (recommended)

3. **Secret Management**
   - Sets `SUBMODULE_TOKEN` in repository secrets
   - Enables GitHub Actions to clone private submodules

## Manual Setup (Alternative)

### 1. Create GitHub Token
1. Go to: https://github.com/settings/personal-access-tokens/new
2. Choose "Fine-grained personal access token"
3. Repository access: Select `UniversalAGI/NL2FTA-UIUX`
4. Permissions: Contents (Read)
5. Generate and copy token

### 2. Set Repository Secret
1. Go to: https://github.com/UniversalAGI/precisely-milestone2-nl2fta-prototype/settings/secrets/actions
2. Click "New repository secret"
3. Name: `SUBMODULE_TOKEN`
4. Value: Paste your token
5. Click "Add secret"

## Security Best Practices

- **Minimal Permissions**: Token only has read access to specific repository
- **Expiration**: Set reasonable expiration date (90 days)
- **Scope**: Limited to single repository, not organization-wide
- **Rotation**: Regenerate tokens before expiration

## Troubleshooting

### Common Issues

1. **"Repository not found" error**
   - Token doesn't have access to private repository
   - Re-run setup script with correct permissions

2. **"Bad credentials" error**
   - Token has expired
   - Regenerate token and update secret

3. **"No such file or directory" errors**
   - Submodule not checked out
   - Verify SUBMODULE_TOKEN is set correctly

### Verification

After setup, check that:
1. `SUBMODULE_TOKEN` appears in repository secrets
2. GitHub Actions can access frontend files
3. Deployment workflows complete successfully

## Alternative Approaches

If fine-grained tokens don't work:

1. **Deploy Keys**: SSH-based repository access
2. **GitHub Apps**: Application-based authentication
3. **Organization Tokens**: If you have org-level access

Contact support if you need help with alternative authentication methods.