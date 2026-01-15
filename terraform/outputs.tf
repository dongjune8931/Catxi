# Terraform Outputs for Catxi Infrastructure

# ==========================================
# VPC Outputs
# ==========================================

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = module.vpc.public_subnet_ids
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = module.vpc.private_subnet_ids
}

# ==========================================
# EC2 Outputs
# ==========================================

output "ec2_instance_id" {
  description = "EC2 instance ID"
  value       = module.ec2.instance_id
}

output "ec2_public_ip" {
  description = "EC2 public IP address"
  value       = module.ec2.public_ip
}

output "ec2_private_ip" {
  description = "EC2 private IP address"
  value       = module.ec2.private_ip
}

# ==========================================
# RDS Outputs
# ==========================================

output "rds_endpoint" {
  description = "RDS endpoint (without port)"
  value       = module.rds.endpoint
}

output "rds_address" {
  description = "RDS hostname"
  value       = module.rds.address
}

output "rds_port" {
  description = "RDS port"
  value       = module.rds.port
}

output "rds_database_name" {
  description = "RDS database name"
  value       = module.rds.database_name
}

# ==========================================
# ECR Outputs
# ==========================================

output "ecr_repository_url" {
  description = "ECR repository URL"
  value       = module.ecr.repository_url
}

output "ecr_repository_name" {
  description = "ECR repository name"
  value       = module.ecr.repository_name
}

# ==========================================
# Deployment Information
# ==========================================

output "deployment_info" {
  description = "Deployment information"
  value = {
    application_url    = "http://${module.ec2.public_ip}:8080"
    health_check_url   = "http://${module.ec2.public_ip}:8080/actuator/health"
    ssh_command        = "ssh -i ${var.ec2_key_name}.pem ubuntu@${module.ec2.public_ip}"
    ecr_login_command  = "aws ecr get-login-password --region ${var.aws_region} | docker login --username AWS --password-stdin ${module.ecr.repository_url}"
  }
}