# Client Package Creation Instructions

## Purpose

Creates a complete, self-contained client package for local development and testing. Excludes deployment infrastructure to focus on what clients need to run the application locally.

## Usage

```bash
# Create deployment package (from root)
./deployment/client-package/create-deployment-package.sh
```

## Output

The script creates a zip file named:

```
nl2fta-e2e-happy-path.zip
```

When extracted, the contents will be in a folder named `nl2fta-e2e-happy-path/`

## Package Contents

The generated package includes:

1. **Complete Application**

   - Backend Spring Boot application
   - Frontend Angular application (no submodule dependency)
   - All necessary configuration files

2. **Local Development Tools**

   - Docker Compose configurations (dev and prod)
   - Development scripts (run-dev.sh, run-prod.sh, etc.)
   - Environment file templates

3. **Testing & Evaluation**

   - Unit and integration test scripts
   - FTA benchmark evaluation tools
   - Service health check utilities

4. **Documentation**
   - Complete README.md with setup instructions
   - Configuration details
   - Troubleshooting tips

## Client Instructions

The package includes the main `README.md` with:

- Step-by-step local setup guide
- Optional AWS Bedrock configuration
- Available development scripts
- Troubleshooting information

## Security Notes

The package excludes:

- Git history and sensitive files
- Deployment infrastructure (AWS, GitHub Actions)
- Development dependencies and build artifacts
- Private keys and credentials

## Distribution

Share the generated zip file with clients for local development and testing.

## Package Size

Typical package size: ~50-100MB (compressed)

- Includes all source code and dependencies
- Excludes node_modules and build artifacts
