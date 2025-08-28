# 🚀 Deployment

This directory contains deployment scripts and documentation for the NL2FTA application.

## 📁 Directory Structure

```
deployment/
├── README.md                    # This file
├── client-package/            # Client deployment package creation
│   ├── create-deployment-package.sh
│   └── INSTRUCTIONS.md
└── github-actions/            # GitHub Actions setup
    ├── setup-submodule-access.sh
    └── INSTRUCTIONS.md
```

## 🎯 Current Deployment Method

We use **EC2 + GitHub Actions**:

### How It Works
1. **Push to main branch** → GitHub Actions triggers
2. **SSH to EC2** → Pulls latest code and submodules
3. **Seamless deployment** → Builds new containers, switches traffic
4. **Automatic verification** → Checks endpoints are working

### Why This Approach?
- ✅ **Cost**: ~$20/month
- ✅ **Debugging**: Direct SSH access
- ✅ **Simplicity**: Just Docker Compose + Nginx
- ✅ **Reliability**: Fewer moving parts = fewer failures

## 🚀 Quick Deployment

```bash
# That's it! Just push to main
git push origin main
```

GitHub Actions handles everything automatically.

## 📋 Initial Setup

See [Infrastructure Simple Setup](../infrastructure/simple/README.md) for EC2 setup.

### Required GitHub Secrets
- `EC2_HOST`: Your EC2 instance IP (e.g., 3.214.211.68)
- `EC2_SSH_KEY`: Private key content for SSH access

### Required on EC2
Environment variables in `/home/ubuntu/.env`:
```bash
AWS_ACCESS_KEY_ID=your-key
AWS_SECRET_ACCESS_KEY=your-secret
AUTH_PASSWORD=your-password
JWT_SECRET=your-jwt-secret
```

## 🔧 Deployment Scripts

### Client Package Creation
Location: `client-package/create-deployment-package.sh`

Creates a deployment package for clients without GitHub access.

## ⚠️ Important Notes

### Frontend Submodule
**ALWAYS** commit frontend changes:
```bash
cd frontend
git add .
git commit -m "your changes"
git push origin functional-prototype
cd ..
git add frontend
git commit -m "chore: update frontend submodule"
```

This is the #1 cause of deployment failures!


## 🆘 Troubleshooting

### Deployment Fails
1. Check GitHub Actions logs
2. Verify frontend submodule is committed
3. Check environment variables on EC2
4. See [Deployment Checklist](../DEPLOYMENT_CHECKLIST.md)

### Manual Deployment
If GitHub Actions fails:
```bash
ssh -i nl2fta-key.pem ubuntu@your-ec2-ip
cd /home/ubuntu/nl2fta-app
git pull origin main
git submodule update --init --recursive --remote
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up -d --build
```

## 📚 Documentation

- [Deployment Checklist](../DEPLOYMENT_CHECKLIST.md) - **MUST READ!**
- [Infrastructure Setup](../infrastructure/deployment/README.md)
- [GitHub Actions Workflow](../.github/workflows/deploy.yml)

### Note on Evaluation Safeguards (SemTab and Telco 5G Traffic)

When running evaluator workflows (outside of user-facing UX), the evaluator applies dataset-specific safeguards for `semtab` and `telco_5GTraffic` to avoid overloading the backend (row halving, row/column caps, and file-upload mode). These controls are internal to the evaluator and are not exposed in the user UI.


---