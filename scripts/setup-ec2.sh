#!/bin/bash

# ============================================
# EC2 Instance Setup Script
# ============================================
# Run this script on EC2 instance for initial setup
# This is a backup of user-data.sh for manual setup

set -e

echo "=========================================="
echo "Catxi EC2 Setup Script"
echo "=========================================="
echo "This script will install:"
echo "- Docker"
echo "- Docker Compose"
echo "- AWS CLI"
echo "- Additional tools"
echo "=========================================="
echo ""

read -p "Continue? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

# Update system
echo "Updating system packages..."
sudo apt-get update -y
sudo apt-get upgrade -y

# Install Docker
echo "Installing Docker..."
sudo apt-get install -y ca-certificates curl gnupg lsb-release
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker $USER

# Install Docker Compose
echo "Installing Docker Compose..."
DOCKER_COMPOSE_VERSION="2.24.5"
sudo curl -L "https://github.com/docker/compose/releases/download/v${DOCKER_COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" \
    -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Install AWS CLI
echo "Installing AWS CLI..."
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
sudo apt-get install -y unzip
unzip awscliv2.zip
sudo ./aws/install
rm -rf aws awscliv2.zip

# Install additional tools
echo "Installing additional tools..."
sudo apt-get install -y git vim htop jq net-tools curl wget

# Create application directory
echo "Creating application directory..."
mkdir -p $HOME/catxi/logs
chmod 755 $HOME/catxi

# Set timezone
echo "Setting timezone to Asia/Seoul..."
sudo timedatectl set-timezone Asia/Seoul

# Enable swap
echo "Creating swap file..."
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

echo ""
echo "=========================================="
echo "✅ Setup completed!"
echo "=========================================="
echo "Docker version: $(docker --version)"
echo "Docker Compose version: $(docker-compose --version)"
echo "AWS CLI version: $(aws --version)"
echo ""
echo "⚠️  IMPORTANT: Log out and log back in for Docker group to take effect"
echo ""
