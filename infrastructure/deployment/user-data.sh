#!/bin/bash
set -e

# Update system
apt-get update
apt-get upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
usermod -aG docker ubuntu

# Install Docker Compose
curl -L "https://github.com/docker/compose/releases/download/v2.20.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# Install nginx and certbot
apt-get install -y nginx certbot python3-certbot-nginx

# Clone the repository
cd /home/ubuntu
git clone https://github.com/UniversalAGI/precisely-milestone2-nl2fta-prototype.git nl2fta-app
cd nl2fta-app
git submodule update --init --recursive
chown -R ubuntu:ubuntu /home/ubuntu/nl2fta-app

# Create systemd service for auto-start
cat > /etc/systemd/system/nl2fta.service << 'EOF'
[Unit]
Description=NL2FTA Application
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/home/ubuntu/nl2fta-app
ExecStart=/usr/local/bin/docker-compose -f docker-compose.prod.yml up -d
ExecStop=/usr/local/bin/docker-compose -f docker-compose.prod.yml down
User=ubuntu
Group=ubuntu

[Install]
WantedBy=multi-user.target
EOF

systemctl enable nl2fta.service

# Configure nginx to use docker ports
cat > /etc/nginx/sites-available/nl2fta << 'EOF'
server {
    listen 80 default_server;
    server_name _;
    
    location / {
        proxy_pass http://localhost:4000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }
}
EOF

ln -s /etc/nginx/sites-available/nl2fta /etc/nginx/sites-enabled/
rm /etc/nginx/sites-enabled/default
systemctl restart nginx

echo "Setup complete! Next steps:"
echo "1. Copy .env file to /home/ubuntu/.env"
echo "2. Point precisely-prototype.universalagi.com to the Elastic IP"
echo "3. Run: sudo certbot --nginx -d precisely-prototype.universalagi.com"
echo "4. Start the app: sudo systemctl start nl2fta"