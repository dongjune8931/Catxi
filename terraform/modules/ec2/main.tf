# EC2 Module - Application Server

# ==========================================
# Get Latest Ubuntu 24.04 LTS AMI
# ==========================================

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ==========================================
# EC2 Instance
# ==========================================

resource "aws_instance" "app" {
  ami           = data.aws_ami.ubuntu.id
  instance_type = var.instance_type
  key_name      = var.key_name

  # Network Configuration
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [var.security_group_id]
  associate_public_ip_address = true

  # IAM Instance Profile (for ECR access)
  iam_instance_profile = var.iam_instance_profile

  # Root Volume Configuration
  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20 # GB (increase if needed)
    delete_on_termination = true
    encrypted             = true

    tags = merge(
      var.common_tags,
      {
        Name = "${var.project_name}-root-volume"
      }
    )
  }

  # User Data - Bootstrap script
  user_data = file("${path.module}/user-data.sh")

  # Enable detailed monitoring
  monitoring = true

  # Metadata options (security best practice)
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required" # IMDSv2 only
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "enabled"
  }

  # Disable source/destination check (not needed for single instance)
  source_dest_check = true

  tags = merge(
    var.common_tags,
    {
      Name = "${var.project_name}-app-server"
      Role = "Application"
    }
  )

  lifecycle {
    ignore_changes = [
      # Ignore user_data changes after initial creation
      user_data
    ]
  }
}

# ==========================================
# Elastic IP - 수동 관리 (AWS 콘솔에서 할당)
# 현재 할당된 EIP: 54.180.169.207
# ==========================================

# ==========================================
# CloudWatch Alarms (Optional)
# ==========================================

resource "aws_cloudwatch_metric_alarm" "cpu_utilization" {
  alarm_name          = "${var.project_name}-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Triggers when CPU utilization exceeds 80%"
  alarm_actions       = [] # Add SNS topic ARN for notifications

  dimensions = {
    InstanceId = aws_instance.app.id
  }

  tags = var.common_tags
}

resource "aws_cloudwatch_metric_alarm" "status_check_failed" {
  alarm_name          = "${var.project_name}-status-check-failed"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "StatusCheckFailed"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  alarm_description   = "Triggers when instance status check fails"
  alarm_actions       = [] # Add SNS topic ARN for notifications

  dimensions = {
    InstanceId = aws_instance.app.id
  }

  tags = var.common_tags
}
