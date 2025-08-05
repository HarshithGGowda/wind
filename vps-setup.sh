#!/bin/bash

# PeerLink VPS Setup Script
# This script helps set up PeerLink on a fresh Ubuntu/Debian VPS

# Exit on error
set -e

echo "=== PeerLink VPS Setup Script ==="
echo "This script will install Java, Node.js, Nginx, and set up PeerLink."

# Update system
echo "Updating system packages..."
sudo apt update
sudo apt upgrade -y

# Install Java and set JAVA_HOME
echo "Installing Java..."
sudo apt install -y openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
echo "export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64" | sudo tee -a /etc/environment

# Verify Java installation
java -version
if [ $? -ne 0 ]; then
    echo "Error: Java installation failed"
    exit 1
fi

# Install Node.js
echo "Installing Node.js..."
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# Install Nginx
echo "Installing Nginx..."
sudo apt install -y nginx

# Install PM2
echo "Installing PM2..."
sudo npm install -g pm2

# Install Maven
echo "Installing Maven..."
sudo apt install -y maven

# Optimize system for large file transfers (500MB support)
echo "Optimizing system for large file transfers..."
# Increase file descriptor limits
echo "* soft nofile 65536" | sudo tee -a /etc/security/limits.conf
echo "* hard nofile 65536" | sudo tee -a /etc/security/limits.conf

# Optimize network settings for large transfers
echo "net.core.rmem_max = 16777216" | sudo tee -a /etc/sysctl.conf
echo "net.core.wmem_max = 16777216" | sudo tee -a /etc/sysctl.conf
echo "net.ipv4.tcp_rmem = 4096 87380 16777216" | sudo tee -a /etc/sysctl.conf
echo "net.ipv4.tcp_wmem = 4096 65536 16777216" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
echo "System optimized for large file transfers."

# Clone repository with error handling
echo "Cloning repository..."
if [ -d "wind" ]; then
    rm -rf wind
fi
git clone https://github.com/HarshithGGowda/wind.git
cd wind

# Verify required files exist
if [ ! -f "pom.xml" ]; then
    echo "Error: pom.xml not found in cloned repository"
    exit 1
fi
if [ ! -d "ui" ]; then
    echo "Error: ui directory not found in cloned repository"
    exit 1
fi

# Build backend with error handling
echo "Building Java backend..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "Error: Maven build failed"
    exit 1
fi

# Verify JAR was created
if [ ! -f "target/p2p-1.0-SNAPSHOT.jar" ]; then
    echo "Error: JAR file was not created during build"
    exit 1
fi
echo "Backend build successful"

# Build frontend with error handling
echo "Building frontend..."
cd ui
npm install
if [ $? -ne 0 ]; then
    echo "Error: npm install failed"
    exit 1
fi

npm run build
if [ $? -ne 0 ]; then
    echo "Error: npm build failed"
    exit 1
fi

# Verify build directory was created
if [ ! -d "build" ]; then
    echo "Error: Frontend build directory not created"
    exit 1
fi
cd ..
echo "Frontend build successful"

# Set up Nginx (your existing nginx config is correct)
echo "Setting up Nginx..."

# Ensure the default site is removed to avoid conflicts
if [ -e /etc/nginx/sites-enabled/default ]; then
    sudo rm /etc/nginx/sites-enabled/default
    echo "Removed default Nginx site configuration."
fi

# Create the peerlink configuration file with the correct content
echo "Creating /etc/nginx/sites-available/peerlink..."
cat <<EOF | sudo tee /etc/nginx/sites-available/peerlink
server {
    listen 80;
    server_name _; # Catch-all for HTTP requests

    # Increase client max body size for large file uploads
    client_max_body_size 500M;
    client_body_timeout 300s;
    client_header_timeout 300s;
    
    # Increase proxy timeouts for large file transfers
    proxy_connect_timeout 300s;
    proxy_send_timeout 300s;
    proxy_read_timeout 300s;
    send_timeout 300s;

    # Backend API
    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;
        
        # Increase buffer sizes for large files
        proxy_buffering off;
        proxy_request_buffering off;
        proxy_max_temp_file_size 0;
    }

    # Frontend
    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;
    }

    # Additional security headers
    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options SAMEORIGIN;
    add_header X-XSS-Protection "1; mode=block";
}
EOF

# Create the symbolic link to enable the peerlink site
sudo ln -sf /etc/nginx/sites-available/peerlink /etc/nginx/sites-enabled/peerlink

sudo nginx -t
if [ $? -eq 0 ]; then
    sudo systemctl restart nginx
    echo "Nginx configured and restarted successfully."
else
    echo "Nginx configuration test failed. Please check /etc/nginx/nginx.conf and /etc/nginx/sites-available/peerlink."
    exit 1
fi

# Open firewall ports for P2P file sharing
echo "Configuring firewall for P2P file transfers..."
# Reset UFW to clean state
sudo ufw --force reset
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp
sudo ufw allow 3000/tcp
sudo ufw allow 49152:65535/tcp
echo "y" | sudo ufw enable
echo "Firewall configured for P2P transfers."
sudo ufw status verbose

# Start backend with memory check
echo "Starting backend with PM2..."
TOTAL_MEM=$(free -m | awk 'NR==2{printf "%.0f", $2}')
if [ $TOTAL_MEM -lt 1500 ]; then
    # For smaller instances
    echo "Detected ${TOTAL_MEM}MB RAM, using conservative memory settings"
    pm2 start --name peerlink-backend java -- -Xmx768m -Xms256m -XX:+UseG1GC -jar target/p2p-1.0-SNAPSHOT.jar
else
    # For 2GB+ instances  
    echo "Detected ${TOTAL_MEM}MB RAM, using optimized memory settings"
    pm2 start --name peerlink-backend java -- -Xmx1536m -Xms512m -XX:+UseG1GC -jar target/p2p-1.0-SNAPSHOT.jar
fi

# Install serve for production frontend
echo "Installing serve for production frontend..."
sudo npm install -g serve

# Start frontend with PM2 (production mode)
echo "Starting frontend with PM2..."
cd ui
# Make sure we're serving the correct build directory
if [ ! -d "build" ]; then
    echo "Error: build directory not found. Build may have failed."
    exit 1
fi
pm2 start --name peerlink-frontend serve -- -s build -p 3000
cd ..

# Set up PM2 to start on boot with proper user
echo "Setting up PM2 to start on boot..."
# Wait for services to be fully running
sleep 3
pm2 save
sudo env PATH=$PATH:/usr/bin /usr/lib/node_modules/pm2/bin/pm2 startup systemd -u $USER --hp $HOME
echo "IMPORTANT: Run the command shown above to complete PM2 startup configuration."

echo "=== Setup Complete ==="
echo "PeerLink is now running on your VPS!"
echo ""
echo "Services Status:"
pm2 status
echo ""
echo "Access Points:"
echo "- Backend API: http://localhost:8080 (Internal - accessed via Nginx)"
echo "- Frontend: http://$(curl -s http://checkip.amazonaws.com 2>/dev/null || echo 'YOUR_LIGHTSAIL_IP') (Public access)"
echo ""
echo "Important Notes:"
echo "✓ System optimized for 500MB file transfers"
echo "✓ Firewall configured for P2P ports (49152-65535)"
echo "✓ PM2 configured for auto-restart on boot"
echo "✓ Nginx configured with large file support"
echo ""
echo "🚨 CRITICAL AWS LIGHTSAIL SETUP REQUIRED 🚨"
echo "Your server firewall is configured, but you MUST also configure AWS Lightsail networking:"
echo ""
echo "1. Go to: https://lightsail.aws.amazon.com/ls/webapp/home"
echo "2. Click your instance → Networking tab"
echo "3. Click 'Add rule' under Firewall"
echo "4. Add: Custom TCP, Port range: 49152-65535, Source: Anywhere (0.0.0.0/0)"
echo ""
echo "Without this step, P2P file transfers will NOT work!"
echo ""
echo "Your application is ready to handle large file transfers!"