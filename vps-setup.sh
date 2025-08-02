#!/bin/bash

# Wind VPS Setup Script
set -e

echo "=== Wind VPS Setup Script ==="
echo "This script will install Java, Node.js, Nginx, and set up Wind."

# Update system
sudo apt update && sudo apt upgrade -y

# Install dependencies
sudo apt install -y openjdk-17-jdk nodejs npm nginx maven

# Install PM2
sudo npm install -g pm2

# Clone and build
git clone https://github.com/HarshithGGowda/wind.git
cd wind

# Build backend
mvn clean package

# Build frontend
cd ui && npm install && npm run build && cd ..

# Configure Nginx
sudo rm -f /etc/nginx/sites-enabled/default

cat <<EOF | sudo tee /etc/nginx/sites-available/wind
server {
    listen 80;
    server_name _;
    client_max_body_size 500M;
    
    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
    
    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/wind /etc/nginx/sites-enabled/wind
sudo nginx -t && sudo systemctl restart nginx

# Start services
CLASSPATH="target/p2p-1.0-SNAPSHOT.jar:\$(mvn dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout -q)"
pm2 start --name wind-backend java -- -cp "\$CLASSPATH" p2p.App

cd ui && pm2 start npm --name wind-frontend -- start && cd ..
pm2 save && pm2 startup

echo "=== Wind Setup Complete ==="
echo "Access your application at: http://\$(curl -s ifconfig.me)"