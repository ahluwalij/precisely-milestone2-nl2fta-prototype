#!/bin/bash
set -e

echo "🚀 Deploying EC2 Infrastructure"
echo "================================"

# Check for required variables
if [ -z "$SSH_KEY_NAME" ]; then
    echo "❌ Please set SSH_KEY_NAME environment variable"
    echo "   Example: export SSH_KEY_NAME=my-key-name"
    exit 1
fi

if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ Please set GITHUB_TOKEN environment variable"
    echo "   This should be a personal access token with repo access"
    exit 1
fi

# Initialize Terraform
echo "📦 Initializing Terraform..."
terraform init

# Plan the deployment
echo "📋 Planning deployment..."
terraform plan \
    -var="ssh_key_name=$SSH_KEY_NAME" \
    -var="github_token=$GITHUB_TOKEN" \
    -out=tfplan

# Apply the deployment
echo "🔨 Deploying infrastructure..."
terraform apply tfplan

# Get outputs
ELASTIC_IP=$(terraform output -raw elastic_ip)
INSTANCE_ID=$(terraform output -raw instance_id)

echo ""
echo "✅ Deployment Complete!"
echo "======================="
echo "Elastic IP: $ELASTIC_IP"
echo "Instance ID: $INSTANCE_ID"
echo ""
echo "Next steps:"
echo "1. Update GitHub Secrets:"
echo "   - EC2_HOST = $ELASTIC_IP"
echo "   - EC2_SSH_KEY = (your private key content)"
echo ""
echo "2. Point precisely-prototype.universalagi.com to: $ELASTIC_IP"
echo ""
echo "3. SSH into the instance:"
echo "   ssh -i ~/.ssh/$SSH_KEY_NAME.pem ubuntu@$ELASTIC_IP"
echo ""
echo "4. Copy your .env file to the instance:"
echo "   scp -i ~/.ssh/$SSH_KEY_NAME.pem ../.env ubuntu@$ELASTIC_IP:/home/ubuntu/"
echo ""
echo "5. Complete setup on the instance (see README.md)"