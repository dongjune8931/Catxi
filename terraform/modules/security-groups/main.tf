# Security Groups Module

# ==========================================
# Application Server Security Group
# ==========================================

resource "aws_security_group" "app" {
  name        = "${var.project_name}-app-sg"
  description = "Security group for application server"
  vpc_id      = var.vpc_id

  # SSH from Jenkins server
  ingress {
    description = "SSH from Jenkins"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.jenkins_ip]
  }

  # HTTP
  ingress {
    description = "HTTP from anywhere"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # HTTPS
  ingress {
    description = "HTTPS from anywhere"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Spring Boot Application Port
  ingress {
    description = "Spring Boot from anywhere"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Outbound to RDS (will be added via security group rule)
  # Outbound to internet (for AWS APIs, package updates, OAuth, FCM)
  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.common_tags,
    {
      Name = "${var.project_name}-app-sg"
    }
  )
}

# ==========================================
# RDS Security Group
# ==========================================

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "Security group for RDS MySQL"
  vpc_id      = var.vpc_id

  # MySQL from application server only
  ingress {
    description     = "MySQL from application server"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  # No outbound traffic needed for RDS
  egress {
    description = "No outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = []
  }

  tags = merge(
    var.common_tags,
    {
      Name = "${var.project_name}-rds-sg"
    }
  )
}
