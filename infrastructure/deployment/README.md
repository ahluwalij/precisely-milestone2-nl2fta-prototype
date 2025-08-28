# EC2 Deployment

This deployment runs the app on a single EC2 instance with:
- Docker Compose for running the containers
- Nginx for SSL termination
- Elastic IP for stable DNS
- GitHub Actions for auto-deployment

## Initial Setup

1. **Deploy the infrastructure:**
   ```bash
   cd infrastructure/deployment
   terraform init
   terraform apply -var="ssh_key_name=your-key-name" -var="github_token=your-token"
   ```

2. **Note the Elastic IP from the output**

3. **Update DNS:**
   - Point `precisely-prototype.universalagi.com` to the Elastic IP

4. **SSH into the instance:**
   ```bash
   ssh -i your-key.pem ubuntu@<elastic-ip>
   ```

5. **Create the .env file:**
   ```bash
   sudo vim /home/ubuntu/.env
   # Copy contents from your local .env file
   ```

6. **Wait for DNS to propagate, then get SSL certificate:**
   ```bash
   sudo certbot --nginx -d precisely-prototype.universalagi.com
   ```

7. **Start the application:**
   ```bash
   cd /home/ubuntu/nl2fta-app
   sudo -u ubuntu docker-compose -f docker-compose.prod.yml up -d
   ```

## GitHub Actions Setup

Add these secrets to your GitHub repository:
- `EC2_HOST`: The Elastic IP address
- `EC2_SSH_KEY`: The private SSH key content

## Auto-deployment

Every push to `main` will automatically:
1. SSH into the EC2 instance
2. Pull the latest code
3. Rebuild and restart the containers
4. No downtime (containers are rebuilt then swapped)

## Manual Commands

```bash
# View logs
docker-compose -f docker-compose.prod.yml logs -f

# Restart services
docker-compose -f docker-compose.prod.yml restart

# Stop services
docker-compose -f docker-compose.prod.yml down

# Update and restart
git pull
docker-compose -f docker-compose.prod.yml up -d --build
```