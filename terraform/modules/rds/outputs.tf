# RDS Module Outputs

output "instance_id" {
  description = "RDS instance ID"
  value       = aws_db_instance.main.id
}

output "endpoint" {
  description = "RDS endpoint (hostname:port)"
  value       = aws_db_instance.main.endpoint
}

output "address" {
  description = "RDS hostname"
  value       = aws_db_instance.main.address
}

output "port" {
  description = "RDS port"
  value       = aws_db_instance.main.port
}

output "database_name" {
  description = "Database name"
  value       = aws_db_instance.main.db_name
}

output "username" {
  description = "Master username"
  value       = aws_db_instance.main.username
  sensitive   = true
}

output "arn" {
  description = "RDS instance ARN"
  value       = aws_db_instance.main.arn
}
