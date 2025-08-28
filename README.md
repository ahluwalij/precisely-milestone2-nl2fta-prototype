<div align="center">
<br>
<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./assets/logo_white.png">
    <source media="(prefers-color-scheme: light)" srcset="./assets/logo_black.png">
    <!-- Fallback -->
    <img alt="vCache" src="./assets/logo_white.png" width="55%">
  </picture>
</p>

**Natural Language to FTA Plugin Prototype** is a comprehensive AI-powered tabular data analysis platform built with the FTA library, with semantic type profiling and custom semantic type generation using **AWS Bedrock**.

[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=flat-square&logo=docker&logoColor=white)](https://www.docker.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-6DB33F?style=flat-square&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-18.2-DD0031?style=flat-square&logo=angular&logoColor=white)](https://angular.io/)
[![AWS Bedrock](https://img.shields.io/badge/AWS-Bedrock-FF9900?style=flat-square&logo=amazon-aws&logoColor=white)](https://aws.amazon.com/bedrock/)
[![FTA Library](https://img.shields.io/badge/FTA-16.0.3-4285F4?style=flat-square)](https://github.com/tsegall/fta)
[![Chainguard](https://img.shields.io/badge/Chainguard-Secure-00D4AA?style=flat-square&logo=chainguard&logoColor=white)](https://chainguard.dev/)

</div>

> ## **🔐 Required Prerequisites**
>
> ### **Chainguard Account**
>
> **You must have a Chainguard account to run this application.** The application uses Chainguard's secure container images as base images.
>
> **Create your account at: https://console.chainguard.dev/**
>
> ### **Other Requirements**
>
> - **Docker & Docker Compose**: Required for containerized deployment
> - **AWS Bedrock Access**: You must provide your own AWS Credentials.
>
> ### **🔒 Security Features**
>
> The application implements end-to-end encryption for AWS credentials:
>
> - **Encrypted Credential Transmission**: All AWS credentials are encrypted using RSA-OAEP (SHA-256) before transmission over HTTP
> - **Development**: Credentials appear in `/api/config` for autofill when `APP_ENVIRONMENT=development`

---

## 📚 Table of Contents

- [⚡ Quickstart](#-quickstart)
  - [Set Up Application](#-set-up-application)
  - [AWS Setup](#-aws-setup)
  - [Running the Application](#-running-the-application)
  - [Access Points](#-access-points)
- [🏗 Architecture](#-architecture)
  - [Technology Stack](#-technology-stack)
- [🔌 API Reference](#-api-reference)
- [⚙️ Configuration](#⚙️-configuration)
  - [Environment Files](#-environment-files)
  - [Changing Settings](#-changing-settings)
- [👨‍💻 Development Guide](#-development-guide)
  - [Project Structure](#-project-structure)
  - [Development Workflow](#-development-workflow)
  - [Integration Tests](#-integration-tests)
  - [Code Coverage](#-code-coverage)
- [🤖 AI Features](#-ai-features)
  - [Semantic Type Generation](#-semantic-type-generation)
  - [Custom Semantic Types & Vector Storage](#-custom-semantic-types--vector-storage)
- [🐳 Docker Deployment](#-docker-deployment)
  - [Container Architecture](#-container-architecture)

## ⚡ Quickstart

_You need to use NL2FTA with Docker. Install Docker Engine and ensure the service is running. See [Docker Engine install](https://docs.docker.com/engine/install/)._ 

### Windows one-time setup (run .sh scripts natively)

If you are on Windows and want to run all `./scripts/*.sh` directly from PowerShell (without WSL or aliases), run this once from the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\windows-enable-bash-scripts.ps1
```

What it does:

- Associates `.sh` with Git Bash per-user and adds `.SH` to `PATHEXT` so PowerShell treats `.sh` as executable
- Normalizes script line-endings to LF to avoid Bash CRLF issues
- Generates `.cmd` wrappers next to each `.sh` as a guaranteed fallback

After that, open a new PowerShell and run scripts normally:

```powershell
.\n+scripts\run-dev.sh
scripts\run-eval.sh --dataset example
scripts\generate-semantic-types.sh --dataset extension
```

If your system still opens `.sh` files in an editor, use the generated wrappers instead (they are kept in sync automatically):

```powershell
.
scripts\run-dev.cmd
scripts\run-eval.cmd --dataset example
scripts\generate-semantic-types.cmd --dataset extension
```

Troubleshooting:

- If `.sh` opens in a text editor: re-run the setup script, then restart PowerShell, or use the `.cmd` wrappers
- Confirm association: `cmd /c assoc .sh` and `cmd /c ftype sh_auto_file` should show Bash as the handler

### Set Up Application

```bash
# Extract from the provided zip package
unzip nl2fta-milestone3.zip
cd nl2fta-milestone3

# Set up environment variables
cp .env.example .env
```

> **Authentication Setup**
>
> `AUTH_PASSWORD` in the `.env` file:
>
> - This password is what you'll enter on the application's login page
> - This password must be **more than 8 characters**
> - If the password is less than 8 characters, you will not be able to access the application

### AWS Setup

<summary><strong>Setting Up Your AWS Account</strong></summary>

**Prerequisites**: You need an AWS account with admin access to proceed.

> **Note**: You'll stay logged in as your admin user throughout this setup. The IAM user we create is only for the NL2FTA application to use programmatically.

#### Step 1: Create IAM User for NL2FTA Application

1. **Navigate to IAM**

   - Navigate to the [AWS Console](https://console.aws.amazon.com/console/home) on an account with AWS Console Access
   - In AWS Console, search for "IAM" and click on it

2. **Set Permissions**

   - Click "Policies" on the left
   - Click "Create policy"
   - Switch to "JSON" tab
   - Copy and paste the entire contents of `nl2fta-user-policy.json`
   - Click "Next"
   - Policy name: `NL2FTAPolicy`
   - Click "Create policy"

   > **Note about S3 Buckets**: The IAM policy allows the app to create any S3 bucket starting with `nl2fta-*`. The application will automatically generate unique bucket names based on your AWS account ID (e.g., `nl2fta-semantic-types-123456789012-us-east-1` and `nl2fta-vector-storage-123456789012-us-east-1`) when you connect your AWS credentials.

3. **Complete User Creation**

   - Go to the IAM Users page from the left sidebar
   - Click "Create user"
   - User name: `nl2fta-user`
   - Click "Next"
   - Select "Attach policies directly"
   - Search for and select `NL2FTAPolicy`
   - Click "Next" then "Create user"

4. **Create Access Keys**
   - Click on the newly created user
   - Go to "Security credentials" tab
   - Under "Access keys", click "Create access key"
   - Select "Application running outside AWS"
   - Click "Next", then "Create access key"
   - In .env, set AWS_ACCESS_KEY_ID=[YOUR_ACCESS_KEY] and AWS_SECRET_ACCESS_KEY=[YOUR_SECRET_ACCESS_KEY] so that these credentials can be autofilled in the app

#### Step 2: Enable Claude 4 Sonnet in AWS Bedrock

_(Continue as your admin user - model access applies to the entire AWS account)_

> **How Bedrock Works**: Model access is granted at the AWS account level, not per user. Once you enable models as admin, the IAM user can access them through the API using the permissions in the policy.

1. **Navigate to AWS Bedrock**

   - In [AWS Console](https://console.aws.amazon.com/console/home), search for "Bedrock"
   - Click on "Amazon Bedrock"

2. **Request Model Access**

   - In left sidebar, click "Model access"
   - Find and enable these models:
     - **Claude Sonnet 4** (ID: `us.anthropic.claude-sonnet-4-20250514-v1:0`) - For AI generation
     - **Titan Text Embeddings V2** (ID: `amazon.titan-embed-text-v2:0`) - For semantic similarity
   - Click "Request access" for each model
   - Submit the request

3. **Wait for Approval**
   - Access is usually granted within minutes for both models
   - You'll receive email confirmations
   - Status will change from "Access requested" to "Access granted"
   - The `nl2fta-user` can now access these models via API

#### Step 3: Enable CloudWatch Logging and Metrics

NL2FTA supports engineering metrics through AWS CloudWatch integration.

> **What This Enables**:
>
> - Centralized logging with user attribution
> - User feedback tracking
> - Performance metrics and error monitoring
> - Usage analytics across all users

1. **Create IAM User for CloudWatch Admin**

   - In [AWS Console](https://console.aws.amazon.com/console/home), go to IAM → Users → Create user
   - User name: `nl2fta-admin`
   - Click "Next"
   - Select "Attach policies directly"
   - Click "Create policy" and switch to JSON tab
   - Copy and paste the contents of `nl2fta-admin-policy.json`
   - Name the policy: `NL2FTAAdminPolicy`
   - Create the policy and attach it to the user
   - Create access keys for this user

2. **Configure CloudWatch in .env**

   ```bash
   # CloudWatch is automatically enabled when admin credentials are provided
   AWS_ADMIN_ACCESS_KEY_ID=your-admin-access-key   # Admin user from step 1
   AWS_ADMIN_SECRET_ACCESS_KEY=your-admin-secret-key
   AWS_ADMIN_REGION=us-east-1                      # The region your Admin user is created in
   # Optional overrides
   AWS_CLOUDWATCH_LOG_GROUP=/aws/nl2fta
   AWS_CLOUDWATCH_LOG_STREAM=application-logs
   ```

3. **View Logs in AWS Console**
   - Navigate to CloudWatch → Log groups
   - Find `/aws/nl2fta` log group
   - Logs will show: `[LEVEL] [USERNAME] Message` followed by detailed JSON
   - Example: `[INFO] [john.doe] Successfully saved semantic type: CUSTOMER.ID`

> **Note**: Admin credentials are separate from user credentials to maintain security isolation. The admin credentials are only used for CloudWatch operations.

### Running the Application

The application runs locally using Docker containers:

Access points:

- **Frontend**: http://localhost:4200 (Development mode) / http://localhost:4000 (Production mode)
- **Backend API**: http://localhost:8081
- **API Documentation**: http://localhost:8081/swagger-ui/index.html

> **Authentication**: When you first access the frontend, you'll see a password page. Enter the password you set as `AUTH_PASSWORD` in your `.env` file to access the application.

> **What happens when you run the scripts:**
>
> 1. **Chainguard Setup**: Scripts automatically install `chainctl` and guide you through one-time authentication
> 2. **Environment Loading**: Scripts load from `.env` file in project root (required for AUTH_PASSWORD and JWT_SECRET)
> 3. **Docker Build**: Fresh container builds with secure Chainguard base images
> 4. **Service Startup**: Both frontend and backend containers start with hot reload in dev mode

## 🏗 Architecture

```
┌─────────────────────────┐          ┌──────────────────────────┐     ┌─────────────────────┐
│   Frontend (Angular)    │─────────▶│   Backend (Spring Boot)  │────▶│     AWS Bedrock     │
│     Port: 4200/4000     │          │       Port: 8081         │     │  (Claude Sonnet 4)  │
│                         │          │                          │     │                     │
│ • File Upload UI        │          │ • FTA Library Integration│     │ • Type Generation   │
│ • Semantic Type Mgmt    │          │ • Custom Type Storage    │     │ • Pattern Analysis  │
│ • AI Generation Modal   │          │ • AWS Bedrock Client     │     │ • Similarity Check  │
│ • Real-time Results     │          │ • RESTful APIs           │     │                     │
└─────────────────────────┘          └──────────────────────────┘     └─────────────────────┘
           │                                      │
           │                                      ├───────────────────────────┐
           │                                      ▼                           ▼
           │                         ┌──────────────────────────┐  ┌──────────────────────┐
           └────────────────────────▶│       Custom Types       │  │   AWS CloudWatch     │
                                     │   AWS S3 Vector Buckets  │  │                      │
                                     └──────────────────────────┘  │ • Centralized Logs   │
                                                                   │ • User Attribution   │
                                                                   │ • Performance Metrics│
                                                                   │ • Usage Analytics    │
                                                                   └──────────────────────┘
```

## 🛠 Technology Stack

| Component             | Technology                    | Purpose                                  |
| --------------------- | ----------------------------- | ---------------------------------------- |
| **Frontend**          | Angular 18.2 with Signals     | Modern UI with reactive state management |
| **Backend**           | Spring Boot 3.4.1             | RESTful APIs and business logic          |
| **AI Engine**         | AWS Bedrock (Claude Sonnet 4) | Semantic type generation and analysis    |
| **Data Processing**   | FTA Library 16.0.3            | Fast tabular data analysis               |
| **Logging & Metrics** | AWS CloudWatch                | Centralized logging and analytics        |
| **Infrastructure**    | Docker + Chainguard           | Secure containerized deployment          |
| **UI Components**     | PrimeNG 18.0                  | Professional Angular components          |
| **Code Quality**      | ESLint, Prettier, Stylelint   | Consistent code formatting and quality   |
| **Containerization**  | Multi-stage Dockerfiles       | Secure Chainguard base images            |

## 🔌 API Reference

> **Interactive API Documentation**
>
> The full, interactive API reference is available at [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html) when the backend is running.

<details>
<summary><strong>AWS Credentials</strong></summary>

Manage AWS credentials and storage configuration.

- `GET /api/aws/credentials/status` — Get AWS credentials and storage status
- `POST /api/aws/credentials/storage/reload` — Reload semantic types storage
- `POST /api/aws/credentials/disconnect` — Disconnect AWS credentials
- `POST /api/aws/credentials/connect` — Connect AWS credentials

</details>

<details>
<summary><strong>AWS Management</strong></summary>

AWS credentials and region management for Claude Sonnet 4.0.

- `DELETE /api/aws/credentials` — Clear AWS credentials
- `GET /api/aws/status` — Get AWS configuration status
- `POST /api/aws/validate-model/{region}` — Validate access to Claude Sonnet 4.0
- `POST /api/aws/validate-credentials` — Validate AWS credentials and get available regions
- `POST /api/aws/models/{region}` — Get Claude Sonnet 4.0 model for a region
- `POST /api/aws/configure` — Configure AWS Bedrock client for Claude Sonnet 4.0

</details>

<details>
<summary><strong>Configuration</strong></summary>

Runtime configuration for frontend application.

- `GET /api/config` — Get frontend configuration

</details>

<details>
<summary><strong>Custom Semantic Types</strong></summary>

Manage custom semantic type definitions.

- `DELETE /api/semantic-types/{semanticType}` — Remove a custom semantic type
- `GET /api/semantic-types` — Get all custom semantic types
- `GET /api/semantic-types/custom-only` — Get only user-defined custom semantic types
- `POST /api/semantic-types` — Add a new custom semantic type
- `POST /api/semantic-types/reload` — Reload custom semantic types
- `PUT /api/semantic-types/{semanticType}` — Update an existing custom semantic type

</details>

<details>
<summary><strong>File Upload</strong></summary>

File upload and analysis endpoints.

- `POST /api/table-classification/reanalyze/{analysisId}` — Re-analyze with updated semantic types
- `POST /api/table-classification/analyze` — Analyze uploaded file

</details>

<details>
<summary><strong>Semantic Type Generation</strong></summary>

AI-powered semantic type generation using AWS Bedrock.

- `GET /api/semantic-types/aws/status` — Check AWS configuration status
- `POST /api/semantic-types/generate` — Generate semantic type using AI
- `POST /api/semantic-types/generate-validated-examples`
- `POST /api/semantic-types/generate-custom` — Generate custom semantic type ready for storage
- `POST /api/semantic-types/aws/logout` — Logout and clear AWS credentials
- `POST /api/semantic-types/aws/configure` — Configure AWS credentials

</details>

<details>
<summary><strong>Table Classification</strong></summary>

Table column classification using FTA.

- `DELETE /api/analyses` — Delete all stored analyses
- `GET /api/health` — Health check
- `GET /api/analyses` — Get all stored analyses
- `POST /api/classify/table` — Classify table columns
- `POST /api/analyses/{analysisId}/reanalyze` — Reanalyze a stored analysis

</details>

---

## ⚙️ Configuration

All configuration is centralized in Docker Compose files for easy management.

### Environment Files

| Environment               | File                      | Purpose                                           |
| ------------------------- | ------------------------- | ------------------------------------------------- |
| **Development**           | `docker-compose.dev.yml`  | Hot reload, debug logging                         |
| **Production**            | `docker-compose.prod.yml` | Optimized performance, security                   |
| **Environment Variables** | `.env`                    | AWS credentials and other secrets (never commit!) |

### Changing Settings

1. Edit the appropriate docker-compose file or `.env` file
2. Restart services: `./scripts/stop-services.sh && ./scripts/run-dev.sh`

## 👨‍💻 Development Guide

### Project Structure

The project is organized into several main directories:

- **backend/** - Spring Boot application with Java services, controllers, and comprehensive test coverage (91%+)
- **frontend/** - Angular application (when deployed)
- **evaluator/** - FTA performance evaluation framework with benchmark datasets
- **infrastructure/** - Infrastructure as Code (Terraform) configurations
- **scripts/** - Development and operations automation scripts
- **Docker files** - Container configurations for development and production environments

### Development Workflow

| Command                                | Purpose                                                           |
| -------------------------------------- | ----------------------------------------------------------------- |
| `./scripts/run-dev.sh`                 | Start development environment                                     |
| `./scripts/run-prod.sh`                | Start production environment                                      |
| `./scripts/run-unit-tests.sh`          | Run tests with coverage reports                                   |
| `./scripts/run-integration-tests.sh`   | Run integration tests                                             |
| `./scripts/run-eval.sh [DATASET]`      | Run FTA benchmark evaluation                                      |
| `./scripts/stop-services.sh`           | Stop all services                                                 |
| `./scripts/validate-datasets.sh`       | Validate evaluator datasets and show ground-truth mappings        |
| `./scripts/generate-semantic-types.sh` | Generate custom semantic types via LLM and save timestamped JSONs |

### Integration Tests

> **AWS Requirements**
>
> - Integration tests make real API calls to AWS Bedrock and will incur charges
> - You need a fully configured AWS account with an IAM user that has the permissions defined in `nl2fta-user-policy.json`
> - AWS Bedrock models must be enabled: Claude Sonnet 4.0 and Titan Text Embeddings V2
> - The tests will prompt for AWS credentials if not already configured

**Run Tests**: `./scripts/run-integration-tests.sh [COMPONENT|all]`

**Available options**:

- `-h, --help` - Show usage information
- `--use-saved-credentials` - Use saved AWS credentials without prompting
- `--non-interactive` - Run in non-interactive mode (for CI)
- `--skip-aws-validation` - Skip AWS credentials validation
- `aws` - AWS Connectivity & Authentication only
- `file` - File Upload & Initial Classification only
- `llm` - LLM Semantic Type Generation only
- `vector` - Vector Storage & Embedding only
- `similarity` - Similarity Search & Checking only
- `reclassify` - Re-classification with Custom Types only
- `validation` - End-to-End Workflow Validation only
- `all` - Run complete E2E test suite (default)

**Examples**:

```bash
./scripts/run-integration-tests.sh              # Run full test suite
./scripts/run-integration-tests.sh all          # Run full test suite
./scripts/run-integration-tests.sh aws          # Run only AWS connectivity test
./scripts/run-integration-tests.sh llm          # Run only LLM generation test

# Use saved credentials (non-interactive)
./scripts/run-integration-tests.sh --use-saved-credentials
./scripts/run-integration-tests.sh aws --use-saved-credentials
```

**Note about `--use-saved-credentials`**: This flag automatically uses previously saved AWS credentials without prompting. If no saved credentials exist, the script will exit with an error message explaining how to set up credentials first. Run the script without this flag initially to save your credentials interactively.

The integration tests use the `EnhancedE2EIntegrationTest` class which performs comprehensive testing of all system components with detailed logging, metrics, and full prompt visibility.

### FTA Evaluator

The FTA Evaluator provides a comprehensive framework for evaluating semantic type detection performance on benchmark datasets.

**Quick Start:**

```bash
# Run all datasets with all descriptions (Python 3.9+ required)
./scripts/run-eval.sh --dataset all

# Run specific dataset
# Unified multi-file evaluator for all datasets. Use --files (comma-separated) and optional --data-dir
./scripts/run-eval.sh --dataset semtab
./scripts/run-eval.sh --dataset semtab --files \
  "/Users/you/semtab/GitTables_104.csv,/Users/you/semtab/folder,/Users/you/semtab/GitTables_38.csv"
./scripts/run-eval.sh --dataset semtab --data-dir \
  "/Users/you/semtab" --files "GitTables_60.csv,subset_dir"
```

#### Validate datasets (ground truth layout and cleanliness)

Before running evaluations, you can validate dataset directories and view ground-truth mappings per column:

```bash
# Validate all dataset directories under evaluator/datasets/data/
./scripts/validate-datasets.sh

# Validate a single dataset directory (e.g., extension)
./scripts/validate-datasets.sh --dataset extension
```

Expected CSV layout (universal):

- Row 0: baseline GT (built-in types)
- Row 1: custom GT (your custom types)
- Row 2: column headers
- Row 3+: data rows

The script prints compact per-column mappings: `header: baseline='...'  custom='...'` to help you manually verify correctness.

**Available Datasets:** insurance, semtab, transactions, banking, telco_5GTraffic, telco_customer_churn

For complete documentation, including SemTab multi-file evaluator usage, dataset resolution, and timestamped generated types, see the [Evaluator README](evaluator/README.md). The evaluator installs dependencies strictly from a checked-in `evaluator/requirements.lock.txt` for reproducibility (no fallbacks). Backend builds occur inside Docker Compose; no host-side Java/Gradle is required on EC2/Linux.

### LLM Semantic Type Generator (CLI)

Use the generator to create custom semantic types with AWS Bedrock (Claude Sonnet 4) and store them for the evaluator to consume.

What it does

- Starts a short-lived backend on port 8083 with tuned JVM options
- Interactively collects/validates AWS credentials (or uses env vars) and configures Bedrock
- Copies dataset label files to `evaluator/datasets/generation-labels/`
- Calls the backend API to generate types for the selected dataset and description patterns
- Saves timestamped JSON outputs in `evaluator/generated_semantic_types/`
- Writes a full per-run log under `evaluator/logs/<timestamp>/`

Outputs

- Generated types: `evaluator/generated_semantic_types/{dataset}_descriptionN_{YYYYMMDD_HHMMSS}.json`
- Log: `evaluator/logs/<timestamp>/generate-types.log`
- Labels snapshot (if found): `evaluator/datasets/generation-labels/`

Usage

```bash
# Interactive mode (prompts for AWS creds unless provided via env)
./scripts/generate-semantic-types.sh --dataset extension

# Non-interactive with env credentials
AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... AWS_REGION=us-east-1 \
  ./scripts/generate-semantic-types.sh --dataset insurance --descriptions "6"

# Base generation on a directory of CSVs
./scripts/generate-semantic-types.sh --dataset transactions \
  --data-dir evaluator/datasets/data/transactions

# Base generation on specific files and subfolders (comma-separated)
./scripts/generate-semantic-types.sh --dataset semtab \
  --files "/abs/path/GitTables_104.csv,/abs/path/subset,/abs/path/GitTables_38.csv"

# Increase backend heap
./scripts/generate-semantic-types.sh --dataset extension --descriptions "6" --heap 3g

# Leave generator backend running after completion (for debugging)
./scripts/generate-semantic-types.sh --dataset extension --no-cleanup
```

Options

- `--dataset NAME`: Dataset tag for output naming and description selection (default: all)
- `--descriptions "LIST"`: Space-separated description numbers to generate (optional; if omitted, the generator auto-detects available description indices from the inputs header).
- `--data-dir DIR`: Directory containing CSVs to base generation on (required if `--files` omitted)
- `--files LIST`: Comma-separated files/dirs to base generation on (required if `--data-dir` omitted)
- `--region REGION`: AWS region for Bedrock (default: us-east-1)
- `--heap SIZE`: Backend `-Xmx` (e.g., 2g). `-Xms` is auto-set to ~50% of `-Xmx`
- `--jvm-opts STRING`: Full `JAVA_OPTS` override for the generator backend
- `--no-cleanup`: Do not stop the generator backend after completion
- `--use-saved-credentials`: Use saved AWS credentials without prompting

Notes

- **About `--use-saved-credentials`**: This flag automatically uses previously saved AWS credentials without prompting. If no saved credentials exist, the script will exit with an error message explaining how to set up credentials first. Run the script without this flag initially to save your credentials interactively.
- The evaluator auto-loads the latest timestamped file for each selected description number `N` when running with `--dataset NAME`.
- If multiple timestamped files exist and you need determinism, pass `--timestamp YYYYMMDD_HHMMSS` to the evaluator.
- Ports: generator uses 8083; evaluator uses 8082; dev backend uses 8081.

#### How the evaluator resolves datasets, generated types, labels, logs and results

- When you pass `--data-dir DIR` or `--files ...` to `./scripts/run-eval.sh`, the evaluator derives the dataset tag from the directory/file path and runs a unified multi-file flow.
- Generated types are loaded from `evaluator/generated_semantic_types/{dataset}_descriptionN_*.json` (latest timestamp) with a legacy fallback without timestamp. If `--descriptions` is omitted for evaluation, the runner auto-detects available description indices from these files (fallback to `1 2 3 4 5 6` when none found).
- Ground truth (GT) comes from the universal CSV layout in your inputs: row 0 (baseline/built-ins), row 1 (custom), row 2 (headers), row 3+ (data).
- In comparative mode, the evaluator runs a baseline pass against row 0 and a custom pass against row 1, then reports deltas.
- The runner writes logs/results into a per-run directory. By default it sets `EVALUATOR_RUN_DIR=evaluator/logs/<timestamp>`. The evaluation now consolidates logging into a single file per run: `run-eval.log`. Results JSON are also saved in this directory. If `EVALUATOR_RUN_DIR` is absent, outputs are written under `evaluator/results/<timestamp>/`.

> Note on evaluator safeguards (internal, non-UX): For the `semtab`, `telco_5GTraffic`, and `insurance` datasets, the evaluator applies dataset-scoped truncation and caps (rows/columns). These controls are applied programmatically in the evaluator and are not visible in the user-facing UI for running evals. See the Evaluator README for details and overrides.

### Code Coverage

The project includes comprehensive code coverage reporting for both backend and frontend components.

#### Prerequisites

Before running unit tests, ensure you have Gradle 8.14.2 installed (this specific version is required):

```bash
# macOS - Install Gradle 8.x
brew install gradle@8

# Add Gradle 8 to your PATH (required for keg-only formula)
echo 'export PATH="/opt/homebrew/opt/gradle@8/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc  # Or restart your terminal

# Linux - Install specific version
sdk install gradle 8.14.2  # Using SDKMAN
# Or download from: https://gradle.org/releases/

# Windows
# Download Gradle 8.14.2 from: https://gradle.org/releases/
# Follow installation instructions for Windows

# Verify installation
gradle --version  # Should show version 8.x
```

**Note**: The project uses Gradle Wrapper, so you can also use `./gradlew` (Linux/macOS) or `gradlew.bat` (Windows) instead of installing Gradle globally.

**Important**: The project requires Gradle 8.14.2. If you encounter JUnit Platform errors, verify the wrapper version:

```bash
# Check current wrapper version
./gradlew --version

# If not 8.14.2, update the wrapper
./gradlew wrapper --gradle-version 8.14.2
```

#### Coverage Reports

```bash
# Generate coverage reports for both backend and frontend
./scripts/run-unit-tests.sh

# View detailed coverage reports
open backend/build/reports/jacoco/test/html/index.html     # Backend HTML report
open frontend/coverage/frontend/lcov-report/index.html    # Frontend HTML report
open coverage/                                             # Combined coverage directory
```

**Note**: The script continues to generate coverage reports even if tests fail, allowing you to see coverage for both passing and failing tests.

#### Coverage Configuration

- **Backend**: JaCoCo plugin with XML/HTML reports showing Lines, Instructions, and Branch coverage
- **Frontend**: Karma with Istanbul for TypeScript coverage using ChromeHeadless browser
- **Thresholds**: Build fails if coverage drops below configured thresholds
- **Console Output**: Coverage percentages are displayed during test execution

#### Coverage Tools

- **Backend**: JaCoCo 0.8.12 with Gradle integration
- **Frontend**: Karma + Istanbul with Angular test runner
- **Visualization**: HTML reports with line-by-line coverage

#### Enhanced E2E Test Components

The Enhanced E2E Integration Test Suite validates the complete workflow:

1. **AWS Connectivity & Authentication** (`aws`)

   - Validates AWS credentials and Bedrock access
   - Tests Claude Sonnet 4.0 model availability
   - Verifies S3 bucket permissions

2. **File Upload & Initial Classification** (`file`)

   - Tests CSV/SQL file processing
   - Validates FTA library integration
   - Performs initial semantic type detection

3. **LLM Semantic Type Generation** (`llm`)

   - Tests AI-powered type generation with Claude Sonnet 4.0
   - Validates prompt templates and responses
   - Generates custom semantic type definitions

4. **Vector Storage & Embedding** (`vector`)

   - Tests Amazon Titan Text Embeddings V2 integration
   - Validates vector storage in S3
   - Tests embedding generation and storage

5. **Similarity Search & Checking** (`similarity`)

   - Tests vector-based similarity search
   - Validates duplicate type prevention
   - Tests similarity threshold (0.35) functionality

6. **Re-classification with Custom Types** (`reclassify`)

   - Tests application of custom semantic types
   - Validates type persistence and retrieval
   - Tests classification accuracy improvements

7. **End-to-End Workflow Validation** (`validation`)
   - Runs complete workflow from file upload to custom type application
   - Validates all components working together
   - Generates comprehensive metrics and logs

## 🤖 AI Features

### Semantic Type Generation

The platform leverages AWS Bedrock with **Bring-Your-Own (BYO) AWS Credentials**:

#### Capabilities

- **Similarity Layer**: Vector-based similarity with Titan embeddings + prompt-based fallback
- **Validation Layer**: Examples generated and validated against plugins

#### AI Prompt Templates

Located in `backend/src/main/resources/prompts/`:

- `semantic-type-generation.txt` - Core type generation
- `similarity-check.txt` - Type similarity analysis
- `semantic-type-comparison.txt` - Type comparison and validation
- `regenerate-data-values.txt` - Data value regeneration
- `regenerate-header-values.txt` - Header value regeneration

### Custom Semantic Types & Vector Storage

#### Storage Architecture

Custom semantic types are **only available when AWS credentials are connected**:

1. **AWS S3 Storage** (Required)

   - All custom types stored in user's S3 bucket
   - Automatic bucket creation if needed
   - Persistent across deployments
   - 5-minute automatic sync interval

2. **Vector Embeddings** (AWS Bedrock Titan)
   - Semantic similarity search using Amazon Titan Text Embeddings V2
   - 0.35 similarity threshold
   - **Required**: Titan Text Embeddings V2 must be enabled in Bedrock (see AWS Setup above)

#### S3 Bucket Configuration

When AWS credentials are provided, the system uses:

```env
# Semantic types storage
AWS_S3_SEMANTIC_TYPES_BUCKET=nl2fta-semantic-types-123456789012-us-east-1
AWS_S3_SEMANTIC_TYPES_KEY=custom-semantic-types.json

# Vector storage (separate bucket)
AWS_S3_VECTOR_BUCKET=nl2fta-vector-storage-123456789012-us-east-1
AWS_S3_VECTOR_PREFIX=semantic-type-vectors/
```

The S3 buckets store:

- **Semantic Types Bucket**: Custom type definitions, metadata, and validation rules
- **Vector Storage Bucket**: Embeddings for each semantic type with similarity search index

#### Vector Search Architecture

```
User Input → AWS Bedrock Titan → Vector Embedding
                                        ↓
                              Similarity Search (S3)
                                        ↓
                              Find Similar Types
                                        ↓
                              Prevent Duplicates
```

#### Storage Format

Custom types are stored in JSON format:

```json
{
  "types": [
    {
      "id": "social-security",
      "name": "Social Security Number",
      "description": "US Social Security Number",
      "pattern": "\\d{3}-\\d{2}-\\d{4}",
      "examples": ["123-45-6789", "987-65-4321"]
    }
  ]
}
```

## 🐳 Docker Deployment

<details>
<summary><strong>Development Mode (`docker-compose.dev.yml`)</strong></summary>

- **Frontend**: Angular dev server with hot reload on port 4200
- **Backend**: Spring Boot with debug logging on port 8081
- **Volume mounts**: Live code synchronization
- **Network**: Direct service communication

</details>

<details>
<summary><strong>Production Mode (`docker-compose.prod.yml`)</strong></summary>

- **Frontend**: Node.js server on port 4000
- **Backend**: Production-optimized Spring Boot on port 8081
- **Security**: Environment-based configuration
- **Performance**: JVM tuning, resource limits

</details>

---

<br>

Built with ❤️ by the [UniversalAGI Team](https://universalagi.com/)
