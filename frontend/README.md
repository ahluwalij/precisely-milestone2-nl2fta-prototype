# Precisely NL2FTA Prototype (UI/UX)

A platform that provides semantic type detection for tabular data (.csv and .sql files) using the FTA library, enhanced with AI-powered custom semantic type generation via AWS Bedrock.

## 📋 Table of Contents

- [What This Application Does (UI/UX)](#what-this-application-does-uiux)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Quickstart Guide](#quickstart-guide)
- [Available Commands](#available-commands)
- [Testing](#testing)
- [Configuration](#configuration)
- [API Endpoints](#api-endpoints)
- [Project Structure](#project-structure)
- [Development](#development)
- [Deployment](#deployment)
- [Environment Variables](#environment-variables)

## What This Application Does (UI/UX)

- **Intelligent Data Analysis**: Upload CSV or SQL files and automatically classify columns with semantic types
- **AI-Powered Type Generation**: Create custom semantic types using natural language descriptions via AWS Bedrock
- **Built-in Semantic Library**: Includes pre-configured semantic types (EMAIL, PHONE, DATE, US_STATE, etc.)
- **Confidence Scoring**: Provides confidence levels for all semantic type classifications
- **Pattern Recognition**: Uses regex patterns and list-based matching for accurate data classification
- **Secure Access**: Protected with authentication and secure session management

## ✨ Key Features

### 🤖 AI-Powered Semantic Type Generation (Mocked)

- **Natural Language Input**: Describe desired semantic types in plain English
- **AWS Bedrock Integration**: ⚠️ **COMPLETELY MOCKED** - No real AWS calls, provides demo responses
- **Pattern Generation**: Automatically creates regex patterns and validation rules
- **Example Management**: Handles positive and negative examples for training
- **Header Pattern Recognition**: Generates header matching patterns for column identification

### 🛡️ Security & Authentication

- **Password Protection**: Secure login system with session management
- **Development Mode**: Built-in development password for easy local testing
- **Production Security**: Environment-based authentication for deployment
- **Session Timeout**: 24-hour session expiration with automatic cleanup

### 🎨 User Interface

- **Modern Design**: Built with PrimeNG components and custom theming
- **Responsive Layout**: Works seamlessly across desktop and mobile devices
- **Real-time Updates**: Live progress indicators and status updates
- **Accessibility**: WCAG-compliant design with proper ARIA labels

## 🏗️ Architecture

### Technology Stack

- **Frontend**: Angular 18.2.0 with standalone components
- **UI Library**: PrimeNG 18.0.0 with custom theming with Precisely's colors
- **Language**: TypeScript 5.4.0
- **Styling**: CSS with design system variables
- **Backend**: Spring Boot with REST API
- **AI Integration**: AWS Bedrock for semantic type generation
- **Testing**: Jasmine & Karma with comprehensive test suite
- **Build System**: Angular CLI with Webpack

## 🚀 Quickstart Guide

### Prerequisites

- **Node.js**: Version 18.x or higher
- **npm**: Version 8.x or higher
- **Modern Browser**: Chrome, Firefox, Safari, or Edge

### ✅ Minimal Environment Setup Required!

**This is a fully self-contained prototype** - no AWS account, no database setup needed! Only requires setting a password in `.env.local`.

### Installation

1. **Ensure Node.js and npm are installed**:

   If you get "command not found" when running `npm install`, install Node.js first:

   ```bash
   # macOS (using Homebrew - install from https://brew.sh if needed)
   brew install node

   # macOS (without Homebrew - using official installer)
   curl "https://nodejs.org/dist/latest/node-${VERSION:-$(curl -s https://nodejs.org/dist/latest/ | grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' | head -1)}.pkg" > "$HOME/Downloads/node-latest.pkg" && open "$HOME/Downloads/node-latest.pkg"

   # Ubuntu/Debian
   curl -fsSL https://deb.nodesource.com/setup_lts.x | sudo -E bash -
   sudo apt-get install -y nodejs

   # Windows (using Chocolatey)
   choco install nodejs

   # Or download directly from https://nodejs.org/
   ```

2. **Install dependencies**:

   ```bash
   npm install
   ```

3. **Create environment file**:

   ```bash
   cp .env.example .env.local
   ```

   Edit `.env.local` and set your password (must be at least 8 characters long):

   ```
   AUTH_PASSWORD=your-secure-password-for-the-password-page
   ```

4. **Start the development server**:

   ```bash
   npm start
   ```

5. **Access the application**:
   - Open your browser to `http://localhost:4200`
   - Login with the password you set in `.env.local`

### Quick Demo

1. **Mock AWS Configuration**:
   - When prompted in the UI, enter any values into the AWS configuration input fields.
   - The demo will use mocked responses for AWS Bedrock integration—no real AWS account or credentials are required.

2. **Upload Sample Data**:
   - Use the provided sample file: `public/templates/employees.csv` by clicking on the button in the UI
   - Contains employee data with ID, first name, and last name columns

3. **View Analysis Results**:
   - Automatic classification of each column
   - Confidence scores and statistical analysis
   - Expandable detailed results

4. **Test Built-in Semantic Types**:
   - Navigate to "Semantic Types" in the main menu
   - View pre-loaded types: EMAIL, PHONE, DATE, US_STATE, CREDIT_CARD, SSN
   - Each type shows its description, pattern, and confidence threshold

5. **Create Custom Semantic Type (AI-Powered)**:
   - Click "Add Semantic Type" button
   - **Step 1 - Natural Language Input**:
     - Describe your type: "First Name" or "Employee ID"
     - Add positive examples: "John", "Sarah", "Michael"
     - Add negative examples: "john", "123", "NULL"
     - Add header examples: "fname", "first_name", "given_name"
   - **Step 2 - Configure Type**:
     - Review AI-generated regex pattern and description
     - Modify confidence threshold (default: 85)
     - Edit patterns if needed
     - Click "Regenerate with Feedback" to refine
   - Save the new semantic type

6. **Test Duplicate Detection**:
   - Try creating a semantic type with description containing "phone"
   - The system will detect similarity to existing PHONE type
   - Choose to "Use Existing Type" or "Create Different Type"
   - This demonstrates the duplicate prevention feature

7. **See Auto Re-analysis**:
   - After creating a custom type, existing analyses automatically re-run
   - New semantic type is applied to previously uploaded data
   - Updated confidence scores and classifications appear

## 📋 Available Commands

### Development Commands

```bash
# Start development server (starts both auth server and Angular)
npm start

# Start development server (local only, no auth)
npm run start:local

# Start authentication server only
npm run start:auth
```

### Testing Commands

```bash
# Run all tests (single run)
npm run test

# Run tests with file watching
npm run test:watch

# Run tests with coverage report
npm run test:coverage
```

### Code Quality Commands

```bash
# Lint TypeScript and templates
npm run lint

# Fix linting issues automatically
npm run lint:fix

# Lint CSS files
npm run lint:css

# Fix CSS linting issues
npm run lint:css:fix

# Format code with Prettier
npm run format

# Check code formatting
npm run format:check
```

## 🧪 Testing

### Test Coverage Overview

The application maintains high test coverage across all critical components:

- **Total Test Files**: 29 test files (`*.spec.ts`)
- **Total Test Cases**: 672 individual test cases
- **Statement Coverage**: 91.03% (1,717/1,886 statements)
- **Branch Coverage**: 82.99% (610/735 branches)
- **Function Coverage**: 91.49% (441/482 functions)
- **Line Coverage**: 91.13% (1,624/1,782 lines)

### Test Categories

#### Component Tests

- **Authentication Components**: Login functionality and guard behavior
- **File Upload Components**: File validation, processing, and error handling
- **Semantic Types Components**: Type creation, editing, and management
- **Analysis Components**: Results display and interaction

#### Service Tests

- **Core Services**: Configuration, logging, and environment management
- **Business Logic**: FTA classification, AWS Bedrock integration
- **Data Processing**: File parsing, analysis workflows
- **Authentication**: Session management and security

#### Integration Tests

- **End-to-End Workflows**: Complete user journeys from upload to analysis
- **API Integration**: Mock and real API endpoint testing
- **Error Scenarios**: Comprehensive error handling validation

### Running Tests

```bash
# Run all tests with coverage
npm run test:coverage

# Watch mode for development
npm run test:watch

# Single run for CI/CD
npm run test
```

## ⚙️ Configuration

### Environment Configuration

The application uses a hierarchical configuration system:

1. **Default Values**: Hardcoded defaults for development
2. **Environment Variables**: Override defaults in production
3. **Runtime Config**: Loaded from backend API

### Key Configuration Options

| Setting                    | Default         | Description                 |
| -------------------------- | --------------- | --------------------------- |
| `MAX_FILE_SIZE`            | 10485760 (10MB) | Maximum upload file size    |
| `MAX_ROWS`                 | 1000            | Maximum rows to process     |
| `HTTP_TIMEOUT_MS`          | 30000           | API request timeout         |
| `DEFAULT_HIGH_THRESHOLD`   | 95              | High confidence threshold   |
| `DEFAULT_MEDIUM_THRESHOLD` | 80              | Medium confidence threshold |
| `DEFAULT_LOW_THRESHOLD`    | 50              | Low confidence threshold    |

### Development vs Production

#### Development Mode (Default)

- **Authentication**: Uses environment-configured password via .env file
- **AWS Bedrock**: Completely mocked implementation - NO real AWS integration
- **FTA Classification**: Mock responses with simulated delays
- **File Analysis**: All processing done client-side with mock data
- **Configuration**: Uses environment variables from .env file
- **Backend**: Minimal backend dependencies - auth server for password validation

#### Production Mode

- **Authentication**: Uses `/api/auth` endpoint (still has defaults if env vars not set)
- **AWS Bedrock**: Still completely mocked - NO real AWS integration
- **All Other Services**: Same mock implementations as development

## 🌐 API Endpoints

### Authentication

- `POST /api/auth` - User authentication

### File Processing

- File upload handled client-side with immediate analysis

### Mock Endpoints (Development)

The application includes comprehensive mock implementations for development and demonstration purposes.

## 📁 Project Structure

```
Precisely-NL2FTA-Prototype/
├── src/
│   ├── app/
│   │   ├── auth/                 # Authentication module
│   │   ├── core/                 # Core services and components
│   │   │   ├── components/       # Shared components (header)
│   │   │   └── services/         # Core business services
│   │   ├── features/             # Feature modules
│   │   │   ├── analyses/         # Analysis results and management
│   │   │   ├── file-upload/      # File upload functionality
│   │   │   └── semantic-types/   # Semantic type management
│   │   ├── main/                 # Main application layout
│   │   ├── shared/               # Shared models and utilities
│   │   └── styles/               # Global styles and theming
│   ├── styles.css               # Global stylesheet
│   └── index.html               # Application entry point
├── api/                         # API integration utilities
├── public/                      # Static assets and templates
├── coverage/                    # Test coverage reports
├── dist/                        # Production build output
└── package.json                 # Dependencies and scripts
```

### Key Directories

- **`src/app/core/services/`**: Business logic and data services
- **`src/app/features/`**: Feature-specific components and logic
- **`src/app/shared/models/`**: TypeScript interfaces and data models
- **`src/app/styles/`**: Design system and theming
- **`api/`**: Backend API functions for Vercel deployment

## 🔧 Development

### Local Development Setup

1. **Install dependencies**:

   ```bash
   npm install
   ```

2. **Start development server**:

   ```bash
   npm run start
   ```

3. **Run tests**:
   ```bash
   npm run test:watch
   ```

### Development Features

- **Hot Reload**: Automatic browser refresh on code changes
- **Proxy Configuration**: API calls proxied to backend during development
- **Source Maps**: Full debugging support with original TypeScript
- **Linting**: Real-time code quality feedback

### Code Style

The project follows Angular and TypeScript best practices:

- **ESLint**: TypeScript and Angular-specific rules
- **Prettier**: Consistent code formatting
- **Stylelint**: CSS code quality
- **Strict TypeScript**: Full type safety enabled

## 🚀 Deployment

### EC2 Deployment

The application is deployed to EC2 using the deployment script in the root directory:

```bash
# From project root
./deploy-to-ec2.sh
```

### Environment Variables for Production

Production environment variables are configured in the backend application.yml and through AWS configuration.

### Build Optimization

- **Tree Shaking**: Removes unused code
- **Minification**: Compressed JavaScript and CSS
- **Lazy Loading**: Route-based code splitting
- **Service Worker**: Optional PWA features

## 🔑 Environment Variables

### 🔒 Security Configuration

**For Development**:

- Set `AUTH_PASSWORD` in `.env.local` file
- `JWT_SECRET` will use a generated default if not set

**For Production**: Required environment variables:

- `AUTH_PASSWORD`: Password for application access (REQUIRED - no default)
- `JWT_SECRET`: Secret for session token generation (recommended)

### ❌ NOT NEEDED - These Are Completely Mocked

- ~~`MAX_FILE_SIZE`~~ - Hardcoded to 10MB
- ~~`MAX_ROWS`~~ - Hardcoded to 1000
- ~~`HTTP_TIMEOUT_MS`~~ - No real HTTP calls in prototype
- ~~`AWS_ACCESS_KEY_ID`~~ - AWS is completely mocked
- ~~`AWS_SECRET_ACCESS_KEY`~~ - AWS is completely mocked
- ~~`AWS_REGION`~~ - AWS is completely mocked

---

## 📞 Support

For technical support or questions about this prototype, please refer to the development team or create an issue in the project repository.

**Version**: 0.0.0  
**Angular**: 18.2.0  
**Node.js**: 18.x+  
**License**: Private/Proprietary
# Deployment trigger Thu Jul 24 20:32:58 PDT 2025
