# Terraform Backend Configuration
# S3 for state storage + DynamoDB for state locking

terraform {
  backend "s3" {
    bucket         = "catxi-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "ap-northeast-2"
    encrypt        = true
    dynamodb_table = "catxi-terraform-locks"
  }

  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "Catxi"
      Environment = "Production"
      ManagedBy   = "Terraform"
    }
  }
}