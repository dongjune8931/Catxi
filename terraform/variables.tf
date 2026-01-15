# Terraform Variables for Catxi Infrastructure

# ==========================================
# General Configuration
# ==========================================

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "catxi"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

# ==========================================
# Network Configuration
# ==========================================

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones for subnets"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets"
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24"]
}

# ==========================================
# EC2 Configuration
# ==========================================

variable "ec2_instance_type" {
  description = "EC2 instance type for application server"
  type        = string
  default     = "t2.micro" # Free tier eligible
}

variable "ec2_key_name" {
  description = "EC2 key pair name for SSH access"
  type        = string
  default     = "popol-key" # Your existing key
}

variable "jenkins_ip" {
  description = "Jenkins server IP for SSH access (CIDR format)"
  type        = string
  default     = "52.79.93.195/32" # Jenkins server Elastic IP
}

# ==========================================
# RDS Configuration
# ==========================================

variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro" # Smallest available for MySQL
}

variable "rds_allocated_storage" {
  description = "Allocated storage for RDS in GB"
  type        = number
  default     = 20 # Free tier: 20GB
}

variable "rds_engine_version" {
  description = "MySQL engine version"
  type        = string
  default     = "8.0.35"
}

variable "rds_database_name" {
  description = "Initial database name"
  type        = string
  default     = "catxi"
}

variable "rds_username" {
  description = "Master username for RDS"
  type        = string
  default     = "catxi_admin"
}

variable "rds_password" {
  description = "Master password for RDS"
  type        = string
  sensitive   = true
  # Set via terraform.tfvars or environment variable TF_VAR_rds_password
}

variable "rds_backup_retention_period" {
  description = "Backup retention period in days"
  type        = number
  default     = 1  # 프리티어는 0~1일만 가능
}

variable "rds_multi_az" {
  description = "Enable Multi-AZ for high availability"
  type        = bool
  default     = false # Set to true for production HA (doubles cost)
}

# ==========================================
# ECR Configuration
# ==========================================

variable "ecr_repository_name" {
  description = "ECR repository name"
  type        = string
  default     = "catxi-backend"
}

variable "ecr_image_tag_mutability" {
  description = "Image tag mutability setting"
  type        = string
  default     = "MUTABLE"
}

variable "ecr_image_retention_count" {
  description = "Number of images to retain"
  type        = number
  default     = 10
}

# ==========================================
# Tags
# ==========================================

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default = {
    Project     = "Catxi"
    Environment = "Production"
    ManagedBy   = "Terraform"
  }
}