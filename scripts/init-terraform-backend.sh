#!/bin/bash

# ============================================
# Initialize Terraform Backend
# ============================================
# This script creates S3 bucket and DynamoDB table
# for Terraform state management
#
# Run this ONCE before first terraform init

set -e

# Configuration
BUCKET_NAME="catxi-terraform-state"
DYNAMODB_TABLE="catxi-terraform-locks"
AWS_REGION="ap-northeast-2"

echo "=========================================="
echo "Terraform Backend Initialization"
echo "=========================================="
echo "Bucket: $BUCKET_NAME"
echo "DynamoDB Table: $DYNAMODB_TABLE"
echo "Region: $AWS_REGION"
echo "=========================================="
echo ""

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo "❌ AWS CLI is not installed. Please install it first."
    exit 1
fi

# Check AWS credentials
echo "Checking AWS credentials..."
aws sts get-caller-identity > /dev/null || {
    echo "❌ AWS credentials not configured. Run 'aws configure' first."
    exit 1
}
echo "✅ AWS credentials OK"
echo ""

# ==========================================
# Create S3 Bucket
# ==========================================

echo "Creating S3 bucket: $BUCKET_NAME"

# Check if bucket already exists
if aws s3api head-bucket --bucket "$BUCKET_NAME" 2>/dev/null; then
    echo "⚠️  S3 bucket already exists: $BUCKET_NAME"
else
    # Create bucket
    aws s3api create-bucket \
        --bucket "$BUCKET_NAME" \
        --region "$AWS_REGION" \
        --create-bucket-configuration LocationConstraint="$AWS_REGION"

    echo "✅ S3 bucket created: $BUCKET_NAME"

    # Enable versioning
    aws s3api put-bucket-versioning \
        --bucket "$BUCKET_NAME" \
        --versioning-configuration Status=Enabled

    echo "✅ Versioning enabled"

    # Enable encryption
    aws s3api put-bucket-encryption \
        --bucket "$BUCKET_NAME" \
        --server-side-encryption-configuration '{
            "Rules": [
                {
                    "ApplyServerSideEncryptionByDefault": {
                        "SSEAlgorithm": "AES256"
                    },
                    "BucketKeyEnabled": true
                }
            ]
        }'

    echo "✅ Encryption enabled"

    # Block public access
    aws s3api put-public-access-block \
        --bucket "$BUCKET_NAME" \
        --public-access-block-configuration \
        "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

    echo "✅ Public access blocked"

    # Add lifecycle policy (optional - keep only recent versions)
    aws s3api put-bucket-lifecycle-configuration \
        --bucket "$BUCKET_NAME" \
        --lifecycle-configuration '{
            "Rules": [
                {
                    "Id": "DeleteOldVersions",
                    "Status": "Enabled",
                    "NoncurrentVersionExpiration": {
                        "NoncurrentDays": 90
                    }
                }
            ]
        }'

    echo "✅ Lifecycle policy configured"
fi

echo ""

# ==========================================
# Create DynamoDB Table
# ==========================================

echo "Creating DynamoDB table: $DYNAMODB_TABLE"

# Check if table already exists
if aws dynamodb describe-table --table-name "$DYNAMODB_TABLE" --region "$AWS_REGION" 2>/dev/null; then
    echo "⚠️  DynamoDB table already exists: $DYNAMODB_TABLE"
else
    # Create table
    aws dynamodb create-table \
        --table-name "$DYNAMODB_TABLE" \
        --attribute-definitions AttributeName=LockID,AttributeType=S \
        --key-schema AttributeName=LockID,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST \
        --region "$AWS_REGION" \
        --tags Key=Project,Value=Catxi Key=Purpose,Value=TerraformStateLocking

    echo "✅ DynamoDB table created: $DYNAMODB_TABLE"

    # Wait for table to be active
    echo "Waiting for table to be active..."
    aws dynamodb wait table-exists \
        --table-name "$DYNAMODB_TABLE" \
        --region "$AWS_REGION"

    echo "✅ Table is active"
fi

echo ""
echo "=========================================="
echo "✅ Terraform backend initialized!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. cd terraform"
echo "2. terraform init"
echo "3. terraform plan"
echo "4. terraform apply"
echo ""
echo "Resources created:"
echo "- S3 Bucket: s3://$BUCKET_NAME"
echo "- DynamoDB Table: $DYNAMODB_TABLE"
echo ""
