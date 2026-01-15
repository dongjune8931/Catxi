#!/bin/bash

# ============================================
# Rollback Script
# ============================================
# Rollback to previous Docker image version

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <build-number>"
    echo "Example: $0 42"
    exit 1
fi

ROLLBACK_VERSION=$1

# Get EC2 IP from Terraform
cd terraform
EC2_IP=$(terraform output -raw ec2_public_ip 2>/dev/null)
cd ..

if [ -z "$EC2_IP" ]; then
    echo "❌ Could not get EC2 IP from Terraform"
    echo "Please provide EC2 IP manually:"
    read -p "EC2 IP: " EC2_IP
fi

echo "=========================================="
echo "Rollback Deployment"
echo "=========================================="
echo "Target: $EC2_IP"
echo "Rollback to build: $ROLLBACK_VERSION"
echo "=========================================="
echo ""

read -p "Continue? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

# SSH and rollback
ssh -o StrictHostKeyChecking=no ubuntu@${EC2_IP} << EOF
    set -e
    cd /home/ubuntu/catxi

    echo "Updating .env with rollback version..."
    sed -i "s/BUILD_NUMBER=.*/BUILD_NUMBER=${ROLLBACK_VERSION}/" .env

    echo "Pulling image version ${ROLLBACK_VERSION}..."
    docker-compose -f docker-compose.prod.yml pull

    echo "Restarting containers..."
    docker-compose -f docker-compose.prod.yml down
    docker-compose -f docker-compose.prod.yml up -d

    echo "Waiting for application to start..."
    sleep 30

    echo "Checking health..."
    curl -f http://localhost:8080/actuator/health

    echo ""
    echo "✅ Rollback completed successfully!"
    echo "Current version: ${ROLLBACK_VERSION}"
EOF

echo ""
echo "=========================================="
echo "✅ Rollback completed!"
echo "=========================================="
echo "Verify at: http://${EC2_IP}:8080/actuator/health"
echo ""
