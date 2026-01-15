# Security Groups Module Outputs

output "app_security_group_id" {
  description = "Application server security group ID"
  value       = aws_security_group.app.id
}

output "rds_security_group_id" {
  description = "RDS security group ID"
  value       = aws_security_group.rds.id
}
