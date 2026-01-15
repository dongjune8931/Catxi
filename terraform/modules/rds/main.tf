# RDS Module - MySQL Database

# ==========================================
# RDS Instance
# ==========================================

resource "aws_db_instance" "main" {
  # Instance Configuration
  identifier     = "${var.project_name}-db"
  engine         = "mysql"
  engine_version = var.engine_version
  instance_class = var.instance_class

  # Storage Configuration
  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.allocated_storage * 2 # Auto-scaling up to 2x
  storage_type          = "gp3"
  storage_encrypted     = true

  # Database Configuration
  db_name  = var.database_name
  username = var.username
  password = var.password
  port     = 3306

  # Network Configuration
  db_subnet_group_name   = var.db_subnet_group_name
  vpc_security_group_ids = [var.security_group_id]
  publicly_accessible    = false

  # High Availability
  multi_az = var.multi_az

  # Backup Configuration
  backup_retention_period = var.backup_retention_period
  backup_window           = "03:00-04:00" # 12:00-13:00 KST (UTC+9)
  maintenance_window      = "mon:04:00-mon:05:00" # 13:00-14:00 KST

  # Performance Insights
  performance_insights_enabled = false # Enable for production monitoring

  # Parameter Group
  parameter_group_name = aws_db_parameter_group.main.name

  # Monitoring
  enabled_cloudwatch_logs_exports = ["error", "general", "slowquery"]
  monitoring_interval             = 60
  monitoring_role_arn             = aws_iam_role.rds_monitoring.arn

  # Deletion Protection
  deletion_protection = false # Set to true for production
  skip_final_snapshot = true  # Set to false for production
  # final_snapshot_identifier = "${var.project_name}-db-final-snapshot-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"

  # Auto Minor Version Upgrade
  auto_minor_version_upgrade = true

  # Timezone
  # Note: MySQL doesn't support timezone parameter in RDS
  # Application should handle timezone

  tags = merge(
    var.common_tags,
    {
      Name = "${var.project_name}-rds-instance"
    }
  )
}

# ==========================================
# Parameter Group
# ==========================================

resource "aws_db_parameter_group" "main" {
  name   = "${var.project_name}-mysql-params"
  family = "mysql8.0"

  # Character Set Configuration (UTF-8)
  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "character_set_client"
    value = "utf8mb4"
  }

  parameter {
    name  = "character_set_connection"
    value = "utf8mb4"
  }

  parameter {
    name  = "character_set_database"
    value = "utf8mb4"
  }

  parameter {
    name  = "character_set_results"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_unicode_ci"
  }

  # Timezone Configuration
  parameter {
    name  = "time_zone"
    value = "Asia/Seoul"
  }

  # Connection Configuration
  parameter {
    name  = "max_connections"
    value = "100"
  }

  # Slow Query Log
  parameter {
    name  = "slow_query_log"
    value = "1"
  }

  parameter {
    name  = "long_query_time"
    value = "2"
  }

  tags = merge(
    var.common_tags,
    {
      Name = "${var.project_name}-mysql-params"
    }
  )
}

# ==========================================
# IAM Role for Enhanced Monitoring
# ==========================================

data "aws_iam_policy_document" "rds_monitoring_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["monitoring.rds.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "rds_monitoring" {
  name               = "${var.project_name}-rds-monitoring-role"
  assume_role_policy = data.aws_iam_policy_document.rds_monitoring_assume.json

  tags = merge(
    var.common_tags,
    {
      Name = "${var.project_name}-rds-monitoring-role"
    }
  )
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}
