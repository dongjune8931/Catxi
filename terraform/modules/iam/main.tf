# IAM Module - EC2 Role for ECR Access

# ==========================================
# IAM Role for EC2 Instance
# ==========================================

# Trust policy - Allow EC2 to assume this role
data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

# IAM Role
resource "aws_iam_role" "ec2_role" {
  name               = "${var.project_name}-ec2-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json

  tags = merge(
    var.common_tags,
    {
      Name = "${var.project_name}-ec2-role"
    }
  )
}

# ==========================================
# ECR Access Policy
# ==========================================

data "aws_iam_policy_document" "ecr_access" {
  # Allow ECR login and image pull
  statement {
    sid    = "ECRAccess"
    effect = "Allow"

    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:DescribeRepositories",
      "ecr:ListImages"
    ]

    resources = ["*"]
  }

  # Allow CloudWatch Logs (for application logging)
  statement {
    sid    = "CloudWatchLogs"
    effect = "Allow"

    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogStreams"
    ]

    resources = ["arn:aws:logs:${var.aws_region}:*:*"]
  }
}

resource "aws_iam_policy" "ecr_access" {
  name        = "${var.project_name}-ecr-access-policy"
  description = "Policy for EC2 to access ECR and CloudWatch Logs"
  policy      = data.aws_iam_policy_document.ecr_access.json

  tags = merge(
    var.common_tags,
    {
      Name = "${var.project_name}-ecr-access-policy"
    }
  )
}

# Attach policy to role
resource "aws_iam_role_policy_attachment" "ecr_access" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = aws_iam_policy.ecr_access.arn
}

# Attach SSM policy for Session Manager access
resource "aws_iam_role_policy_attachment" "ssm_access" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# ==========================================
# Instance Profile
# ==========================================

resource "aws_iam_instance_profile" "ec2_profile" {
  name = "${var.project_name}-ec2-profile"
  role = aws_iam_role.ec2_role.name

  tags = merge(
    var.common_tags,
    {
      Name = "${var.project_name}-ec2-profile"
    }
  )
}
