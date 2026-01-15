# Catxi Infrastructure - Main Terraform Configuration

# ==========================================
# VPC Module
# ==========================================

module "vpc" {
  source = "./modules/vpc"

  project_name         = var.project_name
  vpc_cidr             = var.vpc_cidr
  availability_zones   = var.availability_zones
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  common_tags          = var.common_tags
}

# ==========================================
# Security Groups Module
# ==========================================

module "security_groups" {
  source = "./modules/security-groups"

  project_name = var.project_name
  vpc_id       = module.vpc.vpc_id
  jenkins_ip   = var.jenkins_ip
  common_tags  = var.common_tags

  depends_on = [module.vpc]
}

# ==========================================
# IAM Module
# ==========================================

module "iam" {
  source = "./modules/iam"

  project_name = var.project_name
  aws_region   = var.aws_region
  common_tags  = var.common_tags
}

# ==========================================
# ECR Module
# ==========================================

module "ecr" {
  source = "./modules/ecr"

  project_name           = var.project_name
  repository_name        = var.ecr_repository_name
  image_tag_mutability   = var.ecr_image_tag_mutability
  image_retention_count  = var.ecr_image_retention_count
  common_tags            = var.common_tags
}

# ==========================================
# RDS Module
# ==========================================

module "rds" {
  source = "./modules/rds"

  project_name            = var.project_name
  instance_class          = var.rds_instance_class
  allocated_storage       = var.rds_allocated_storage
  engine_version          = var.rds_engine_version
  database_name           = var.rds_database_name
  username                = var.rds_username
  password                = var.rds_password
  db_subnet_group_name    = module.vpc.db_subnet_group_name
  security_group_id       = module.security_groups.rds_security_group_id
  multi_az                = var.rds_multi_az
  backup_retention_period = var.rds_backup_retention_period
  common_tags             = var.common_tags

  depends_on = [module.vpc, module.security_groups]
}

# ==========================================
# EC2 Module
# ==========================================

module "ec2" {
  source = "./modules/ec2"

  project_name         = var.project_name
  instance_type        = var.ec2_instance_type
  key_name             = var.ec2_key_name
  subnet_id            = module.vpc.public_subnet_ids[0] # Deploy in first public subnet
  security_group_id    = module.security_groups.app_security_group_id
  iam_instance_profile = module.iam.ec2_instance_profile_name
  elastic_ip           = var.ec2_elastic_ip
  common_tags          = var.common_tags

  depends_on = [module.vpc, module.security_groups, module.iam]
}
