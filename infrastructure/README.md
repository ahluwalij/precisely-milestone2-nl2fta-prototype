# 🏗️ Infrastructure

This project uses an **EC2 deployment** approach for production.

## 📁 Directory Structure

```
infrastructure/
└── deployment/       # EC2 deployment configuration
    ├── main.tf      # Terraform configuration
    ├── user-data.sh # EC2 initialization script
    └── README.md    # Setup instructions
```

## 🚀 Current Deployment Method

We use an **EC2 instance** with:
- Docker and Docker Compose
- Nginx as reverse proxy with SSL (Let's Encrypt)
- GitHub Actions for CI/CD
- Seamless deployments

### Key Benefits:
- ✅ Simple to understand and maintain
- ✅ Cost-effective (~$10-20/month vs $150-200 for ECS)
- ✅ No complex AWS services needed
- ✅ Direct SSH access for debugging
- ✅ Easy rollback if needed

## 📋 Prerequisites

- AWS CLI configured
- Terraform installed (optional, for initial setup)
- EC2 key pair created
- Domain name (for SSL)

## 🔧 Deployment Steps

### Initial Setup (One-time)

1. **Create EC2 Instance**:
   ```bash
   cd infrastructure/simple
   terraform init
   terraform apply
   ```

2. **Configure Server**:
   - SSH to instance: `ssh -i nl2fta-key.pem ubuntu@<EC2-IP>`
   - Create `/home/ubuntu/.env` with required environment variables
   - Run initial deployment

### Ongoing Deployments

GitHub Actions automatically deploys on push to main branch using zero-downtime strategy.

## 🔑 Environment Variables

Set in `/home/ubuntu/.env` on EC2:

```bash
# AWS Credentials for the app
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_REGION=us-east-1

# AWS Bedrock Configuration
AWS_BEDROCK_REGION=us-east-1
AWS_BEDROCK_MODEL_ID=us.anthropic.claude-sonnet-4-20250514-v1:0

# S3 Configuration
AWS_S3_SEMANTIC_TYPES_BUCKET=my-nl2fta-semantic-types
AWS_S3_SEMANTIC_TYPES_KEY=custom-semantic-types.json

# Authentication
AUTH_PASSWORD=your-secure-password
JWT_SECRET=your-jwt-secret-min-32-chars

# Frontend Configuration
VITE_API_BASE_URL=https://your-domain.com
CORS_ALLOWED_ORIGINS=https://your-domain.com
```

## 📚 Documentation

- [EC2 Setup](deployment/README.md) - Detailed setup instructions
- [Deployment Checklist](../DEPLOYMENT_CHECKLIST.md) - Ensures deployments always work

### Why EC2?
- ✅ 85% cost reduction compared to container orchestration
- ✅ Direct SSH access for debugging
- ✅ No complex AWS services to manage
- ✅ Standard Docker Compose setup
- ✅ Easy to understand and maintain

## 🔍 Monitoring

Simple monitoring approach:
- SSH to check logs: `docker logs nl2fta-app-backend-1`
- GitHub Actions shows deployment status
- Uptime monitoring via external service (optional)

## 🚨 Troubleshooting

### Application Issues
```bash
# SSH to server
ssh -i nl2fta-key.pem ubuntu@your-ec2-ip

# Check running containers
docker ps

# Check logs
docker logs nl2fta-app-backend-1
docker logs nl2fta-app-frontend-1

# Restart if needed
docker-compose -f docker-compose.prod.yml restart
```

### Deployment Issues
- Check GitHub Actions logs
- Verify environment variables in `/home/ubuntu/.env`
- Check [Deployment Checklist](../DEPLOYMENT_CHECKLIST.md)

This simple approach has been working perfectly and saves ~$130/month compared to the complex setup!