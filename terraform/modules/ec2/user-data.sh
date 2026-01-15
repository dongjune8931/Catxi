#!/bin/bash

# ============================================
# Catxi EC2 Instance Bootstrap Script
# ============================================
# This script runs on first boot to set up
# Docker, Docker Compose, and AWS CLI

set -e

# Update system
echo "=========================================="
echo "Updating system packages..."
echo "=========================================="
sudo apt-get update -y
sudo apt-get upgrade -y

# ==========================================
# Install Docker
# ==========================================
echo "=========================================="
echo "Installing Docker..."
echo "=========================================="

# Install prerequisites
sudo apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Add Docker's official GPG key
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Start and enable Docker
sudo systemctl start docker
sudo systemctl enable docker

# Add ubuntu user to docker group
sudo usermod -aG docker ubuntu

echo "Docker installed successfully!"
docker --version

# ==========================================
# Install Docker Compose (standalone)
# ==========================================
echo "=========================================="
echo "Installing Docker Compose..."
echo "=========================================="

DOCKER_COMPOSE_VERSION="2.24.5"
sudo curl -L "https://github.com/docker/compose/releases/download/v${DOCKER_COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" \
    -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

echo "Docker Compose installed successfully!"
docker-compose --version

# ==========================================
# Install AWS CLI v2
# ==========================================
echo "=========================================="
echo "Installing AWS CLI..."
echo "=========================================="

curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
sudo apt-get install -y unzip
unzip awscliv2.zip
sudo ./aws/install
rm -rf aws awscliv2.zip

echo "AWS CLI installed successfully!"
aws --version

# ==========================================
# Install SSM Agent (for Session Manager)
# ==========================================
echo "=========================================="
echo "Installing SSM Agent..."
echo "=========================================="

sudo snap install amazon-ssm-agent --classic
sudo systemctl enable snap.amazon-ssm-agent.amazon-ssm-agent.service
sudo systemctl start snap.amazon-ssm-agent.amazon-ssm-agent.service

echo "SSM Agent installed successfully!"

# ==========================================
# Install Additional Tools
# ==========================================
echo "=========================================="
echo "Installing additional tools..."
echo "=========================================="

sudo apt-get install -y \
    git \
    vim \
    htop \
    jq \
    net-tools \
    curl \
    wget

# ==========================================
# Create Application Directory
# ==========================================
echo "=========================================="
echo "Creating application directory..."
echo "=========================================="

sudo mkdir -p /home/ubuntu/catxi/logs
sudo chown -R ubuntu:ubuntu /home/ubuntu/catxi
chmod 755 /home/ubuntu/catxi

# ==========================================
# Configure CloudWatch Agent (Optional)
# ==========================================
# Uncomment if you want to send logs to CloudWatch
# echo "=========================================="
# echo "Installing CloudWatch Agent..."
# echo "=========================================="
#
# wget https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb
# sudo dpkg -i -E ./amazon-cloudwatch-agent.deb
# rm amazon-cloudwatch-agent.deb

# ==========================================
# System Configuration
# ==========================================
echo "=========================================="
echo "Configuring system..."
echo "=========================================="

# Set timezone to Asia/Seoul
sudo timedatectl set-timezone Asia/Seoul

# Increase file descriptors limit for Docker
echo "* soft nofile 65536" | sudo tee -a /etc/security/limits.conf
echo "* hard nofile 65536" | sudo tee -a /etc/security/limits.conf

# Enable swap (for low-memory instances)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# ==========================================
# Create systemd service for application
# ==========================================
echo "=========================================="
echo "Creating systemd service..."
echo "=========================================="

cat << 'EOF' | sudo tee /etc/systemd/system/catxi-backend.service
[Unit]
Description=Catxi Backend Application
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/home/ubuntu/catxi
ExecStart=/usr/local/bin/docker-compose -f docker-compose.prod.yml up -d
ExecStop=/usr/local/bin/docker-compose -f docker-compose.prod.yml down
User=ubuntu
Group=ubuntu

[Install]
WantedBy=multi-user.target
EOF

# Enable service (will be started by Jenkins deployment)
sudo systemctl daemon-reload
sudo systemctl enable catxi-backend.service

echo "Systemd service created!"

# ==========================================
# Cleanup
# ==========================================
echo "=========================================="
echo "Cleaning up..."
echo "=========================================="

sudo apt-get autoremove -y
sudo apt-get clean

# ==========================================
# Completion
# ==========================================
echo "=========================================="
echo "Bootstrap completed successfully!"
echo "=========================================="
echo "Docker version: $(docker --version)"
echo "Docker Compose version: $(docker-compose --version)"
echo "AWS CLI version: $(aws --version)"
echo "Timezone: $(timedatectl | grep 'Time zone')"
echo "Application directory: /home/ubuntu/catxi"
echo "=========================================="
echo "Server is ready for deployment!"
echo "=========================================="
